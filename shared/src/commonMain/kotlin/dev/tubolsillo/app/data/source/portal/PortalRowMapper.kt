package dev.tubolsillo.app.data.source.portal

import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.core.text.MerchantNormalizer
import dev.tubolsillo.app.core.time.DateParser
import dev.tubolsillo.app.domain.model.ParsedTransaction
import dev.tubolsillo.app.domain.model.TransactionCategory
import kotlinx.datetime.TimeZone
import kotlin.math.abs

/** Resultado de mapear un payload completo. */
data class PortalMapResult(
    val rows: List<ParsedTransaction> = emptyList(),
    val skipped: Int = 0,
    val balanceMinor: Long? = null,
    val balanceCurrency: String? = null,
)

/**
 * Convierte las filas crudas del portal en [ParsedTransaction]. Función pura:
 * sin DAOs, sin corrutinas, testeable con kotlin.test.
 *
 * El signo NUNCA se delega a [AmountNormalizer]: ese filtra los caracteres no
 * numéricos, así que "(1.234,56)" sale positivo y "1.234,56 D" pierde la marca.
 * Se toma el valor absoluto y se aplica la política de la receta.
 */
object PortalRowMapper {

    const val PARSER_VERSION = 1

    fun map(payload: PortalPayload, recipe: PortalRecipe, fallbackOccurredAt: Long): PortalMapResult {
        val parsed = mutableListOf<ParsedTransaction>()
        var skipped = 0
        payload.rows.forEach { row ->
            val tx = mapRow(row, recipe, fallbackOccurredAt)
            if (tx == null) skipped++ else parsed.add(tx)
        }
        return PortalMapResult(
            rows = parsed,
            skipped = skipped,
            balanceMinor = balanceMinorOf(payload, recipe),
            balanceCurrency = if (payload.balanceText != null) recipe.currency else null,
        )
    }

    fun mapRow(row: PortalRawRow, recipe: PortalRecipe, fallbackOccurredAt: Long): ParsedTransaction? {
        val zone = runCatching { TimeZone.of(recipe.timeZoneId) }.getOrElse { TimeZone.UTC }
        val occurredAt = DateParser.parse(row.date, recipe.dateFormat, zone) ?: return null
        val magnitude = amountMinorOf(row, recipe) ?: return null
        if (magnitude == 0L) return null
        val amountMinor = magnitude * signOf(row, recipe)
        val merchant = merchantOf(row)
        val description = descriptionOf(row)
        return ParsedTransaction(
            institution = recipe.institution,
            occurredAt = occurredAt,
            amountMinor = amountMinor,
            currency = recipe.currency,
            merchant = merchant,
            description = description,
            category = TransactionCategory.OTHER,
            balanceMinor = row.balance.takeIf { it.isNotBlank() }
                ?.let { AmountNormalizer.parseMinor(it, recipe.currency) }?.let { abs(it) },
            balanceCurrency = if (row.balance.isNotBlank()) recipe.currency else null,
            parserVersion = PARSER_VERSION,
            raw = row.raw.ifBlank { listOf(row.date, row.description, row.amount).joinToString(" ") },
        )
    }

    /** Magnitud del movimiento, siempre positiva. */
    fun amountMinorOf(row: PortalRawRow, recipe: PortalRecipe): Long? {
        val text = when {
            row.amount.isNotBlank() -> row.amount
            row.debit.isNotBlank() -> row.debit
            row.credit.isNotBlank() -> row.credit
            else -> return null
        }
        val minor = AmountNormalizer.parseMinor(text, recipe.currency) ?: return null
        return abs(minor)
    }

    /** +1 suma, -1 resta, según la política declarada en la receta. */
    fun signOf(row: PortalRawRow, recipe: PortalRecipe): Int = when (recipe.signPolicy) {
        SignPolicy.ALL_NEGATIVE -> -1

        SignPolicy.DEBIT_CREDIT_COLUMNS -> when {
            row.debit.isNotBlank() -> -1
            row.credit.isNotBlank() -> 1
            else -> hintSign(row, recipe)
        }

        SignPolicy.AMOUNT_SIGNED -> {
            val text = row.amount.ifBlank { row.debit.ifBlank { row.credit } }
            val negative = text.contains('-') || (text.contains('(') && text.contains(')')) ||
                text.trimEnd().endsWith("D", ignoreCase = true)
            if (negative) -1 else 1
        }

        SignPolicy.HINTS_FROM_DESCRIPTION -> hintSign(row, recipe)
    }

    private fun hintSign(row: PortalRawRow, recipe: PortalRecipe): Int {
        val haystack = MerchantNormalizer.normalize(
            listOf(row.description, row.raw).joinToString(" ")
        )
        if (recipe.positiveHints.any { haystack.contains(MerchantNormalizer.normalize(it)) }) return 1
        if (recipe.negativeHints.any { haystack.contains(MerchantNormalizer.normalize(it)) }) return -1
        return -1
    }

    fun merchantOf(row: PortalRawRow): String {
        val base = row.description.ifBlank { row.raw }.trim()
        return base.take(80).ifBlank { "Movimiento" }
    }

    private fun descriptionOf(row: PortalRawRow): String {
        val base = merchantOf(row)
        return if (row.reference.isBlank()) base else base + " · Ref " + row.reference.trim()
    }

    /** Saldo del portal: autoritativo, se aplica una sola vez por consulta. */
    fun balanceMinorOf(payload: PortalPayload, recipe: PortalRecipe): Long? {
        val text = payload.balanceText?.takeIf { it.isNotBlank() } ?: return null
        return AmountNormalizer.parseMinor(text, recipe.currency)
    }
}
