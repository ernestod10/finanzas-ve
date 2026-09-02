package dev.tubolsillo.app.data.source.binance

import dev.tubolsillo.app.core.crypto.SecretStore
import dev.tubolsillo.app.core.db.dao.AccountDao
import dev.tubolsillo.app.core.db.dao.TransactionDao
import dev.tubolsillo.app.core.db.entity.AccountEntity
import dev.tubolsillo.app.core.db.entity.TransactionEntity
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.SourceType
import dev.tubolsillo.app.domain.model.TransactionCategory
import dev.tubolsillo.app.domain.model.TxStatus
import dev.tubolsillo.app.domain.repo.AccountRepository
import dev.tubolsillo.app.domain.usecase.RateProvider
import dev.tubolsillo.app.domain.model.RateSource
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ServerResponseException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Resultado de una sincronización con Binance. */
data class SyncResult(
    val error: String? = null,
    val accountsUpdated: Int = 0,
    val newTransactions: Int = 0,
) {
    val isSuccess: Boolean get() = error == null
}

/**
 * Sync de Binance: saldos spot → cuentas CRYPTO por asset con snapshot de
 * saldo; tasa BTC→USDT→USD del ticker; depósitos/retiros → transacciones.
 * No hace nada sin llaves configuradas.
 */
@Singleton
class BinanceSync @Inject constructor(
    private val api: BinanceApi,
    private val secretStore: SecretStore,
    private val accountDao: AccountDao,
    private val accountRepository: AccountRepository,
    private val transactionDao: TransactionDao,
    private val rateProvider: RateProvider,
    private val signer: BinanceSigner,
) {

    private val syncMutex = Mutex()

    /** Offset de reloj del servidor (ms), ajustado en cada sync. */
    @Volatile
    private var clockOffsetMs: Long = 0

    suspend fun testConnection(): SyncResult {
        val (apiKey, apiSecret) = secretStore.binanceKeys() ?: return SyncResult(error = "Sin llaves")
        return try {
            refreshClockOffset()
            api.account(signed(apiSecret), apiKey)
            SyncResult()
        } catch (e: Exception) {
            SyncResult(error = friendlyError(e))
        }
    }

    suspend fun sync(): SyncResult = syncMutex.withLock {
        val (apiKey, apiSecret) = secretStore.binanceKeys() ?: return SyncResult(error = "Sin llaves configuradas")
        return try {
            refreshClockOffset()
            val info = api.account(signed(apiSecret), apiKey)

            var accountsUpdated = 0
            for (balance in info.balances) {
                val total = balance.free.toDoubleOrNull() ?: 0.0
                val locked = balance.locked.toDoubleOrNull() ?: 0.0
                if (total + locked <= 0.0) continue
                val minor = AmountNormalizer.parseMinor((total + locked).toString(), balance.asset) ?: continue
                val account = findOrCreateAssetAccount(balance.asset)
                accountRepository.recordBalance(
                    accountId = account.id,
                    amountMinor = minor,
                    currencyCode = balance.asset,
                    source = SourceType.BINANCE,
                )
                accountsUpdated++
            }

            var newTransactions = 0
            newTransactions += syncDeposits(apiKey, apiSecret)
            newTransactions += syncWithdrawals(apiKey, apiSecret)
            syncBtcRate()

            SyncResult(accountsUpdated = accountsUpdated, newTransactions = newTransactions)
        } catch (e: Exception) {
            SyncResult(error = friendlyError(e))
        }
    }

    private suspend fun syncDeposits(apiKey: String, apiSecret: String): Int {
        val records = api.deposits(signed(apiSecret), apiKey)
        var inserted = 0
        for (r in records) {
            if (r.status != DEPOSIT_SUCCESS) continue
            val minor = AmountNormalizer.parseMinor(r.amount, r.coin) ?: continue
            val dedupKey = "BINANCE|DEP|${r.id}"
            if (transactionDao.findByDedupKey(dedupKey) != null) continue
            insertBinanceTx(
                dedupKey = dedupKey,
                account = findOrCreateAssetAccount(r.coin),
                occurredAt = if (r.insertTime > 0) r.insertTime else System.currentTimeMillis(),
                amountMinor = minor,
                currency = r.coin,
                description = "Depósito ${r.coin} (${r.network})",
            )
            inserted++
        }
        return inserted
    }

    private suspend fun syncWithdrawals(apiKey: String, apiSecret: String): Int {
        val records = api.withdrawals(signed(apiSecret), apiKey)
        var inserted = 0
        for (r in records) {
            if (r.status != WITHDRAW_SUCCESS) continue
            val minor = AmountNormalizer.parseMinor(r.amount, r.coin) ?: continue
            val dedupKey = "BINANCE|WD|${r.id}"
            if (transactionDao.findByDedupKey(dedupKey) != null) continue
            insertBinanceTx(
                dedupKey = dedupKey,
                account = findOrCreateAssetAccount(r.coin),
                occurredAt = if (r.applyTime > 0) r.applyTime else System.currentTimeMillis(),
                amountMinor = -kotlin.math.abs(minor),
                currency = r.coin,
                description = "Retiro ${r.coin} (${r.network})",
            )
            inserted++
        }
        return inserted
    }

    private suspend fun syncBtcRate() {
        try {
            val ticker = api.tickerPrice("BTCUSDT")
            val price = ticker.price.toDoubleOrNull() ?: return
            if (price > 0) {
                rateProvider.setRate("BTC", price, RateSource.BINANCE_TICKER)
            }
        } catch (_: Exception) {
            // La tasa BTC es best-effort; un fallo no debe tumbar la sync.
        }
    }

    private suspend fun refreshClockOffset() {
        try {
            val serverTime = api.serverTime()
            clockOffsetMs = serverTime.serverTime - System.currentTimeMillis()
        } catch (_: Exception) {
            // Sin offset, se usa el reloj local.
        }
    }

    private suspend fun findOrCreateAssetAccount(asset: String): AccountEntity {
        val existing = accountDao.findByInstitutionAndCurrency(Institution.BINANCE.key, asset.uppercase())
        if (existing != null) return existing
        val id = accountRepository.create(
            institution = Institution.BINANCE,
            name = "Binance " + asset.uppercase(),
            kind = dev.tubolsillo.app.domain.model.AccountKind.CRYPTO,
            currencyCode = asset.uppercase(),
            initialBalanceMinor = null,
            creditLimitMinor = null,
        )
        return accountDao.getById(id)!!
    }

    private suspend fun insertBinanceTx(
        dedupKey: String,
        account: AccountEntity,
        occurredAt: Long,
        amountMinor: Long,
        currency: String,
        description: String,
    ): Long = transactionDao.insert(
        TransactionEntity(
            accountId = account.id,
            occurredAt = occurredAt,
            amountMinor = amountMinor,
            currency = currency,
            description = description,
            rawDescription = description,
            category = TransactionCategory.OTHER.key,
            status = TxStatus.CONFIRMED.key,
            dedupKey = dedupKey,
            source = SourceType.BINANCE.key,
        )
    )

    /** Params firmados con el offset de reloj del servidor aplicado. */
    private fun signed(apiSecret: String): Map<String, String> =
        signer.signedParams(emptyMap(), apiSecret, System.currentTimeMillis() + clockOffsetMs)

    /** Traduce errores de Binance a mensajes accionables (geo-bloqueo, llaves malas). */
    private fun friendlyError(e: Exception): String {
        val status = when (e) {
            is ClientRequestException -> e.response.status.value
            is ServerResponseException -> e.response.status.value
            else -> null
        }
        return when (status) {
            451 -> "Binance rechazó la conexión (451). Prueba con una VPN."
            400, 401 -> "Llaves inválidas o sin permiso de lectura. Verifícalas en Binance."
            else -> when (e) {
                is HttpRequestTimeoutException, is ConnectTimeoutException ->
                    "Tiempo agotado. Revisa tu conexión a internet."
                else -> e.message ?: "Error desconocido"
            }
        }
    }

    private companion object {
        const val DEPOSIT_SUCCESS = 1
        const val WITHDRAW_SUCCESS = 6
    }
}
