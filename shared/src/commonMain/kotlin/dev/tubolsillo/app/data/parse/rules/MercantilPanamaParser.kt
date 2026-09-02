package dev.tubolsillo.app.data.parse.rules

import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.data.parse.InstitutionParser
import dev.tubolsillo.app.data.parse.ParserSupport
import dev.tubolsillo.app.data.parse.ParserTemplate
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.ParsedTransaction
import dev.tubolsillo.app.domain.model.TransactionCategory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMS/push de Mercantil Panamá. Montos en USD con prefijo "B/.".
 * v1 con compra/retiro genéricos; se afina con el corpus real.
 */
@Singleton
class MercantilPanamaParser @Inject constructor() : InstitutionParser {

    override val institution = Institution.MERCANTIL_PA
    override val version = 1

    private val templates = listOf(
        // "Compra por B/. 12.34 en FARMACIA X"
        ParserTemplate(
            regex = Regex(
                "(?i)compra\\s+(?:por\\s+)?b/\\.?\\s*(?<amount>[\\d.,]+)\\s+(?:en\\s+)?(?<merchant>.+?)(?:\\.\\s*saldo:?.*|\\.\\s*$|$)"
            ),
            category = TransactionCategory.MERCHANT,
            amountSign = -1,
        ),
        // "Retiro por B/. 50.00 en ATM ..."
        ParserTemplate(
            regex = Regex(
                "(?i)retiro\\s+(?:por\\s+)?b/\\.?\\s*(?<amount>[\\d.,]+)\\s+(?:en\\s+)?(?<merchant>.+?)(?:\\.\\s*saldo:?.*|\\.\\s*$|$)"
            ),
            category = TransactionCategory.MERCHANT,
            amountSign = -1,
        ),
        // Genérico: cualquier texto con "B/. <monto>"
        ParserTemplate(
            regex = Regex("(?i)b/\\.?\\s*(?<amount>[\\d.,]+)"),
            category = TransactionCategory.OTHER,
            amountSign = -1,
        ),
    )

    override fun parse(text: String, occurredAt: Long): ParsedTransaction? {
        val looksLikePanama = text.contains("B/.", ignoreCase = true) ||
            text.contains("Mercantil", ignoreCase = true)
        if (!looksLikePanama) return null

        for (template in templates) {
            val match = template.regex.find(text) ?: continue
            val amountMinor = AmountNormalizer.parseMinor(match.groups["amount"]!!.value, "USD")
                ?: continue
            val merchant = ParserSupport.groupOrNull(match, "merchant")?.let { ParserSupport.cleanMerchant(it) }
                ?: "Mercantil Panamá"
            val signed = if (template.amountSign < 0) -kotlin.math.abs(amountMinor)
            else kotlin.math.abs(amountMinor)
            return ParsedTransaction(
                institution = institution,
                occurredAt = occurredAt,
                amountMinor = signed,
                currency = "USD",
                merchant = merchant,
                description = merchant,
                category = template.category,
                balanceMinor = ParserSupport.extractBalance(text, "USD"),
                balanceCurrency = "USD",
                parserVersion = version,
                raw = text,
            )
        }
        return null
    }
}
