package dev.tubolsillo.app.data.parse.rules

import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.data.parse.InstitutionParser
import dev.tubolsillo.app.data.parse.ParserSupport
import dev.tubolsillo.app.data.parse.ParserTemplate
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.ParsedTransaction
import dev.tubolsillo.app.domain.model.TransactionCategory

/**
 * Push de Cashea (com.cashea.app). Cashea opera TODO en USD (el Bs. es solo
 * el medio de pago al cobrar, al cambio BCV), así que los montos se parsean
 * en USD. v1 best-effort; se afina con corpus real.
 */
class CasheaParser() : InstitutionParser {

    override val institution = Institution.CASHEA
    override val version = 1

    private val templates = listOf(
        // "Tu compra en FARMACIA X fue aprobada por Bs. 100,00"
        ParserTemplate(
            regex = Regex(
                "(?i)compra\\s+(?:en\\s+)?(?<merchant>.+?)\\s+(?:fue\\s+)?aprobada.*?(?:por\\s+)?(?:bs\\.?)?\\s*(?<amount>[\\d.,]+)"
            ),
            category = TransactionCategory.MERCHANT,
            amountSign = -1,
        ),
        // "Recuerda pagar tu cuota de Bs. 50,00 antes del ..."
        ParserTemplate(
            regex = Regex(
                "(?i)cuota\\s+(?:de\\s+)?(?:bs\\.?)?\\s*(?<amount>[\\d.,]+)"
            ),
            category = TransactionCategory.OTHER,
            amountSign = -1,
        ),
        // Genérico: "Cashea ... Bs. X"
        ParserTemplate(
            regex = Regex("(?i)cashea.*?(?:bs\\.?)?\\s*(?<amount>[\\d.,]+)"),
            category = TransactionCategory.OTHER,
            amountSign = -1,
        ),
    )

    override fun parse(text: String, occurredAt: Long): ParsedTransaction? {
        if (!text.contains("Cashea", ignoreCase = true)) return null

        for (template in templates) {
            val match = template.regex.find(text) ?: continue
            // La moneda sale del propio aviso: "Bs. 100,00" son bolívares, y
            // "$25" o sin símbolo son dólares (Cashea opera en USD).
            val currency = currencyOf(match.value)
            val amountMinor = AmountNormalizer.parseMinor(match.groups["amount"]!!.value, currency)
                ?: continue
            val merchant = ParserSupport.groupOrNull(match, "merchant")?.let { ParserSupport.cleanMerchant(it) }
                ?: "Cashea"
            val signed = if (template.amountSign < 0) -kotlin.math.abs(amountMinor)
            else kotlin.math.abs(amountMinor)
            return ParsedTransaction(
                institution = institution,
                occurredAt = occurredAt,
                amountMinor = signed,
                currency = currency,
                merchant = merchant,
                description = "Cashea · $merchant",
                category = template.category,
                parserVersion = version,
                raw = text,
            )
        }
        return null
    }

    private fun currencyOf(fragment: String): String {
        val lower = fragment.lowercase()
        return when {
            Regex("bsd?\\.?\\s*\\d").containsMatchIn(lower) || lower.contains("bs.") -> "VES"
            else -> "USD"
        }
    }
}
