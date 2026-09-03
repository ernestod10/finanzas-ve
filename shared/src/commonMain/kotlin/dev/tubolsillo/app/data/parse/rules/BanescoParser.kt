package dev.tubolsillo.app.data.parse.rules

import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.data.parse.InstitutionParser
import dev.tubolsillo.app.data.parse.ParserSupport
import dev.tubolsillo.app.data.parse.ParserTemplate
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.ParsedTransaction
import dev.tubolsillo.app.domain.model.TransactionCategory

/**
 * SMS de Banesco ("BanescoInforma: Compra por Bs. 25,00 en FARMACIA X. Saldo: ...").
 * v1 con los formatos típicos de compra TDC/débito y pago móvil; se afina con
 * el corpus real del usuario (pantalla Replay).
 */
class BanescoParser() : InstitutionParser {

    override val institution = Institution.BANESCO
    override val version = 1

    private val templates = listOf(
        // Compra débito: "Compra por Bs. 25,00 en FARMACIA X. Saldo: Bs. 1.234,56"
        ParserTemplate(
            regex = Regex(
                "(?i)compra\\s+por\\s+(?:bs\\.?|bsd)?\\s*(?<amount>[\\d.,]+)\\s+en\\s+(?<merchant>.+?)(?:\\.\\s*saldo:?.*|\\.\\s*$|$)"
            ),
            category = TransactionCategory.MERCHANT,
            amountSign = -1,
        ),
        // TDC: "Su tarjeta de credito terminacion 1234 ... por Bs. 25,00 en FARMACIA X"
        ParserTemplate(
            regex = Regex(
                "(?i)tarjeta\\s+de\\s+credito[^.]*?por\\s+(?:bs\\.?)?\\s*(?<amount>[\\d.,]+)\\s+en\\s+(?<merchant>.+?)(?:\\.|$)"
            ),
            category = TransactionCategory.MERCHANT,
            amountSign = -1,
        ),
        // Pago móvil recibido: "Pago Movil recibido por Bs. 100,00 de V12345678"
        ParserTemplate(
            regex = Regex(
                "(?i)pago\\s+movil\\s+recibido\\s+(?:por\\s+)?(?:bs\\.?)?\\s*(?<amount>[\\d.,]+)\\s+(?:de|desde)\\s+(?<merchant>[A-Za-z]?\\d+)"
            ),
            category = TransactionCategory.PM_RECEIVED,
            amountSign = +1,
        ),
        // Pago móvil enviado: "Pago Movil enviado por Bs. 25,00 a V12345678"
        ParserTemplate(
            regex = Regex(
                "(?i)pago\\s+movil\\s+(?:enviado|realizado)\\s+(?:por\\s+)?(?:bs\\.?)?\\s*(?<amount>[\\d.,]+)\\s+(?:a|al)\\s+(?<merchant>[A-Za-z]?\\d+)"
            ),
            category = TransactionCategory.PM_SENT,
            amountSign = -1,
        ),
        // Transferencia recibida: "Transferencia recibida por Bs. 1.000,00"
        ParserTemplate(
            regex = Regex(
                "(?i)transferencia\\s+recibida\\s+(?:por\\s+)?(?:bs\\.?)?\\s*(?<amount>[\\d.,]+)"
            ),
            category = TransactionCategory.OTHER,
            amountSign = +1,
        ),
        // Transferencia enviada: "Transferencia enviada por Bs. 1.000,00"
        ParserTemplate(
            regex = Regex(
                "(?i)transferencia\\s+(?:enviada|realizada)\\s+(?:por\\s+)?(?:bs\\.?)?\\s*(?<amount>[\\d.,]+)"
            ),
            category = TransactionCategory.OTHER,
            amountSign = -1,
        ),
    )

    override fun parse(text: String, occurredAt: Long): ParsedTransaction? {
        if (!text.contains("Banesco", ignoreCase = true) &&
            !text.contains("BanescoInforma", ignoreCase = true)
        ) return null

        for (template in templates) {
            val match = template.regex.find(text) ?: continue
            val amountMinor = AmountNormalizer.parseMinor(match.groups["amount"]!!.value, "VES")
                ?: continue
            val merchant = ParserSupport.groupOrNull(match, "merchant")?.let { ParserSupport.cleanMerchant(it) }
                ?: "Banesco"
            val signed = if (template.amountSign < 0) -kotlin.math.abs(amountMinor)
            else kotlin.math.abs(amountMinor)
            return ParsedTransaction(
                institution = institution,
                occurredAt = occurredAt,
                amountMinor = signed,
                currency = "VES",
                merchant = merchant,
                description = descriptionFor(template, merchant),
                category = template.category,
                balanceMinor = ParserSupport.extractBalance(text, "VES"),
                balanceCurrency = "VES",
                parserVersion = version,
                raw = text,
            )
        }
        return null
    }

    private fun descriptionFor(template: ParserTemplate, merchant: String): String =
        when (template.category) {
            TransactionCategory.PM_RECEIVED -> "Pago móvil recibido de $merchant"
            TransactionCategory.PM_SENT -> "Pago móvil enviado a $merchant"
            else -> merchant
        }
}
