@file:OptIn(kotlin.time.ExperimentalTime::class)

package dev.tubolsillo.app.data.source.portal

import dev.tubolsillo.app.core.db.dao.TagDao
import dev.tubolsillo.app.core.db.dao.TransactionDao
import dev.tubolsillo.app.core.db.entity.TransactionEntity
import dev.tubolsillo.app.core.text.MerchantNormalizer
import dev.tubolsillo.app.core.time.nowMillis
import dev.tubolsillo.app.domain.model.ParsedTransaction
import dev.tubolsillo.app.domain.model.SourceType
import dev.tubolsillo.app.domain.model.TxStatus
import dev.tubolsillo.app.domain.repo.AccountRepository
import dev.tubolsillo.app.domain.usecase.RuleEngine
import dev.tubolsillo.app.domain.usecase.TransferMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class PortalImportResult(
    val inserted: Int = 0,
    val reconciled: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val balanceMinor: Long? = null,
    val balanceUpdated: Boolean = false,
    val accountId: Long? = null,
)

/**
 * Importa en LOTE lo que trajo el portal. Sigue el patrón de CsvImporter en vez
 * de CapturePipeline.ingest() a propósito: ingest() emite una notificación por
 * llamada y deriva el saldo sumando al anterior, así que usarlo por fila daría
 * N notificaciones y dejaría el saldo corrupto si las filas no llegan en orden.
 *
 * Aquí el saldo del portal es AUTORITATIVO y se aplica una sola vez al final.
 */
class PortalImporter(
    private val transactionDao: TransactionDao,
    private val tagDao: TagDao,
    private val ruleEngine: RuleEngine,
    private val transferMatcher: TransferMatcher,
    private val accountRepository: AccountRepository,
    private val usdEquivalence: dev.tubolsillo.app.domain.usecase.UsdEquivalence,
) {

    suspend fun import(
        recipe: PortalRecipe,
        mapped: PortalMapResult,
        accountId: Long? = null,
        capturedAt: Long = nowMillis(),
    ): PortalImportResult = withContext(Dispatchers.IO) {
        // La cuenta bancaria del portal es siempre de depósito (Corriente/Ahorro),
        // nunca una TDC aunque el primer movimiento diga "PAGO TARJETA DE CREDITO".
        val probe = syntheticProbe(recipe).copy(
            currency = mapped.rows.firstOrNull()?.currency ?: mapped.balanceCurrency ?: recipe.currency,
        )
        val account = accountId?.let { accountRepository.getById(it) }
            ?: accountRepository.findForCapture(recipe.institution, probe)

        if (mapped.rows.isEmpty() && mapped.balanceMinor == null && mapped.accountBalances.isEmpty()) {
            return@withContext PortalImportResult(skipped = mapped.skipped, accountId = account.id)
        }

        var inserted = 0
        var reconciled = 0
        var failed = 0

        for (parsed in mapped.rows) {
            try {
                val plan = PortalDedup.plan(parsed, recipe)
                val existing = findExisting(plan, account.id)
                if (existing != null) {
                    transactionDao.markReconciled(existing.id)
                    reconciled++
                    continue
                }
                // Congelado con la tasa BCV del día del movimiento.
                val usdFrozen = usdEquivalence.usdMinor(
                    parsed.amountMinor, parsed.currency, parsed.occurredAt,
                )
                val txId = transactionDao.insert(
                    TransactionEntity(
                        accountId = account.id,
                        occurredAt = parsed.occurredAt,
                        amountMinor = parsed.amountMinor,
                        currency = parsed.currency,
                        description = parsed.description,
                        rawDescription = parsed.raw,
                        category = parsed.category.key,
                        status = TxStatus.CONFIRMED.key,
                        dedupKey = plan.exactKey,
                        source = SourceType.PORTAL.key,
                        rawJson = parsed.raw,
                        isReconciled = true,
                        usdEquivalentMinor = usdFrozen,
                    )
                )
                ruleEngine.match(MerchantNormalizer.normalize(parsed.merchant))?.let { rule ->
                    rule.tagName?.let { tagDao.link(txId, tagDao.getOrCreate(it).id) }
                    rule.categoryKey?.let { transactionDao.updateCategory(txId, it) }
                }
                transactionDao.getById(txId)?.let { tx ->
                    runCatching { transferMatcher.matchFor(tx) }
                }
                inserted++
            } catch (_: Exception) {
                failed++
            }
        }

        // Saldo autoritativo del banco: aplica a la cuenta principal y a productos adicionales (ej. Cuenta Verde USD o TDC).
        var balanceUpdated = false
        val primaryCurrency = mapped.balanceCurrency ?: recipe.currency
        mapped.balanceMinor?.let { balance ->
            runCatching {
                accountRepository.recordBalance(
                    accountId = account.id,
                    amountMinor = balance,
                    currencyCode = primaryCurrency,
                    source = SourceType.PORTAL,
                    rawText = "portal:" + recipe.institution.key,
                    capturedAt = capturedAt,
                )
                balanceUpdated = true
            }
        }

        var primaryRecordedFromList = mapped.balanceMinor != null
        for (extra in mapped.accountBalances) {
            runCatching {
                if (extra.kind != "CREDIT_CARD" && extra.currency == account.currency && !primaryRecordedFromList) {
                    accountRepository.recordBalance(
                        accountId = account.id,
                        amountMinor = extra.amountMinor,
                        currencyCode = extra.currency,
                        source = SourceType.PORTAL,
                        rawText = "portal:" + recipe.institution.key + ":" + extra.label,
                        capturedAt = capturedAt,
                    )
                    primaryRecordedFromList = true
                    balanceUpdated = true
                } else if (extra.kind == "CREDIT_CARD") {
                    val card = accountRepository.findCardForCapture(
                        institution = recipe.institution,
                        currencyCode = extra.currency,
                        mask = extra.mask,
                        cardName = extra.cardName,
                    )
                    if (card.id != account.id) {
                        applyCard(card, extra, recipe, capturedAt)
                        balanceUpdated = true
                    }
                } else if (extra.currency != account.currency) {
                    val extraProbe = syntheticProbe(recipe).copy(
                        currency = extra.currency,
                        raw = "portal:" + recipe.institution.key,
                    )
                    val targetAcc = accountRepository.findForCapture(recipe.institution, extraProbe)
                    if (targetAcc.id != account.id) {
                        accountRepository.recordBalance(
                            accountId = targetAcc.id,
                            amountMinor = extra.amountMinor,
                            currencyCode = extra.currency,
                            source = SourceType.PORTAL,
                            rawText = "portal:" + recipe.institution.key + ":" + extra.kind,
                            capturedAt = capturedAt,
                        )
                        balanceUpdated = true
                    }
                }
            }
        }

        PortalImportResult(
            inserted = inserted,
            reconciled = reconciled,
            skipped = mapped.skipped,
            failed = failed,
            balanceMinor = mapped.balanceMinor ?: mapped.accountBalances.firstOrNull()?.amountMinor,
            balanceUpdated = balanceUpdated,
            accountId = account.id,
        )
    }

    /**
     * Guarda la ficha de la tarjeta tal como la publica el banco. La deuda va
     * como saldo con signo negativo; el disponible, el mínimo y el día de pago
     * van a sus propias columnas porque son datos distintos y no se pueden
     * derivar unos de otros sin el límite.
     */
    private suspend fun applyCard(
        card: dev.tubolsillo.app.core.db.entity.AccountEntity,
        extra: MappedAccountBalance,
        recipe: PortalRecipe,
        capturedAt: Long,
    ) {
        accountRepository.updateCreditMeta(
            accountId = card.id,
            limitMinor = extra.limitMinor,
            availableMinor = extra.availableMinor,
            minPaymentMinor = extra.minPaymentMinor,
            dueAt = extra.dueAt,
            mask = extra.mask,
        )
        val balance = when {
            extra.debtMinor != null -> -extra.debtMinor
            extra.availableMinor != null && extra.limitMinor != null ->
                -(extra.limitMinor - extra.availableMinor).coerceAtLeast(0L)
            else -> null
        }
        if (balance != null) {
            accountRepository.recordBalance(
                accountId = card.id,
                amountMinor = balance,
                currencyCode = extra.currency,
                source = SourceType.PORTAL,
                rawText = "portal:" + recipe.institution.key + ":TDC",
                capturedAt = capturedAt,
            )
        } else if (extra.availableMinor != null &&
            card.lastBalanceMinor != null &&
            abs(card.lastBalanceMinor) == extra.availableMinor
        ) {
            // Lectura vieja: el disponible de la tarjeta se guardaba como deuda.
            accountRepository.clearLastBalance(card.id)
        }
        println(
            "PortalImport: TDC " + recipe.institution.key +
                " mask=" + extra.mask +
                " deuda=" + extra.debtMinor +
                " disp=" + extra.availableMinor +
                " limite=" + extra.limitMinor +
                " min=" + extra.minPaymentMinor +
                " vence=" + extra.dueAt
        )
    }

    /** Busca la transacción ya registrada: clave exacta, vecinas, y día. */
    private suspend fun findExisting(plan: PortalDedup.Lookup, accountId: Long): TransactionEntity? {
        transactionDao.findByDedupKey(plan.exactKey)?.let { return it }
        for (key in plan.neighborKeys) {
            transactionDao.findByDedupKey(key)?.let { return it }
        }
        plan.dayWindow?.let { window ->
            transactionDao.findByAmountInWindow(
                accountId = accountId,
                amountMinor = window.amountMinor,
                from = window.fromMs,
                to = window.toMs,
            )?.let { return it }
        }
        return null
    }

    /** Sonda para resolver la cuenta cuando el portal solo trajo saldo. */
    private fun syntheticProbe(recipe: PortalRecipe): ParsedTransaction = ParsedTransaction(
        institution = recipe.institution,
        occurredAt = nowMillis(),
        amountMinor = 0,
        currency = recipe.currency,
        merchant = "Portal",
        description = "Consulta de portal",
        category = dev.tubolsillo.app.domain.model.TransactionCategory.OTHER,
        parserVersion = PortalRowMapper.PARSER_VERSION,
        raw = "portal:" + recipe.institution.key,
    )
}
