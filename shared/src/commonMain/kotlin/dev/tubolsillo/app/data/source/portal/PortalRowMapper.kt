package dev.tubolsillo.app.data.source.portal

import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.core.text.MerchantNormalizer
import dev.tubolsillo.app.core.time.DateParser
import dev.tubolsillo.app.domain.model.ParsedTransaction
import dev.tubolsillo.app.domain.model.TransactionCategory
import kotlinx.datetime.TimeZone
import kotlin.math.abs

data class MappedAccountBalance(
    val kind: String,
    val currency: String,
    val amountMinor: Long,
    val mask: String = "",
    val label: String = "",
    /** Deuda impresa por el portal (positiva). */
    val debtMinor: Long? = null,
    /** Crédito disponible: lo que aún se puede usar de la tarjeta. */
    val availableMinor: Long? = null,
    /** Límite declarado o deducido de deuda + disponible. */
    val limitMinor: Long? = null,
    val minPaymentMinor: Long? = null,
    /** Día de pago (epoch ms, medianoche del día). */
    val dueAt: Long? = null,
    /** Nombre comercial impreso en la tarjeta ("Visa Avant Estand"). */
    val cardName: String = "",
)

/** Resultado de mapear un payload completo. */
data class PortalMapResult(
    val rows: List<ParsedTransaction> = emptyList(),
    val skipped: Int = 0,
    val balanceMinor: Long? = null,
    val balanceCurrency: String? = null,
    val accountBalances: List<MappedAccountBalance> = emptyList(),
)

/**
 * Convierte las filas crudas del portal en [ParsedTransaction]. Función pura:
 * sin DAOs, sin corrutinas, testeable con kotlin.test.
 *
 * El signo NUNCA se delega a [AmountNormalizer]: ese filtra los caracteres no
 * numéricos, así que "(1.234,56)" sale positivo y "1.234,56 D" pierde la marca.
 * Se toma el valor absoluto y se aplica la política de la receta.
 */
object PortalRowMapper {

    const val PARSER_VERSION = 1

    fun map(payload: PortalPayload, recipe: PortalRecipe, fallbackOccurredAt: Long): PortalMapResult {
        val mapped = mutableListOf<Pair<PortalRawRow, ParsedTransaction>>()
        var skipped = 0
        payload.rows.forEach { row ->
            val tx = mapRow(row, recipe, fallbackOccurredAt)
            if (tx == null) skipped++ else mapped.add(row to tx)
        }
        val parsed = applyBalanceSigns(mapped, recipe)
        val mappedBalances = dedupeCards(payload.accounts.mapNotNull { acc ->
            if (acc.kind == "CREDIT_CARD") mapCard(acc, recipe) else mapDeposit(acc, recipe)
        })
        val primaryBal = balanceMinorOf(payload, recipe)
            ?: mappedBalances.firstOrNull { it.kind != "CREDIT_CARD" && it.currency == recipe.currency }?.amountMinor
        return PortalMapResult(
            rows = parsed,
            skipped = skipped,
            balanceMinor = primaryBal,
            balanceCurrency = if (primaryBal != null) recipe.currency else null,
            accountBalances = mappedBalances,
        )
    }

    /** Cuenta de depósito (corriente/ahorro/divisas): un solo monto. */
    private fun mapDeposit(acc: PortalAccountBalance, recipe: PortalRecipe): MappedAccountBalance? {
        val curr = acc.currency.ifBlank { recipe.currency }
        val mag = AmountNormalizer.parseMinor(acc.amountText, curr)?.let { abs(it) } ?: return null
        val signed = if (acc.amountText.trim().startsWith("-")) -mag else mag
        return MappedAccountBalance(
            kind = acc.kind.ifBlank { "CHECKING" },
            currency = curr,
            amountMinor = signed,
            mask = acc.mask,
            label = acc.label,
        )
    }

    /**
     * Tarjeta de crédito: el texto trae varios datos (deuda, disponible,
     * mínimo, fecha) y aquí se separan. `amountMinor` sigue siendo el saldo
     * con signo (deuda negativa) para no romper a los consumidores de siempre.
     */
    private fun mapCard(acc: PortalAccountBalance, recipe: PortalRecipe): MappedAccountBalance? {
        val tdc = PortalTdcParser.parse(acc.rawText, recipe.currency)
        val currency = tdc.currency ?: acc.currency.ifBlank { recipe.currency }
        val fallback = AmountNormalizer.parseMinor(acc.amountText, currency)?.let { abs(it) }
        val debt = tdc.debtMinor ?: fallback.takeIf { acc.label == "TDC_DEBT" }
        val available = tdc.availableMinor ?: fallback.takeIf { acc.label == "TDC_AVAILABLE" }
        val magnitude = debt ?: available ?: return null
        return MappedAccountBalance(
            kind = "CREDIT_CARD",
            currency = currency,
            amountMinor = -magnitude,
            mask = tdc.mask.ifBlank { acc.mask },
            label = if (debt != null) "TDC_DEBT" else "TDC_AVAILABLE",
            debtMinor = debt,
            availableMinor = available,
            limitMinor = tdc.effectiveLimitMinor,
            // "Pago mínimo Bs. 0,00" es lo que imprime BNC cuando no hay nada
            // que pagar: no es un dato, es ruido.
            minPaymentMinor = tdc.minPaymentMinor?.takeIf { it > 0 },
            dueAt = tdc.dueAt,
            cardName = tdc.cardName,
        )
    }

    /**
     * La misma tarjeta puede aparecer en dos contenedores del DOM (el bloque y
     * su sección). Se une por máscara quedándose con el dato de cada uno.
     */
    private fun dedupeCards(list: List<MappedAccountBalance>): List<MappedAccountBalance> {
        val cards = mutableListOf<MappedAccountBalance>()
        val others = mutableListOf<MappedAccountBalance>()
        for (b in list) {
            if (b.kind != "CREDIT_CARD") {
                others.add(b)
                continue
            }
            val idx = cards.indexOfFirst { sameCard(it, b) }
            if (idx >= 0) cards[idx] = cards[idx].merge(b) else cards.add(b)
        }
        return (others + cards).map { b ->
            if (b.kind != "CREDIT_CARD") b
            else b.copy(amountMinor = -(b.debtMinor ?: b.availableMinor ?: abs(b.amountMinor)))
        }
    }

    /**
     * BNC publica la tarjeta dos veces: el bloque con la máscara ("***0008") y
     * un panel de detalle sin máscara que trae el mínimo y el vencimiento. Son
     * la misma tarjeta: se reconocen por moneda y, cuando falta la máscara, por
     * el monto.
     */
    private fun sameCard(a: MappedAccountBalance, b: MappedAccountBalance): Boolean {
        if (a.currency != b.currency) return false
        if (a.mask.isNotBlank() && b.mask.isNotBlank()) return a.mask == b.mask
        return a.amountMinor == b.amountMinor
    }

    private fun MappedAccountBalance.merge(other: MappedAccountBalance): MappedAccountBalance = copy(
        amountMinor = if (mask.isNotBlank()) amountMinor else other.amountMinor,
        mask = mask.ifBlank { other.mask },
        label = if (debtMinor != null) label else other.label,
        debtMinor = debtMinor ?: other.debtMinor,
        availableMinor = availableMinor ?: other.availableMinor,
        limitMinor = limitMinor ?: other.limitMinor,
        minPaymentMinor = minPaymentMinor ?: other.minPaymentMinor,
        dueAt = dueAt ?: other.dueAt,
        cardName = cardName.ifBlank { other.cardName },
    )

    fun mapRow(row: PortalRawRow, recipe: PortalRecipe, fallbackOccurredAt: Long): ParsedTransaction? {
        val zone = runCatching { TimeZone.of(recipe.timeZoneId) }.getOrElse { TimeZone.UTC }
        val occurredAt = DateParser.parse(row.date, recipe.dateFormat, zone) ?: return null
        val magnitude = amountMinorOf(row, recipe) ?: return null
        if (magnitude == 0L) return null
        val amountMinor = magnitude * signOf(row, recipe)
        val merchant = merchantOf(row)
        val description = descriptionOf(row)
        return ParsedTransaction(
            institution = recipe.institution,
            occurredAt = occurredAt,
            amountMinor = amountMinor,
            currency = recipe.currency,
            merchant = merchant,
            description = description,
            category = TransactionCategory.OTHER,
            balanceMinor = row.balance.takeIf { it.isNotBlank() }
                ?.let { AmountNormalizer.parseMinor(it, recipe.currency) }?.let { abs(it) },
            balanceCurrency = if (row.balance.isNotBlank()) recipe.currency else null,
            parserVersion = PARSER_VERSION,
            raw = row.raw.ifBlank { listOf(row.date, row.description, row.amount).joinToString(" ") },
        )
    }

    /** Magnitud del movimiento, siempre positiva. */
    fun amountMinorOf(row: PortalRawRow, recipe: PortalRecipe): Long? {
        val text = when {
            row.amount.isNotBlank() -> row.amount
            row.debit.isNotBlank() -> row.debit
            row.credit.isNotBlank() -> row.credit
            else -> return null
        }
        val minor = AmountNormalizer.parseMinor(text, recipe.currency) ?: return null
        return abs(minor)
    }

    /** +1 suma, -1 resta, según la política declarada en la receta. */
    fun signOf(row: PortalRawRow, recipe: PortalRecipe): Int = when (recipe.signPolicy) {
        SignPolicy.ALL_NEGATIVE -> -1

        SignPolicy.DEBIT_CREDIT_COLUMNS -> when {
            row.debit.isNotBlank() -> -1
            row.credit.isNotBlank() -> 1
            else -> hintSign(row, recipe)
        }

        SignPolicy.AMOUNT_SIGNED -> {
            val text = row.amount.ifBlank { row.debit.ifBlank { row.credit } }
            val negative = text.contains('-') || (text.contains('(') && text.contains(')')) ||
                text.trimEnd().endsWith("D", ignoreCase = true)
            if (negative) -1 else 1
        }

        SignPolicy.HINTS_FROM_DESCRIPTION -> hintSign(row, recipe)
    }

    /**
     * El saldo corrido es la fuente de verdad del signo. Si la fila trae saldo y
     * una vecina también, la diferencia TIENE que ser el monto: si subió, entró
     * plata; si bajó, salió. Corrige los portales que no separan Débito/Crédito
     * o que imprimen marcas ambiguas, sin depender de las cabeceras.
     *
     * Se prueban las dos vecinas porque la tabla puede venir del más nuevo al
     * más viejo o al revés.
     */
    private fun applyBalanceSigns(
        rows: List<Pair<PortalRawRow, ParsedTransaction>>,
        recipe: PortalRecipe,
    ): List<ParsedTransaction> {
        if (rows.isEmpty()) return emptyList()
        val balances = rows.map { (row, _) -> balanceOf(row, recipe) }
        return rows.mapIndexed { index, (_, tx) ->
            val amount = abs(tx.amountMinor)
            val own = balances[index]
            if (amount == 0L || own == null) return@mapIndexed tx

            val delta = listOf(index + 1, index - 1)
                .filter { it in balances.indices }
                .mapNotNull { neighbor ->
                    val other = balances[neighbor] ?: return@mapNotNull null
                    val diff = own - other
                    // Tolerancia de 1 unidad menor por redondeos del portal.
                    if (diff != 0L && abs(abs(diff) - amount) <= 1L) diff else null
                }
                .firstOrNull()

            if (delta == null) tx
            else tx.copy(amountMinor = if (delta > 0) amount else -amount)
        }
    }

    private fun balanceOf(row: PortalRawRow, recipe: PortalRecipe): Long? =
        row.balance.takeIf { it.isNotBlank() }
            ?.let { AmountNormalizer.parseMinor(it, recipe.currency) }

    private fun hintSign(row: PortalRawRow, recipe: PortalRecipe): Int {
        val compact = row.amount.replace(" ", "").trim()
        val upper = compact.uppercase()

        // Marcas contables: paréntesis, menos, o sufijo/prefijo D/C/DB/CR.
        if (compact.contains('-') || (compact.contains('(') && compact.contains(')'))) return -1
        if (upper.startsWith("+") || upper.endsWith("CR") || upper.startsWith("CR")) return 1
        if (upper.endsWith("DB") || upper.startsWith("DB")) return -1
        if (upper.endsWith("D") || upper.startsWith("D")) return -1
        if (upper.endsWith("C") || upper.startsWith("C")) return 1

        val haystack = MerchantNormalizer.normalize(
            listOf(row.description, row.raw).joinToString(" ")
        )

        // Raíces, no palabras exactas: "recibida" y "recibido" comparten "recib".
        // Positivas primero para que "PAGO MOVIL RECIBIDO" no caiga en "pago".
        if (POSITIVE_STEMS.any { haystack.contains(it) }) return 1
        if (NEGATIVE_STEMS.any { haystack.contains(it) }) return -1
        if (recipe.negativeHints.any { haystack.contains(MerchantNormalizer.normalize(it)) }) return -1
        if (recipe.positiveHints.any { haystack.contains(MerchantNormalizer.normalize(it)) }) return 1
        return -1
    }

    fun merchantOf(row: PortalRawRow): String {
        val base = row.description.ifBlank { row.raw }.trim()
        return base.take(80).ifBlank { "Movimiento" }
    }

    private fun descriptionOf(row: PortalRawRow): String {
        val base = merchantOf(row)
        return if (row.reference.isBlank()) base else base + " · Ref " + row.reference.trim()
    }

    /** Saldo del portal: autoritativo, se aplica una sola vez por consulta. */
    fun balanceMinorOf(payload: PortalPayload, recipe: PortalRecipe): Long? {
        val text = payload.balanceText?.takeIf { it.isNotBlank() } ?: return null
        return AmountNormalizer.parseMinor(text, recipe.currency)
    }

    /**
     * Raíces de palabras que indican plata que ENTRA. Van antes que las
     * negativas para que "pago móvil recibido" gane sobre "pago".
     */
    private val POSITIVE_STEMS = listOf(
        "abono", "abon", "deposit", "recibid", "nomina", "interes",
        "devoluc", "reembolso", "ingreso", "acredit",
    )

    /**
     * Raíces de plata que SALE. Ojo con "pos": aparece dentro de "deposito",
     * por eso se usa "punto de venta" y no la sigla.
     */
    private val NEGATIVE_STEMS = listOf(
        "compra", "cargo", "retiro", "comision", "consumo", "debito", "enviad",
        "pago", "punto de venta", "cuota", "servicio", "recibo de",
    )
}
