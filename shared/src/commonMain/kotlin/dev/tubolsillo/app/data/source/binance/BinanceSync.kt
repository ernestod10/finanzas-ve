package dev.tubolsillo.app.data.source.binance

import dev.tubolsillo.app.core.time.nowMillis
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
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
class BinanceSync(
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
        val cleanKey = apiKey.trim()
        val cleanSecret = apiSecret.trim()
        return try {
            refreshClockOffset()
            api.account(signed(cleanSecret), cleanKey)
            SyncResult()
        } catch (e: Exception) {
            SyncResult(error = friendlyError(e))
        }
    }

    suspend fun sync(): SyncResult = syncMutex.withLock {
        val (apiKey, apiSecret) = secretStore.binanceKeys() ?: return SyncResult(error = "Sin llaves configuradas")
        val cleanKey = apiKey.trim()
        val cleanSecret = apiSecret.trim()
        return try {
            refreshClockOffset()
            val info = api.account(signed(cleanSecret), cleanKey)

            var accountsUpdated = 0
            for (balance in info.balances) {
                val freeMinor = AmountNormalizer.parseMinor(balance.free, balance.asset) ?: 0L
                val lockedMinor = AmountNormalizer.parseMinor(balance.locked, balance.asset) ?: 0L
                val minor = freeMinor + lockedMinor
                if (minor <= 0L) continue
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
            try {
                newTransactions += syncDeposits(cleanKey, cleanSecret)
            } catch (e: Exception) {
                println("BinanceSync: syncDeposits omitido (${e.message})")
            }
            try {
                newTransactions += syncWithdrawals(cleanKey, cleanSecret)
            } catch (e: Exception) {
                println("BinanceSync: syncWithdrawals omitido (${e.message})")
            }
            // P2P y Pay pueden faltar si la llave no tiene el permiso; cada uno
            // falla aislado para no tumbar saldos ni depósitos.
            try {
                newTransactions += syncP2p(cleanKey, cleanSecret)
            } catch (e: Exception) {
                println("BinanceSync: syncP2p omitido (${e.message})")
            }
            try {
                newTransactions += syncPay(cleanKey, cleanSecret)
            } catch (e: Exception) {
                println("BinanceSync: syncPay omitido (${e.message})")
            }
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
                occurredAt = if (r.insertTime > 0) r.insertTime else nowMillis(),
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
                occurredAt = parseWithdrawTime(r.applyTime),
                amountMinor = -kotlin.math.abs(minor),
                currency = r.coin,
                description = "Retiro ${r.coin} (${r.network})",
            )
            inserted++
        }
        return inserted
    }

    /**
     * Órdenes P2P completadas. Se registra la pata cripto (lo que entra o sale
     * de la wallet) como TRANSFER: comprar USDT con bolívares no es un ingreso,
     * es mover plata entre tus propios activos. La pata fiat la captura el banco.
     */
    private suspend fun syncP2p(apiKey: String, apiSecret: String): Int {
        val now = nowMillis() + clockOffsetMs
        val response = api.p2pOrders(
            signedWith(
                apiSecret,
                mapOf(
                    "startTimestamp" to (now - P2P_WINDOW_MS).toString(),
                    "endTimestamp" to now.toString(),
                    "rows" to "100",
                    "page" to "1",
                ),
            ),
            apiKey,
        )
        var inserted = 0
        for (order in response.data) {
            if (!order.orderStatus.equals("COMPLETED", ignoreCase = true)) continue
            val asset = order.asset.uppercase()
            if (asset.isBlank()) continue
            val minor = AmountNormalizer.parseMinor(order.amount, asset) ?: continue
            if (minor <= 0L) continue
            val isBuy = order.tradeType.equals("BUY", ignoreCase = true)
            val dedupKey = "BINANCE|P2P|" + order.orderNumber
            if (transactionDao.findByDedupKey(dedupKey) != null) continue

            val fiatText = order.fiatSymbol + order.totalPrice
            val counterpart = order.counterPartNickName?.takeIf { it.isNotBlank() }
            val description = buildString {
                append(if (isBuy) "P2P compra " else "P2P venta ")
                append(asset)
                append(" · ")
                append(fiatText)
                counterpart?.let { append(" · "); append(it) }
            }
            insertBinanceTx(
                dedupKey = dedupKey,
                account = findOrCreateAssetAccount(asset),
                occurredAt = if (order.createTime > 0) order.createTime else nowMillis(),
                amountMinor = if (isBuy) minor else -minor,
                currency = asset,
                description = description,
                category = TransactionCategory.TRANSFER,
            )
            inserted++
        }
        return inserted
    }

    /**
     * Binance Pay. El monto ya viene firmado por Binance. Se omiten los pares
     * de retención (HOLDING), que duplicarían el mismo movimiento.
     */
    private suspend fun syncPay(apiKey: String, apiSecret: String): Int {
        val now = nowMillis() + clockOffsetMs
        val response = api.payTransactions(
            signedWith(
                apiSecret,
                mapOf(
                    "startTime" to (now - PAY_WINDOW_MS).toString(),
                    "endTime" to now.toString(),
                    "limit" to "100",
                ),
            ),
            apiKey,
        )
        var inserted = 0
        println("BinanceSync: Pay code=" + response.code + " n=" + response.data.size)
        for (tx in response.data) {
            if (tx.orderType.uppercase() in PAY_HOLDING_TYPES) continue
            val currency = tx.currency.uppercase()
            if (currency.isBlank()) continue
            val minor = AmountNormalizer.parseMinor(tx.amount, currency) ?: continue
            if (minor == 0L) continue
            val dedupKey = "BINANCE|PAY|" + tx.transactionId
            if (transactionDao.findByDedupKey(dedupKey) != null) continue

            // La contraparte útil es a quién le pagaste (salida) o quién te pagó (entrada).
            val party = if (minor < 0) tx.receiverInfo?.name else tx.payerInfo?.name
            val description = buildString {
                append("Binance Pay · ")
                append(payTypeLabel(tx.orderType))
                party?.takeIf { it.isNotBlank() }?.let { append(" · "); append(it) }
            }
            insertBinanceTx(
                dedupKey = dedupKey,
                account = findOrCreateAssetAccount(currency),
                occurredAt = if (tx.transactionTime > 0) tx.transactionTime else nowMillis(),
                amountMinor = minor,
                currency = currency,
                description = description,
            )
            inserted++
        }
        return inserted
    }

    private fun payTypeLabel(orderType: String): String = when (orderType.uppercase()) {
        "PAY" -> "pago"
        "PAY_REFUND" -> "devolución"
        "PAYOUT" -> "retiro"
        "REMITTANCE" -> "remesa"
        "CRYPTO_BOX" -> "crypto box"
        "CRYPTO_BOX_RF" -> "devolución de crypto box"
        else -> orderType.lowercase().ifBlank { "movimiento" }
    }

    private fun parseWithdrawTime(applyTime: String?): Long {
        if (applyTime.isNullOrBlank()) return nowMillis()
        val asLong = applyTime.toLongOrNull()
        if (asLong != null && asLong > 0) return asLong
        return nowMillis()
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
            clockOffsetMs = serverTime.serverTime - nowMillis()
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
        category: TransactionCategory = TransactionCategory.OTHER,
    ): Long = transactionDao.insert(
        TransactionEntity(
            accountId = account.id,
            occurredAt = occurredAt,
            amountMinor = amountMinor,
            currency = currency,
            description = description,
            rawDescription = description,
            category = category.key,
            status = TxStatus.CONFIRMED.key,
            dedupKey = dedupKey,
            source = SourceType.BINANCE.key,
        )
    )

    /** Params firmados con el offset de reloj del servidor aplicado. */
    private fun signed(apiSecret: String): Map<String, String> =
        signer.signedParams(emptyMap(), apiSecret, nowMillis() + clockOffsetMs)

    /** Igual que [signed], pero con parámetros extra (ventanas de fechas). */
    private fun signedWith(apiSecret: String, extra: Map<String, String>): Map<String, String> =
        signer.signedParams(extra, apiSecret, nowMillis() + clockOffsetMs)

    /** Traduce errores de Binance a mensajes accionables (geo-bloqueo, llaves malas). */
    private suspend fun friendlyError(e: Exception): String {
        println("BinanceSync error: ${e::class.simpleName}: ${e.message}")
        val status = (e as? ResponseException)?.response?.status?.value
        val detail = runCatching { (e as? ResponseException)?.response?.bodyAsText() }.getOrNull()
        if (detail != null) {
            println("BinanceSync server detail: $detail")
        }

        val serverMsg = detail?.let { json ->
            val m = Regex("\"msg\"\\s*:\\s*\"([^\"]+)\"").find(json)
            m?.groupValues?.get(1)
        }

        return when {
            status == 451 -> "Binance rechazó la conexión (451). Prueba con una VPN."
            serverMsg != null -> "Binance: $serverMsg"
            status == 400 || status == 401 -> "Llaves inválidas o sin permiso de lectura. Verifícalas en Binance."
            e is io.ktor.client.network.sockets.ConnectTimeoutException || e is io.ktor.client.plugins.HttpRequestTimeoutException ->
                "Tiempo agotado. Revisa tu conexión a internet."
            else -> e.message ?: "Error desconocido"
        }
    }

    private companion object {
        const val DEPOSIT_SUCCESS = 1
        const val WITHDRAW_SUCCESS = 6

        /** La API de P2P acepta como máximo 30 días por consulta. */
        const val P2P_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

        /** Pay acepta hasta 90 días; 30 alcanza y mantiene liviano el primer sync. */
        const val PAY_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

        /** Pares de retención de Pay: registrar ambos duplicaría el movimiento. */
        val PAY_HOLDING_TYPES = setOf("C2C_HOLDING", "C2C_HOLDING_RF")
    }
}
