package dev.tubolsillo.app.data.source.csv

import dev.tubolsillo.app.core.csv.CsvParser
import dev.tubolsillo.app.core.db.dao.TagDao
import dev.tubolsillo.app.core.db.dao.TransactionDao
import dev.tubolsillo.app.core.db.entity.TransactionEntity
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.core.text.MerchantNormalizer
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.SourceType
import dev.tubolsillo.app.domain.model.TransactionCategory
import dev.tubolsillo.app.domain.model.TxStatus
import dev.tubolsillo.app.domain.repo.AccountRepository
import dev.tubolsillo.app.domain.usecase.DedupGate
import dev.tubolsillo.app.domain.usecase.RuleEngine
import dev.tubolsillo.app.domain.usecase.TransferMatcher
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.time.ExperimentalTime

/** Fila de CSV ya normalizada, lista para importar. */
data class CsvRow(
    val occurredAt: Long,
    val description: String,
    val amountMinor: Long,
    val currency: String,
    val balanceMinor: Long? = null,
    val rawLine: String? = null,
)

/** Resultado de una importación. */
data class CsvImportResult(
    val inserted: Int = 0,
    val reconciled: Int = 0,
    val failed: Int = 0,
)

/**
 * Importa estados de cuenta CSV con el parser común (core/csv/CsvParser).
 * Cada fila pasa por el MISMO camino que las capturas: dedup key (un import no
 * duplica lo que ya capturó un SMS), auto-etiquetado por reglas y matcher de
 * transferencias. Las filas que coinciden con una transacción existente se
 * marcan como reconciliadas.
 */
class CsvImporter(
    private val transactionDao: TransactionDao,
    private val tagDao: TagDao,
    private val ruleEngine: RuleEngine,
    private val transferMatcher: TransferMatcher,
    private val accountRepository: AccountRepository,
    private val dedupGate: DedupGate,
) {

    fun parseRows(csvText: String, template: CsvTemplate): List<CsvRow> =
        companionParseRows(csvText, template)

    suspend fun importRows(
        accountId: Long,
        rows: List<CsvRow>,
        template: CsvTemplate,
    ): CsvImportResult {
        var inserted = 0
        var reconciled = 0
        var failed = 0
        val institution = template.institution ?: Institution.OTHER

        for (row in rows) {
            try {
                val key = dedupGate.dedupKey(
                    institution = institution,
                    currency = row.currency,
                    amountMinor = row.amountMinor,
                    occurredAt = row.occurredAt,
                    merchant = row.description,
                )
                val existing = transactionDao.findByDedupKey(key)
                if (existing != null) {
                    transactionDao.markReconciled(existing.id)
                    reconciled++
                    continue
                }
                val txId = transactionDao.insert(
                    TransactionEntity(
                        accountId = accountId,
                        occurredAt = row.occurredAt,
                        amountMinor = row.amountMinor,
                        currency = row.currency,
                        description = row.description,
                        rawDescription = row.description,
                        category = TransactionCategory.OTHER.key,
                        status = TxStatus.CONFIRMED.key,
                        dedupKey = key,
                        source = SourceType.CSV.key,
                        rawJson = row.rawLine,
                        isReconciled = true,
                    )
                )
                ruleEngine.match(MerchantNormalizer.normalize(row.description))?.let { rule ->
                    rule.tagName?.let { tagName ->
                        tagDao.link(txId, tagDao.getOrCreate(tagName).id)
                    }
                    rule.categoryKey?.let { category ->
                        transactionDao.updateCategory(txId, category)
                    }
                }
                transactionDao.getById(txId)?.let { transferMatcher.matchFor(it) }
                inserted++
            } catch (_: Exception) {
                failed++
            }
        }
        return CsvImportResult(inserted = inserted, reconciled = reconciled, failed = failed)
    }

    companion object {

        fun companionParseRows(csvText: String, template: CsvTemplate): List<CsvRow> {
            val rows = CsvParser.readAll(csvText, template.delimiter)
            val dataRows = rows.drop(template.skipLines)
            val institution = template.institution ?: Institution.OTHER

            return dataRows.mapNotNull { line ->
                companionParseRow(line, template, institution)
            }
        }

        private fun companionParseRow(line: List<String>, template: CsvTemplate, institution: Institution): CsvRow? {
            fun value(field: CsvField): String? {
                val index = template.columnMap[field] ?: return null
                return line.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() }
            }

            val dateRaw = value(CsvField.DATE) ?: return null
            val description = value(CsvField.DESCRIPTION) ?: "Importado"
            val currency = value(CsvField.CURRENCY) ?: template.defaultCurrency

            val amountText = value(CsvField.AMOUNT)
            val inflow = value(CsvField.INFLOW)
            val outflow = value(CsvField.OUTFLOW)
            val signedAmount: String = when {
                !amountText.isNullOrBlank() -> amountText
                !inflow.isNullOrBlank() -> "+$inflow"
                !outflow.isNullOrBlank() -> "-$outflow"
                else -> return null
            }
            val amountMinor = AmountNormalizer.parseMinor(signedAmount, currency) ?: return null

            val occurredAt = parseDate(dateRaw, template.datePattern, template.timeZone) ?: return null
            val balanceMinor = value(CsvField.BALANCE)?.let { AmountNormalizer.parseMinor(it, currency) }

            return CsvRow(
                occurredAt = occurredAt,
                description = description,
                amountMinor = amountMinor,
                currency = currency,
                balanceMinor = balanceMinor,
                rawLine = line.joinToString(","),
            )
        }

        /**
         * Parsea fechas: epoch (s o ms), ISO instant, o un subconjunto de
         * patrones java.time-like (yyyy, MM, dd, HH, mm, ss con separadores
         * literales). Sin java.time: motor propio sobre kotlinx-datetime.
         */
        @OptIn(ExperimentalTime::class)
        fun parseDate(raw: String, pattern: String?, zone: TimeZone): Long? {
            val trimmed = raw.trim()
            trimmed.toLongOrNull()?.let { epoch ->
                return if (epoch < 10_000_000_000L) epoch * 1000 else epoch
            }
            runCatching { return Instant.parse(trimmed).toEpochMilliseconds() }

            val pat = pattern ?: "yyyy-MM-dd'T'HH:mm:ss"
            val intGroups = Regex("(\\d+)").findAll(trimmed).map { it.value.toIntOrNull() }.toList()
            val valid = setOf("yyyy", "MM", "dd", "HH", "mm", "ss")
            val tokens = Regex("[a-zA-Z]+").findAll(pat).map { it.value }.filter { it in valid }.toList()
            if (intGroups.size != tokens.size || intGroups.any { it == null }) return null

            val ints = intGroups.map { it!! }
            var year = 0; var month = 1; var day = 1
            var hour = 0; var minute = 0; var second = 0
            tokens.forEachIndexed { idx, token ->
                when (token) {
                    "yyyy" -> year = ints[idx]
                    "MM" -> month = ints[idx]
                    "dd" -> day = ints[idx]
                    "HH" -> hour = ints[idx]
                    "mm" -> minute = ints[idx]
                    "ss" -> second = ints[idx]
                }
            }
            if (year <= 0) return null
            return runCatching {
                LocalDateTime(year, month, day, hour, minute, second, 0)
                    .toInstant(zone).toEpochMilliseconds()
            }.getOrNull() ?: runCatching {
                LocalDate(year, month, day).atStartOfDayIn(zone).toEpochMilliseconds()
            }.getOrNull()
        }
    }
}
