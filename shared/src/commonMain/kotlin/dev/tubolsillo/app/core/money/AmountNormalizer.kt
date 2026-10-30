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
        val isZeroMajor = unsigned.substring(0, sepIndex).all { it == '0' || it == '.' || it == ',' }

        return when {
            digitsAfter == 3 && !isZeroMajor -> {
                // Miles: "1.234" → 1234 major.
                val major = unsigned.replace(",", "").replace(".", "")
                val value = major.toLongOrNull() ?: return null
                signed(value * Money.pow10Long(currency.decimals), negative)
            }
            else -> {
                // Decimal: "25,00", "1,5", "14.09210875" USDT, "0.123"
                val majorStr = unsigned.substring(0, sepIndex).replace(",", "").replace(".", "")
                val major = if (majorStr.isEmpty()) 0L else (majorStr.toLongOrNull() ?: return null)
                val fracStr = unsigned.substring(sepIndex + 1).padEnd(currency.decimals, '0').take(currency.decimals)
                val frac = if (fracStr.isEmpty()) 0L else (fracStr.toLongOrNull() ?: return null)
                signed(major * Money.pow10Long(currency.decimals) + frac, negative)
            }
        }
    }

    private fun buildMinor(normalized: String, currency: Currency, negative: Boolean): Long? {
        val dot = normalized.indexOf('.')
        if (dot < 0) {
            val major = normalized.toLongOrNull() ?: return null
            return signed(major * Money.pow10Long(currency.decimals), negative)
        }
        val majorStr = normalized.substring(0, dot)
        val major = if (majorStr.isEmpty()) 0L else (majorStr.toLongOrNull() ?: return null)
        val fracRaw = normalized.substring(dot + 1).take(currency.decimals)
            .padEnd(currency.decimals, '0')
        val frac = if (fracRaw.isEmpty()) 0L else (fracRaw.toLongOrNull() ?: return null)
        return signed(major * Money.pow10Long(currency.decimals) + frac, negative)
    }

    private fun signed(value: Long, negative: Boolean): Long = if (negative) -value else value
}
