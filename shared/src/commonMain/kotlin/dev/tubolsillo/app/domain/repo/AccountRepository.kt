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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AccountRepository(
    private val accountDao: AccountDao,
    private val snapshotDao: BalanceSnapshotDao,
) {

    /**
     * Serializa los ajustes de saldo para que dos altas manuales seguidas no se
     * pisen (leer saldo → sumar → escribir).
     */
    private val balanceMutex = Mutex()

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

    /** Ficha de la tarjeta que publica el portal: límite, disponible, mínimo y día de pago. */
    suspend fun updateCreditMeta(
        accountId: Long,
        limitMinor: Long?,
        availableMinor: Long?,
        minPaymentMinor: Long?,
        dueAt: Long?,
        mask: String?,
    ) = accountDao.updateCreditMeta(
        id = accountId,
        limit = limitMinor,
        available = availableMinor,
        minPayment = minPaymentMinor,
        dueAt = dueAt,
        mask = mask?.takeIf { it.isNotBlank() },
    )

    /**
     * Borra el saldo desnormalizado sin inventar uno nuevo. Se usa cuando el
     * portal publica solo el disponible de una tarjeta: lo guardado era el
     * disponible leído como si fuera deuda, y dejarlo mostraría una deuda falsa.
     */
    suspend fun clearLastBalance(accountId: Long) = accountDao.clearLastBalance(accountId)

    /**
     * Tarjeta de crédito para una lectura del portal, por máscara cuando se
     * conoce. Sin máscara previa en la app y una sola tarjeta del banco, esa es
     * la tarjeta: así las cuentas que ya existían se completan en vez de
     * duplicarse.
     */
    suspend fun findCardForCapture(
        institution: Institution,
        currencyCode: String,
        mask: String?,
        cardName: String?,
    ): AccountEntity {
        val cards = accountDao.findActiveByInstitution(institution.key)
            .filter { it.kind == AccountKind.CREDIT_CARD.key }
        val cleanMask = mask?.trim()?.takeIf { it.isNotBlank() }

        val existing = cards.firstOrNull { cleanMask != null && it.accountNumberMask == cleanMask }
            ?: cards.firstOrNull { cleanMask != null && it.currency.equals(currencyCode, true) && it.accountNumberMask.isNullOrBlank() }
            ?: cards.firstOrNull { cleanMask == null && it.currency.equals(currencyCode, true) }
            ?: cards.singleOrNull()
        if (existing != null) {
            // La cuenta que creó la app se llamaba "BNC TDC"; si el banco publica
            // el nombre real de la tarjeta, se usa ese. Un nombre que puso el
            // usuario no se toca.
            val autoName = institution.displayName + " TDC"
            if (existing.name == autoName && !cardName.isNullOrBlank() && cardName != autoName) {
                accountDao.updateName(existing.id, cardName)
                return accountDao.getById(existing.id) ?: existing
            }
            return existing
        }

        val name = cardName?.takeIf { it.isNotBlank() } ?: (institution.displayName + " TDC")
        val id = create(
            institution = institution,
            name = name,
            kind = AccountKind.CREDIT_CARD,
            currencyCode = currencyCode,
            initialBalanceMinor = null,
            creditLimitMinor = null,
            accountNumberMask = cleanMask,
        )
        return accountDao.getById(id)!!
    }

    /**
     * Aplica un movimiento manual al saldo de la cuenta: saldo = actual + delta.
     * Deja snapshot para que el historial y los totales (Inicio, patrimonio)
     * reflejen el movimiento. Si la moneda no es la de la cuenta, no toca nada.
     */
    suspend fun applyTransactionDelta(
        accountId: Long,
        deltaMinor: Long,
        currencyCode: String,
        source: SourceType = SourceType.MANUAL,
    ) = balanceMutex.withLock {
        val account = accountDao.getById(accountId) ?: return@withLock
        if (!account.currency.equals(currencyCode, ignoreCase = true)) return@withLock
        val current = account.lastBalanceMinor ?: 0L
        recordBalance(
            accountId = accountId,
            amountMinor = current + deltaMinor,
            currencyCode = account.currency,
            source = source,
        )
    }

    suspend fun updateColor(accountId: Long, colorHex: String?) =
        accountDao.updateColor(accountId, colorHex)

    /** El nombre es solo presentación: no toca saldos, historial ni captura. */
    suspend fun updateName(accountId: Long, name: String) =
        accountDao.updateName(accountId, name.trim())

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
        val esDebit = parsed.raw.contains("debit", ignoreCase = true) ||
            parsed.raw.contains("débito", ignoreCase = true) || parsed.raw.contains("debito", ignoreCase = true)
        val isCard = !esDebit && (
            parsed.raw.contains("tarjeta", ignoreCase = true) ||
                parsed.raw.contains("credito", ignoreCase = true) ||
                parsed.raw.contains("crédito", ignoreCase = true)
            )
        val preferredKind = when {
            // Cashea es BNPL: su deuda va en la sección Deudas, no como efectivo.
            institution == dev.tubolsillo.app.domain.model.Institution.CASHEA ->
                AccountKind.BNPL
            isCard -> AccountKind.CREDIT_CARD
            else -> AccountKind.CHECKING
        }

        val debtKinds = setOf(AccountKind.BNPL.key, AccountKind.CREDIT_CARD.key)
        val active = accountDao.findActiveByInstitution(institution.key)
            .filter { account ->
                when (preferredKind) {
                    AccountKind.BNPL -> account.kind == AccountKind.BNPL.key
                    AccountKind.CREDIT_CARD -> account.kind == AccountKind.CREDIT_CARD.key
                    else -> account.kind !in debtKinds
                }
            }
        val match = active.firstOrNull { it.currency == parsed.currency && it.kind == preferredKind.key }
            ?: active.firstOrNull { it.currency == parsed.currency }
            ?: active.firstOrNull { it.kind == preferredKind.key }
            ?: active.firstOrNull()
        if (match != null) return match

        // Sin cuenta del tipo correspondiente (ej. sólo había TDC y llegan movimientos de cuenta): se crea.
        val id = create(
            institution = institution,
            name = institution.displayName + if (preferredKind == AccountKind.CREDIT_CARD) " TDC" else "",
            kind = preferredKind,
            currencyCode = parsed.currency,
            initialBalanceMinor = null,
            creditLimitMinor = null,
        )
        return accountDao.getById(id)!!
    }
}
