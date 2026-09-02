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

/** SMS/push de Bancaribe. v1 con patrones estándar VE; se afina con corpus real. */
@Singleton
class BancaribeParser @Inject constructor() : InstitutionParser {

    override val institution = Institution.BANCARIBE
    override val version = 1

    private val templates = listOf(
        ParserTemplate(
            regex = Regex(
                "(?i)compra\\s+por\\s+(?:bs\\.?|bsd)?\\s*(?<amount>[\\d.,]+)\\s+en\\s+(?<merchant>.+?)(?:\\.\\s*saldo:?.*|\\.\\s*$|$)"
            ),
            category = TransactionCategory.MERCHANT,
            amountSign = -1,
        ),
        ParserTemplate(
            regex = Regex(
                "(?i)pago\\s+movil\\s+recibido\\s+(?:por\\s+)?(?:bs\\.?)?\\s*(?<amount>[\\d.,]+)\\s+(?:de|desde)\\s+(?<merchant>[A-Za-z]?\\d+)"
            ),
            category = TransactionCategory.PM_RECEIVED,
            amountSign = +1,
        ),
        ParserTemplate(
            regex = Regex(
                "(?i)transferencia\\s+(?:recibida|enviada|realizada)\\s+(?:por\\s+)?(?:bs\\.?)?\\s*(?<amount>[\\d.,]+)"
            ),
            category = TransactionCategory.OTHER,
            amountSign = -1,
        ),
    )

    override fun parse(text: String, occurredAt: Long): ParsedTransaction? {
        if (!text.contains("Bancaribe", ignoreCase = true) &&
            !text.contains("Conexión", ignoreCase = true)
        ) return null

        for (template in templates) {
            val match = template.regex.find(text) ?: continue
            val amountMinor = AmountNormalizer.parseMinor(match.groups["amount"]!!.value, "VES")
                ?: continue
            val merchant = ParserSupport.groupOrNull(match, "merchant")?.let { ParserSupport.cleanMerchant(it) }
                ?: "Bancaribe"
            val signed = if (template.amountSign < 0) -kotlin.math.abs(amountMinor)
            else kotlin.math.abs(amountMinor)
            return ParsedTransaction(
                institution = institution,
                occurredAt = occurredAt,
                amountMinor = signed,
                currency = "VES",
                merchant = merchant,
                description = merchant,
                category = template.category,
                balanceMinor = ParserSupport.extractBalance(text, "VES"),
                balanceCurrency = "VES",
                parserVersion = version,
                raw = text,
            )
        }
        return null
    }
}
