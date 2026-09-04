package dev.tubolsillo.app.domain.repo

import dev.tubolsillo.app.core.time.nowMillis

import dev.tubolsillo.app.core.db.dao.AccountDao
import dev.tubolsillo.app.core.db.dao.BalanceSnapshotDao
import dev.tubolsillo.app.core.db.entity.AccountEntity
import dev.tubolsillo.app.core.db.entity.BalanceSnapshotEntity
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.SourceType
import kotlinx.coroutines.flow.Flow

class AccountRepository(
    private val accountDao: AccountDao,
    private val snapshotDao: BalanceSnapshotDao,
) {

    fun observeActive(): Flow<List<AccountEntity>> = accountDao.observeActive()

    fun observeAll(): Flow<List<AccountEntity>> = accountDao.observeAll()

    fun observeById(id: Long): Flow<AccountEntity?> = accountDao.observeById(id)

    suspend fun getById(id: Long): AccountEntity? = accountDao.getById(id)

    suspend fun create(
        institution: Institution,
        name: String,
        kind: AccountKind,
        currencyCode: String,
        initialBalanceMinor: Long?,
        creditLimitMinor: Long?,
        accountNumberMask: String? = null,
        colorHex: String? = null,
    ): Long {
        val id = accountDao.insert(
            AccountEntity(
                institution = institution.key,
                name = name,
                kind = kind.key,
                currency = currencyCode,
                creditLimitMinor = creditLimitMinor,
                accountNumberMask = accountNumberMask,
                colorHex = colorHex,
            )
        )
        if (initialBalanceMinor != null) {
            recordBalance(id, initialBalanceMinor, currencyCode, SourceType.MANUAL)
        }
        return id
    }

    /**
     * Registra un saldo: inserta snapshot y actualiza el saldo desnormalizado
     * de la cuenta en una sola transacción.
     */
    suspend fun recordBalance(
        accountId: Long,
        amountMinor: Long,
        currencyCode: String,
        source: SourceType,
        rawText: String? = null,
        capturedAt: Long = nowMillis(),
    ) {
        snapshotDao.recordBalance(
            accountId,
            BalanceSnapshotEntity(
                accountId = accountId,
                capturedAt = capturedAt,
                amountMinor = amountMinor,
                currency = currencyCode,
                source = source.key,
                rawText = rawText,
            ),
        )
    }

    suspend fun updateCreditLimit(accountId: Long, limitMinor: Long?) =
        accountDao.updateCreditLimit(accountId, limitMinor)

    suspend fun updateColor(accountId: Long, colorHex: String?) =
        accountDao.updateColor(accountId, colorHex)

    suspend fun archive(accountId: Long) = accountDao.archive(accountId)

    /**
     * Resuelve la cuenta para una captura: activa de la institución en la
     * moneda parseada; si el texto menciona tarjeta de crédito, prefiere una
     * TDC. Si no existe ninguna, la crea (primera captura del banco).
     */
    suspend fun findForCapture(
        institution: dev.tubolsillo.app.domain.model.Institution,
        parsed: dev.tubolsillo.app.domain.model.ParsedTransaction,
    ): AccountEntity {
        val isCard = parsed.raw.contains("tarjeta", ignoreCase = true) ||
            parsed.raw.contains("credito", ignoreCase = true) ||
            parsed.raw.contains("crédito", ignoreCase = true)
        val preferredKind = when {
            // Cashea es BNPL: su deuda va en la sección Deudas, no como efectivo.
            institution == dev.tubolsillo.app.domain.model.Institution.CASHEA ->
                AccountKind.BNPL
            isCard -> AccountKind.CREDIT_CARD
            else -> null
        }

        val active = accountDao.findActiveByInstitution(institution.key)
        val match = active.firstOrNull { it.currency == parsed.currency && it.kind == preferredKind?.key }
            ?: active.firstOrNull { it.currency == parsed.currency }
            ?: active.firstOrNull { it.kind == preferredKind?.key }
            ?: active.firstOrNull()
        if (match != null) return match

        // Sin cuenta: se crea automáticamente con la primera captura.
        val id = create(
            institution = institution,
            name = institution.displayName + if (preferredKind == AccountKind.CREDIT_CARD) " TDC" else "",
            kind = preferredKind ?: AccountKind.CHECKING,
            currencyCode = parsed.currency,
            initialBalanceMinor = null,
            creditLimitMinor = null,
        )
        return accountDao.getById(id)!!
    }
}
