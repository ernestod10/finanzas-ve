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
    /**
     * Totales posibles, el más probable primero. La vista de factura los
     * ofrece para elegir con un toque cuando el OCR deja dudas.
     */
    val candidatesMinor: List<Long> = emptyList(),
    /** true si el total elegido cuadra con subtotal + IVA − descuento. */
    val reconciled: Boolean = false,
)

/**
 * Lectura por reglas de un ticket. Es el respaldo cuando el modelo no está
 * disponible, y también el que valida al modelo: si los dos coinciden, se
 * puede confiar más en el monto.
 *
 * El OCR real mete espacios donde no van ("Bs 3.891, 46"), parte el total en
 * dos líneas y confunde subtotales con totales; por eso primero se compactan
 * los números y el "TOTAL" se busca con sinónimos antes de caer al monto más
 * grande del ticket.
 */
object ReceiptParser {

    private val AMOUNT = Regex("(\\d{1,3}(?:[.,]\\d{3})+|\\d+)[.,](\\d{2})\\b")

    /** Etiqueta de total: "TOTAL", "total a pagar", "importe", "neto", "monto total". */
    private val TOTAL_LINE = Regex(
        "\\b(total\\s*(?:a\\s*pagar|general|factura|neto|bs)?|monto\\s*total|" +
            "importe\\s*(?:total)?|neto\\s*a\\s*pagar|a\\s*pagar)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** Líneas que NUNCA son el total, aunque digan "total". */
    private val NOT_TOTAL = Regex(
        "\\b(sub\\s*-?\\s*total|iva|i\\.v\\.a|exento|exenta|descuento|ahorro|" +
            "cambio|vuelto|recibido|efectivo|tarjeta|puntos|gratis|rif|c\\.?i\\.?|" +
            "cedula|telefono|telf)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val SUBTOTAL_LINE = Regex("\\b(sub\\s*-?\\s*total)\\b", RegexOption.IGNORE_CASE)
    private val IVA_LINE = Regex("\\b(iva|i\\.?\\s*v\\.?\\s*a\\.?|impuesto)\\b", RegexOption.IGNORE_CASE)
    private val DISCOUNT_LINE = Regex("\\b(descuento|descuentos|ahorro|rebaja)\\b", RegexOption.IGNORE_CASE)
    private val PERCENT = Regex("\\d+(?:[.,]\\d+)?\\s*%")

    private val MONEY_HINT = Regex(
        "(\\bbs\\b|\\$|\\bves\\b|\\busd\\b)",
        RegexOption.IGNORE_CASE,
    )

    private val DATE = Regex("\\b(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{2,4})\\b")

    private val MONTHS = mapOf(
        "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4,
        "mayo" to 5, "junio" to 6, "julio" to 7, "agosto" to 8,
        "septiembre" to 9, "setiembre" to 9, "octubre" to 10,
        "noviembre" to 11, "diciembre" to 12,
    )
    private val DATE_LONG = Regex(
        "\\b(\\d{1,2})\\s*(?:de\\s+)?(" + MONTHS.keys.joinToString("|") + ")\\s*(?:de\\s+)?(\\d{4})\\b",
        RegexOption.IGNORE_CASE,
    )

    private val NOISE = Regex(
        "rif|c[ií]\\s*-?\\s*f|telefono|tel[ée]fono|factura|recibo|cliente|caja|vendedor|" +
            "gracias|www\\.|http|@|\\btel\\b|secuencia|beneficiario|vendedor",
        RegexOption.IGNORE_CASE,
    )

    fun parse(rawText: String, currency: String = "VES", zone: TimeZone = TimeZone.currentSystemDefault()): ParsedReceipt {
        val text = compactNumbers(rawText)
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return ParsedReceipt()

        // 1) Línea con "TOTAL" y su monto (puede estar en la línea siguiente).
        val totalIndex = lines.indexOfFirst { TOTAL_LINE.containsMatchIn(it) && !NOT_TOTAL.containsMatchIn(it) }
        val explainedTotal = if (totalIndex >= 0) {
            amountNear(lines, totalIndex, currency)
        } else {
            null
        }

        // 2) Reconciliación: subtotal + IVA − descuento debe dar el total.
        val expected = reconciledTotal(lines, currency)

        // 3) Respaldo: el monto más grande, salteando IVA, descuentos y vueltos.
        val money = largestAmount(lines, currency)
        val any = largestAmountLoose(lines, currency)

        val allAmounts = lines.mapNotNull { lastAmountIn(it, currency) }
        fun appears(value: Long) = allAmounts.any { kotlin.math.abs(it - value) <= 2 }

        // Orden de confianza: lo que cuadra con la aritmética (si además
        // aparece escrito en el ticket) manda sobre la etiqueta; luego la
        // etiqueta, el monto con moneda y por último el más grande.
        val second = secondLargestAmount(lines, currency, any)

        fun near(a: Long, b: Long): Boolean {
            val tolerance = maxOf(2L, b / 100) // 1 %: variantes del mismo monto
            return kotlin.math.abs(a - b) <= tolerance
        }

        val ordered = buildList {
            if (expected != null && explainedTotal != null &&
                kotlin.math.abs(expected - explainedTotal) > 2 && appears(expected)
            ) {
                add(expected)
                add(explainedTotal)
            } else {
                explainedTotal?.let { add(it) }
                expected?.let { add(it) }
            }
            money?.let { add(it) }
            any?.let { add(it) }
        }

        val candidates = mutableListOf<Long>()
        ordered.forEach { value -> if (candidates.none { near(it, value) }) candidates.add(value) }
        // Si solo hay una lectura, el segundo monto más grande es la
        // alternativa razonable (precio unitario vs total, por ejemplo).
        if (candidates.size == 1 && second != null && !near(second, candidates[0])) {
            candidates.add(second)
        }

        val total = candidates.firstOrNull()
        return ParsedReceipt(
            merchant = merchantOf(lines),
            totalMinor = total,
            occurredAt = dateOf(text, zone),
            totalExplained = explainedTotal != null,
            candidatesMinor = candidates,
            reconciled = expected != null && total == expected,
        )
    }

    /**
     * "Bs 3.891, 46" → "Bs 3.891,46" y "8. 746, 52" → "8.746,52". El
     * `AmountNormalizer` ya tolera separadores mezclados; solo le estorbaban
     * los espacios que mete el OCR entre dígitos y separadores.
     */
    private fun compactNumbers(text: String): String {
        val separators = text.replace(Regex("(?<=\\d)[ \\t]*([.,])[ \\t]*(?=\\d)"), "$1")
        // "115.707 88": el OCR perdió la coma decimal y dejó un espacio. Se
        // reconstruye solo cuando ya hay separador de miles (2+ grupos).
        val decimal = separators.replace(
            Regex("(\\d{1,3}(?:[.,]\\d{3})+)[ \\t]+(\\d{2})\\b"),
            "$1,$2",
        )
        // "204 266,46": falta el punto de miles. Se recompone el grupo.
        return decimal.replace(
            Regex("(\\d{1,3})[ \\t]+(\\d{3}[.,]\\d{2})\\b"),
            "$1.$2",
        )
    }

    /** Monto de la línea de [index], o de la siguiente si la etiqueta quedó sola. */
    private fun amountNear(lines: List<String>, index: Int, currency: String): Long? =
        lastAmountIn(lines[index], currency)
            ?: lines.drop(index + 1).take(2).firstNotNullOfOrNull { lastAmountIn(it, currency) }

    /** Último monto de la línea (en "Total: Bs. 1.240,00" interesa el final). */
    private fun lastAmountIn(line: String, currency: String): Long? =
        AMOUNT.findAll(PERCENT.replace(line, " "))
            .lastOrNull()
            ?.value
            ?.let { AmountNormalizer.parseMinor(it, currency) }
            ?.takeIf { it > 0 }

    /**
     * Subtotal + IVA − descuento. Es la única validación cruzada que se puede
     * hacer sin depender de que el OCR haya leído la palabra "TOTAL": si el
     * subtotal existe y la cuenta cierra, ese monto es el total del ticket.
     */
    private fun reconciledTotal(lines: List<String>, currency: String): Long? {
        val subtotalIndex = lines.indexOfFirst { SUBTOTAL_LINE.containsMatchIn(it) }
        if (subtotalIndex < 0) return null
        val subtotal = amountNear(lines, subtotalIndex, currency) ?: return null
        val ivaIndex = lines.indexOfFirst { IVA_LINE.containsMatchIn(it) }
        val iva = if (ivaIndex >= 0) amountNear(lines, ivaIndex, currency) ?: 0L else 0L
        val discountIndex = lines.indexOfFirst { DISCOUNT_LINE.containsMatchIn(it) }
        val discount = if (discountIndex >= 0) amountNear(lines, discountIndex, currency) ?: 0L else 0L
        val expected = subtotal + iva - discount
        return expected.takeIf { it > 0 }
    }

    /** Segundo monto distinto más grande del ticket. */
    private fun secondLargestAmount(lines: List<String>, currency: String, largest: Long?): Long? {
        val amounts = lines.filterNot { NOT_TOTAL.containsMatchIn(it) }
            .mapNotNull { lastAmountIn(it, currency) }
            .sortedDescending()
            .distinct()
        return amounts.firstOrNull { value -> largest == null || kotlin.math.abs(value - largest) > maxOf(2L, largest / 100) }
    }

    /** Monto más grande sin filtrar por símbolo de moneda. */
    private fun largestAmountLoose(lines: List<String>, currency: String): Long? =
        lines.filterNot { NOT_TOTAL.containsMatchIn(it) }
            .mapNotNull { lastAmountIn(it, currency) }
            .maxOrNull()

    /**
     * Respaldo: el monto más grande del ticket suele ser el total. Si alguna
     * línea trae símbolo de moneda ("Bs", "$", "VES"), se prefiere ese
     * conjunto: evita confundir cantidades o códigos con dinero.
     */
    private fun largestAmount(lines: List<String>, currency: String): Long? {
        val withoutNoise = lines.filterNot { NOT_TOTAL.containsMatchIn(it) }
        val usable = if (withoutNoise.isEmpty()) lines else withoutNoise
        val withSymbol = usable.filter { MONEY_HINT.containsMatchIn(it) }
        val fromSymbol = withSymbol.mapNotNull { lastAmountIn(it, currency) }.maxOrNull()
        return fromSymbol ?: usable.mapNotNull { lastAmountIn(it, currency) }.maxOrNull()
    }

    private fun merchantOf(lines: List<String>): String? =
        lines.asSequence()
            .take(6)
            .firstOrNull { line ->
                line.count { it.isLetter() } >= 3 &&
                    !NOISE.containsMatchIn(line) &&
                    !AMOUNT.containsMatchIn(line) &&
                    !TOTAL_LINE.containsMatchIn(line)
            }
            ?.trim(' ', '-', '*', '_', '|', '.', ',')
            ?.take(40)
            ?.takeIf { it.isNotBlank() }

    private fun dateOf(text: String, zone: TimeZone): Long? {
        DATE.find(text)?.let { match ->
            val (day, month, year) = match.destructured
            val pattern = if (year.length <= 2) "dd/MM/yy" else "dd/MM/yyyy"
            DateParser.parse("$day/$month/$year", pattern, zone)?.let { return it }
        }
        DATE_LONG.find(text)?.let { match ->
            val (day, monthName, year) = match.destructured
            val month = MONTHS[monthName.lowercase()] ?: return@let
            val paddedMonth = month.toString().padStart(2, '0')
            val paddedDay = day.padStart(2, '0')
            DateParser.parse("$paddedDay/$paddedMonth/$year", "dd/MM/yyyy", zone)?.let { return it }
        }
        return null
    }
}
