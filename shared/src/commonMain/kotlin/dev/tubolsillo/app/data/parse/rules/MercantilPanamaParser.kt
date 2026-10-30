package dev.tubolsillo.app.data.parse.rules

import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.data.parse.InstitutionParser
import dev.tubolsillo.app.data.parse.ParserSupport
import dev.tubolsillo.app.data.parse.ParserTemplate
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.ParsedTransaction
import dev.tubolsillo.app.domain.model.TransactionCategory

/**
 * SMS/push/correo de Mercantil Panamá. Montos en USD con prefijo "$" o "B/.".
 * v2: soporta compras con tarjeta de débito/crédito, transferencias y envíos Zinli,
 * retiros y formato de correos transaccionales.
 */
class MercantilPanamaParser() : InstitutionParser {

    override val institution = Institution.MERCANTIL_PA
    override val version = 2

    private val templates = listOf(
        // 1) Transferencia / Envío Zinli desde app o correo:
        // "Tu envío de dinero a Zinli desde Mercantil BANCA MÓVIL se realizó con éxito... Monto: $ 50.0"
        // "Enviaste una transferencia a Zinli... Monto: $ 50.0"
        ParserTemplate(
            regex = Regex(
                """(?is)(?:enviaste\s+una\s+transferencia\s+a|transferencia\s+(?:enviada\s+)?a|env[ií]o\s+de\s+dinero\s+a)\s+(?<merchant>[^\r\n,.]+?)(?:\s+desde|\r|\n|\.|·|$).*?monto:\s*(?:\$|b/\.?)\s*(?<amount>[\d.,]+)"""
            ),
            category = TransactionCategory.TRANSFER,
            amountSign = -1,
        ),
        // 2) Transferencia recibida:
        // "Recibiste una transferencia de X... Monto: $ 50.0"
        ParserTemplate(
            regex = Regex(
                """(?is)(?:recibiste\s+una\s+transferencia\s+de|transferencia\s+recibida\s+de)\s+(?<merchant>[^\r\n,.]+?)(?:\s+desde|\r|\n|\.|·|$).*?monto:\s*(?:\$|b/\.?)\s*(?<amount>[\d.,]+)"""
            ),
            category = TransactionCategory.TRANSFER,
            amountSign = +1,
        ),
        // 3) Transferencia simple en una línea: "Transferencia a Zinli por $ 50.00"
        ParserTemplate(
            regex = Regex(
                """(?i)transferencia\s+(?:enviada\s+)?(?:a\s+)?(?<merchant>[^.,\n]+?)\s+por\s+(?:\$|b/\.?)\s*(?<amount>[\d.,]+)"""
            ),
            category = TransactionCategory.TRANSFER,
            amountSign = -1,
        ),
        // 4) Compra con tarjeta débito o crédito / punto de venta:
        // "Gracias por tu Compra por $ 7.47 en FARMACIA FARMAGO CA SA, con la tarjeta MasterCard Débito terminada en *3899."
        // "Compra por B/. 12.34 en FARMACIA X"
        ParserTemplate(
            regex = Regex(
                """(?i)(?:gracias\s+por\s+tu\s+)?compra\s+(?:por\s+)?(?:\$|b/\.?)\s*(?<amount>[\d.,]+)\s+(?:en\s+)?(?<merchant>.+?)(?:,?\s*con\s+la\s+tarjeta.+?|\.\s*saldo:?.*|\.\s*$|$)"""
            ),
            category = TransactionCategory.MERCHANT,
            amountSign = -1,
        ),
        // 5) Retiro cajero / ATM:
        // "Retiro por $ 50.00 en ATM ..." / "Retiro por B/. 50.00 en ATM ..."
        ParserTemplate(
            regex = Regex(
                """(?i)retiro\s+(?:por\s+)?(?:\$|b/\.?)\s*(?<amount>[\d.,]+)\s+(?:en\s+)?(?<merchant>.+?)(?:,?\s*con\s+la\s+tarjeta.+?|\.\s*saldo:?.*|\.\s*$|$)"""
            ),
            category = TransactionCategory.MERCHANT,
            amountSign = -1,
        ),
        // 6) Débito genérico con monto: "Débito cuenta número: ... Monto: $ 50.0"
        ParserTemplate(
            regex = Regex(
                """(?is)d[eé]bito\s+cuenta.*?monto:\s*(?:\$|b/\.?)\s*(?<amount>[\d.,]+)"""
            ),
            category = TransactionCategory.OTHER,
            amountSign = -1,
        ),
        // 7) Genérico con "B/. <monto>"
        ParserTemplate(
            regex = Regex("""(?i)b/\.?\s*(?<amount>[\d.,]+)"""),
            category = TransactionCategory.OTHER,
            amountSign = -1,
        ),
    )

    override fun parse(text: String, occurredAt: Long): ParsedTransaction? {
        val isNotPanama = text.contains("Bs.", ignoreCase = true) || text.contains("VES", ignoreCase = true)
        if (isNotPanama) return null

        val looksLikePanama = text.contains("B/.", ignoreCase = true) ||
            text.contains("mercantilbanco.com.pa", ignoreCase = true) ||
            text.contains("mercantil panam", ignoreCase = true) ||
            text.contains("zinli", ignoreCase = true) ||
            text.contains("MasterCard Débito", ignoreCase = true) ||
            text.contains("MasterCard Debito", ignoreCase = true) ||
            (text.contains("Mercantil", ignoreCase = true) && !text.contains("Bs.", ignoreCase = true))
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
