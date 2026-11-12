package dev.tubolsillo.app.domain.receipts

import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.core.time.DateParser
import kotlinx.datetime.TimeZone

/** Lo que se puede sacar de una factura sin IA, solo con el texto del OCR. */
data class ParsedReceipt(
    val merchant: String? = null,
    val totalMinor: Long? = null,
    val occurredAt: Long? = null,
    /** true si el monto salió de una línea que dice "TOTAL". */
    val totalExplained: Boolean = false,
)

/**
 * Lectura por reglas de un ticket. Es el respaldo cuando el modelo no está
 * disponible, y también el que valida al modelo: si los dos coinciden, se
 * puede confiar más en el monto.
 */
object ReceiptParser {

    private val AMOUNT = Regex("(\\d{1,3}(?:[.,]\\d{3})+|\\d+)[.,](\\d{2})\\b")
    private val TOTAL_LINE = Regex(
        "\\b(total\\s*(?:a\\s*pagar|general|factura)?|monto\\s*total|importe\\s*total)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val DATE = Regex("\\b(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{2,4})\\b")
    private val NOISE = Regex(
        "rif|c[ií]\\s*-?\\s*f|telefono|tel[ée]fono|factura|recibo|cliente|caja|vendedor|" +
            "gracias|www\\.|http|@|\\btel\\b",
        RegexOption.IGNORE_CASE,
    )

    fun parse(rawText: String, currency: String = "VES", zone: TimeZone = TimeZone.currentSystemDefault()): ParsedReceipt {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return ParsedReceipt()

        val totalLine = lines.firstOrNull { TOTAL_LINE.containsMatchIn(it) }
        val explainedTotal = totalLine?.let { lastAmountIn(it, currency) }
        val total = explainedTotal ?: largestAmount(lines, currency)

        return ParsedReceipt(
            merchant = merchantOf(lines),
            totalMinor = total,
            occurredAt = dateOf(rawText, zone),
            totalExplained = explainedTotal != null,
        )
    }

    /** Último monto de la línea (en "Total: Bs. 1.240,00" interesa el final). */
    private fun lastAmountIn(line: String, currency: String): Long? =
        AMOUNT.findAll(line).lastOrNull()
            ?.value
            ?.let { AmountNormalizer.parseMinor(it, currency) }
            ?.takeIf { it > 0 }

    /** Respaldo: el monto más grande del ticket suele ser el total. */
    private fun largestAmount(lines: List<String>, currency: String): Long? =
        lines.mapNotNull { lastAmountIn(it, currency) }.maxOrNull()

    private fun merchantOf(lines: List<String>): String? =
        lines.asSequence()
            .take(4)
            .firstOrNull { line ->
                line.count { it.isLetter() } >= 3 &&
                    !NOISE.containsMatchIn(line) &&
                    !AMOUNT.containsMatchIn(line)
            }
            ?.take(40)

    private fun dateOf(text: String, zone: TimeZone): Long? {
        val match = DATE.find(text) ?: return null
        val (day, month, year) = match.destructured
        val pattern = if (year.length <= 2) "dd/MM/yy" else "dd/MM/yyyy"
        return DateParser.parse("$day/$month/$year", pattern, zone)
    }
}
