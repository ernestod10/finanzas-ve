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
) {

    suspend fun import(
        recipe: PortalRecipe,
        mapped: PortalMapResult,
        accountId: Long? = null,
        capturedAt: Long = nowMillis(),
    ): PortalImportResult = withContext(Dispatchers.IO) {
        if (mapped.rows.isEmpty() && mapped.balanceMinor == null) {
            return@withContext PortalImportResult(skipped = mapped.skipped)
        }

        // La cuenta se resuelve UNA vez para todo el lote.
        val probe = mapped.rows.firstOrNull() ?: syntheticProbe(recipe)
        val account = accountId?.let { accountRepository.getById(it) }
            ?: accountRepository.findForCapture(recipe.institution, probe)

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

        // Saldo autoritativo del banco: una sola escritura, al final.
        var balanceUpdated = false
        mapped.balanceMinor?.let { balance ->
            runCatching {
                accountRepository.recordBalance(
                    accountId = account.id,
                    amountMinor = balance,
                    currencyCode = mapped.balanceCurrency ?: recipe.currency,
                    source = SourceType.PORTAL,
                    rawText = "portal:" + recipe.institution.key,
                    capturedAt = capturedAt,
                )
                balanceUpdated = true
            }
        }

        PortalImportResult(
            inserted = inserted,
            reconciled = reconciled,
            skipped = mapped.skipped,
            failed = failed,
            balanceMinor = mapped.balanceMinor,
            balanceUpdated = balanceUpdated,
            accountId = account.id,
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
