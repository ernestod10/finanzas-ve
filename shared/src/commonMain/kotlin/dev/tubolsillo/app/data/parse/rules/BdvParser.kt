package dev.tubolsillo.app.data.parse.rules

import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.data.parse.InstitutionParser
import dev.tubolsillo.app.data.parse.ParserSupport
import dev.tubolsillo.app.data.parse.ParserTemplate
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.ParsedTransaction
import dev.tubolsillo.app.domain.model.TransactionCategory

/**
 * Push de la app BDV (com.bancodevenezuela.bdvdigital) — v2 afinado con corpus
 * real (sep-2026): PagomovilBDV, transferencias BDV/otros bancos, POS sin
 * contacto ("disp. X" = saldo disponible) y operación en línea con tarjeta.
 * Patrones en raw strings (""") — un solo backslash.
 */
class BdvParser() : InstitutionParser {

    override val institution = Institution.BDV
    override val version = 2

    private val templates = listOf(
        // PagoMóvil BDV recibido: "Recibiste un PagomovilBDV por Bs.3.800,00 del 0424-2841075 Ref:..."
        ParserTemplate(
            Regex(
                """(?i)recibiste\s+un\s+pagomovilbdv\s+por\s+(?:bs\.?)?\s*(?<amount>[\d.,]+)\s+del\s+(?<merchant>\d{4}-?\d{3,8})"""
            ),
            TransactionCategory.PM_RECEIVED,
            +1,
        ),
        // Transferencia (BDV u otros bancos) recibida
        ParserTemplate(
            Regex(
                """(?i)recibiste\s+una\s+transferencia(?:\s+de\s+otros\s+bancos)?\s+(?:bdv\s+)?de\s+(?<merchant>.+?)\s+por\s+(?:bs\.?)?\s*(?<amount>[\d.,]+)\s+bajo\s+el\s+n"""
            ),
            TransactionCategory.OTHER,
            +1,
        ),
        // POS / Biopago sin contacto: "punto de venta por Bs. X, bajo el #N, disp. Y"
        ParserTemplate(
            Regex(
                """(?i)pago\s+sin\s+contacto\s+realizado\s+en\s+punto\s+de\s+venta\s+por\s+(?:bs\.?)?\s*(?<amount>[\d.,]+)"""
            ),
            TransactionCategory.MERCHANT,
            -1,
        ),
        // Operación en línea con tarjeta (débito o crédito)
        ParserTemplate(
            Regex(
                """(?i)operacion\s+en\s+linea\s+con\s+tu\s+tarjeta\s+(?<merchant>.+?)\s+por\s+(?:bs\.?)?\s*(?<amount>[\d.,]+)"""
            ),
            TransactionCategory.MERCHANT,
            -1,
        ),
        // Respaldo genérico de compra
        ParserTemplate(
            Regex(
                """(?i)compra\s+por\s+(?:bs\.?|bsd)?\s*(?<amount>[\d.,]+)\s+en\s+(?<merchant>.+?)(?:\.\s*(?:tu\s+)?saldo.*|\.\s*$|$)"""
            ),
            TransactionCategory.MERCHANT,
            -1,
        ),
    )

    override fun parse(text: String, occurredAt: Long): ParsedTransaction? {
        // El hint de institución ya viene del package; algunos push BDV no mencionan "BDV".
        for (template in templates) {
            val match = template.regex.find(text) ?: continue
            val amountMinor = AmountNormalizer.parseMinor(match.groups["amount"]!!.value, "VES")
                ?: continue
            val merchant = ParserSupport.groupOrNull(match, "merchant")
                ?.let { ParserSupport.cleanMerchant(it) }
                ?: defaultMerchant(template.category)
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
                balanceMinor = ParserSupport.extractBalance(text, "VES") ?: extractDisponible(text),
                balanceCurrency = "VES",
                parserVersion = version,
                raw = text,
            )
        }
        return null
    }

    /** "disp. 18.045,80" = saldo disponible tras la operación (formato BDV). */
    private fun extractDisponible(text: String): Long? {
        val m = Regex("""(?i)disp\.?\s*(?:bs\.?)?\s*(?<bal>[\d.,]+)""").find(text) ?: return null
        return AmountNormalizer.parseMinor(m.groups["bal"]!!.value, "VES")
    }

    private fun defaultMerchant(category: TransactionCategory): String = when (category) {
        TransactionCategory.PM_RECEIVED -> "PagoMóvil BDV"
        TransactionCategory.MERCHANT -> "Punto de venta"
        else -> "Transferencia BDV"
    }

    private fun descriptionFor(template: ParserTemplate, merchant: String): String =
        when (template.category) {
            TransactionCategory.PM_RECEIVED -> "PagoMóvil BDV recibido de $merchant"
            TransactionCategory.OTHER -> "Transferencia recibida de $merchant"
            TransactionCategory.MERCHANT -> when {
                template.regex.pattern.contains("pago sin contacto") -> "Pago sin contacto · $merchant"
                template.regex.pattern.contains("operacion en linea") -> "Pago en línea · $merchant"
                else -> merchant
            }
            else -> merchant
        }
}
