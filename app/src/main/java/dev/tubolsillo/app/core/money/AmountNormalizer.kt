package dev.tubolsillo.app.core.money

/**
 * Convierte montos en texto (SMS, push, CSV) a minor units de forma tolerante.
 *
 * Ambigüedad clásica en mensajes bancarios venezolanos: la coma y el punto se
 * usan indistintamente como separador decimal y de miles ("1.234,56",
 * "1,234.56", "25,00", "1.234"). Heurística:
 *  - Ambos separadores presentes → el que aparece ÚLTIMO es el decimal, el otro es miles.
 *  - Un solo separador → 2 dígitos después = decimal; 3 dígitos después = miles;
 *    1 dígito después = decimal ("1,5" = 1.50).
 * El texto crudo siempre se conserva en la entidad de la transacción, así que
 * un parseo errado se puede corregir manualmente.
 */
object AmountNormalizer {

    private val allowed = Regex("[^0-9.,-]")

    /** Devuelve minor units, o null si el texto no contiene un número válido. */
    fun parseMinor(text: String, code: String): Long? = parseMinor(text, Currency.of(code))

    fun parseMinor(text: String, currency: Currency): Long? {
        val cleaned = text.replace(allowed, "").trim()
        if (cleaned.isEmpty()) return null

        val negative = cleaned.startsWith("-")
        val unsigned = cleaned.trimStart('-')

        val lastComma = unsigned.lastIndexOf(',')
        val lastDot = unsigned.lastIndexOf('.')
        val hasBoth = lastComma >= 0 && lastDot >= 0

        if (hasBoth) {
            // El último separador es el decimal; el otro se elimina.
            return if (lastComma > lastDot) {
                buildMinor(unsigned.replace(".", "").replace(',', '.'), currency, negative)
            } else {
                buildMinor(unsigned.replace(",", ""), currency, negative)
            }
        }

        val sepIndex = maxOf(lastComma, lastDot)
        if (sepIndex < 0) {
            // Entero puro: "2500".
            val major = unsigned.toLongOrNull() ?: return null
            return signed(major * Money.pow10Long(currency.decimals), negative)
        }

        if (currency.decimals > 2) {
            // Crypto: el separador es decimal casi siempre ("0.00001234", "1,5").
            return buildMinor(unsigned.replace(",", "."), currency, negative)
        }

        val digitsAfter = unsigned.length - sepIndex - 1
        return when {
            digitsAfter == 2 || digitsAfter == 1 -> {
                // Decimal: "25,00" → 2500 minor, "1,5" → 150 minor.
                val major = unsigned.substring(0, sepIndex).replace(",", "").replace(".", "")
                val frac = unsigned.substring(sepIndex + 1).padEnd(2, '0').take(2)
                val minor = (major.toLongOrNull() ?: return null) * Money.pow10Long(currency.decimals)
                val fracMinor =
                    frac.toLong() * Money.pow10Long(currency.decimals) / 100
                signed(minor + fracMinor, negative)
            }
            else -> {
                // Miles: "1.234" → 1234 major. Los dígitos sobrantes se ignoran.
                val major = unsigned.replace(",", "").replace(".", "")
                val value = major.toLongOrNull() ?: return null
                signed(value * Money.pow10Long(currency.decimals), negative)
            }
        }
    }

    private fun buildMinor(normalized: String, currency: Currency, negative: Boolean): Long? {
        val dot = normalized.indexOf('.')
        if (dot < 0) {
            val major = normalized.toLongOrNull() ?: return null
            return signed(major * Money.pow10Long(currency.decimals), negative)
        }
        val major = normalized.substring(0, dot).toLongOrNull() ?: return null
        val fracRaw = normalized.substring(dot + 1).take(currency.decimals)
            .padEnd(currency.decimals, '0')
        val frac = fracRaw.toLongOrNull() ?: return null
        return signed(major * Money.pow10Long(currency.decimals) + frac, negative)
    }

    private fun signed(value: Long, negative: Boolean): Long = if (negative) -value else value
}
