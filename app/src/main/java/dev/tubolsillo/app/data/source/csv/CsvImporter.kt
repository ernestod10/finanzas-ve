package dev.tubolsillo.app.data.source.csv

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
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
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

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
 * Importa estados de cuenta CSV. Cada fila pasa por el MISMO camino que las
 * capturas: dedup key (por eso un import no duplica lo que ya capturó un SMS),
 * auto-etiquetado por reglas y matcher de transferencias. Las filas que
 * coinciden con una transacción existente se marcan como reconciliadas.
 */
@Singleton
class CsvImporter @Inject constructor(
    private val transactionDao: TransactionDao,
    private val tagDao: TagDao,
    private val ruleEngine: RuleEngine,
    private val transferMatcher: TransferMatcher,
    private val accountRepository: AccountRepository,
    private val dedupGate: DedupGate,
) {

    /** Parsea el contenido CSV con el template (sin tocar la BD). Estático para testear sin DAOs. */
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
                    // El CSV confirma una captura previa (SMS/push): reconciliar.
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
                // Auto-etiquetado + matcher de transferencias.
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

        /** Parsea el contenido CSV con el template (sin tocar la BD). */
        fun companionParseRows(csvText: String, template: CsvTemplate): List<CsvRow> {
            val rows = csvReader {
                delimiter = template.delimiter
            }.readAll(csvText)

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

            // Monto: columna directa, o inflow/outflow separados.
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

            val occurredAt = parseDate(dateRaw, template.datePattern, template.dateZone) ?: return null
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

        /** Parsea fechas: ISO, epoch millis, o patrón java.time. Null si no se puede. */
        fun parseDate(raw: String, pattern: String?, zone: ZoneId): Long? {
            val trimmed = raw.trim()
            trimmed.toLongOrNull()?.let { epoch ->
                // Epoch en segundos (10 dígitos) o millis (13).
                return if (epoch < 10_000_000_000L) epoch * 1000 else epoch
            }
            try {
                val instant = Instant.parse(trimmed)
                return instant.toEpochMilli()
            } catch (_: Exception) {
                // no ISO
            }
            val formatter = pattern?.let {
                runCatching { DateTimeFormatter.ofPattern(it) }.getOrNull()
            } ?: runCatching { DateTimeFormatter.ISO_LOCAL_DATE_TIME }.getOrNull()
            if (formatter == null) return null
            return try {
                LocalDateTime.parse(trimmed, formatter).atZone(zone).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try {
                    java.time.LocalDate.parse(trimmed, formatter).atStartOfDay(zone).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
