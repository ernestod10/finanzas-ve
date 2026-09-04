package dev.tubolsillo.app.data.parse

import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.domain.model.TransactionCategory

/**
 * Plantilla de parseo: regex con named groups (amount, merchant, balance) +
 * tipo de transacción y signo del monto.
 */
data class ParserTemplate(
    val regex: Regex,
    val category: TransactionCategory,
    val amountSign: Int, // -1 = gasto (compra/envío), +1 = ingreso (recibido)
)

/** Helpers compartidos por los parsers de instituciones. */
object ParserSupport {

    private val balanceRegex = Regex(
        "(?i)saldo(?:\\s+disponible)?:?\\s*(?:bs\\.?|bsd|b/\\.|\\$)?\\s*(?<balance>[\\d.,]+)"
    )

    /** Extrae el saldo mencionado en el texto ("Saldo: Bs. 1.234,56"), o null. */
    fun extractBalance(text: String, currency: String): Long? {
        val match = balanceRegex.find(text) ?: return null
        return AmountNormalizer.parseMinor(match.groups["balance"]!!.value, currency)
    }

    /** Limpia el nombre del comercio extraído (trim, punto final). */
    fun cleanMerchant(raw: String): String =
        raw.trim().trimEnd('.', ' ', '·').ifBlank { "Desconocido" }

    /**
     * Valor de un named group, o null si el template no lo define.
     * (Kotlin lanza IllegalArgumentException al acceder a un grupo inexistente,
     * así que no se puede usar `groups[name]` directo en templates opcionales.)
     */
    fun groupOrNull(match: MatchResult, name: String): String? = try {
        match.groups[name]?.value
    } catch (_: IllegalArgumentException) {
        null
    }
}
