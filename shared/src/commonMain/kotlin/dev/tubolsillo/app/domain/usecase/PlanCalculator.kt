package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.db.entity.PlanItemEntity
import dev.tubolsillo.app.core.db.entity.PlanItemStateEntity
import dev.tubolsillo.app.core.db.entity.TransactionEntity
import dev.tubolsillo.app.core.money.Money
import dev.tubolsillo.app.core.time.MonthBuckets
import dev.tubolsillo.app.core.time.nowMillis
import dev.tubolsillo.app.domain.model.PlanKind
import dev.tubolsillo.app.domain.model.PlanLineView
import dev.tubolsillo.app.domain.model.PlanMonthData
import dev.tubolsillo.app.domain.model.PlanRecurrence
import dev.tubolsillo.app.domain.model.PlanStatus
import dev.tubolsillo.app.domain.model.PlanSuggestion
import dev.tubolsillo.app.domain.model.TransactionCategory
import kotlinx.datetime.TimeZone

/**
 * Resuelve el plan de un mes y lo enfrenta a lo real.
 *
 * Las ocurrencias NO viven en la base: se derivan de la regla, así que una
 * línea recurrente aparece en todos los meses de su rango sin materializarse.
 *
 * Lo real usa el mismo criterio que el presupuesto por categoría
 * (ver [BudgetCalculator]): las transferencias internas no son ingreso ni
 * gasto, y cada monto se convierte a USD con el equivalente congelado al día de
 * la transacción, o con la tasa vigente si no lo tiene.
 */
class PlanCalculator(
    private val totalsCalculator: TotalsCalculator,
) {

    fun monthData(
        month: String,
        items: List<PlanItemEntity>,
        states: List<PlanItemStateEntity>,
        transactions: List<TransactionEntity>,
        rates: Map<String, Double>,
        today: Long = nowMillis(),
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): PlanMonthData {
        val stateByItem = states
            .filter { it.month == month }
            .associateBy { it.itemId }
        val txById = transactions.associateBy { it.id }
        val startOfToday = MonthBuckets.startOfDay(today, zone)

        val active = items.filter { appliesIn(it, month, zone) }

        val lines = active
            .map { item ->
                val state = stateByItem[item.id]
                val expectedMinor = state?.amountMinor ?: item.amountMinor
                val status = PlanStatus.fromKey(state?.status)
                val occursAt = occurrenceAt(item, month, zone)
                val amountUsd = totalsCalculator.usdEquivalent(expectedMinor, item.currency, rates)

                // El real de la línea: manda la transacción enlazada; si está
                // pagada sin enlace, se toma su propio monto como real.
                val linked = state?.txId?.let { txById[it] }
                val settledMinor = linked?.let { kotlin.math.abs(it.amountMinor) }
                    ?: expectedMinor.takeIf { status == PlanStatus.PAID }
                val settledCurrency = linked?.currency
                    ?: item.currency.takeIf { status == PlanStatus.PAID }
                val settledUsd = linked?.let { kotlin.math.abs(usdOf(it, rates)) }
                    ?: amountUsd.takeIf { status == PlanStatus.PAID }

                PlanLineView(
                    itemId = item.id,
                    kind = PlanKind.fromKey(item.kind),
                    label = item.label,
                    categoryId = item.categoryId,
                    currencyCode = item.currency,
                    amountMinor = expectedMinor,
                    amountUsd = amountUsd,
                    occursAt = occursAt,
                    isRecurring = PlanRecurrence.fromKey(item.recurrence) == PlanRecurrence.MONTHLY,
                    status = status,
                    txId = state?.txId,
                    stateId = state?.id,
                    isOverdue = status == PlanStatus.PENDING && occursAt < startOfToday,
                    settledMinor = settledMinor,
                    settledCurrencyCode = settledCurrency,
                    settledUsd = settledUsd,
                )
            }
            .sortedBy { it.occursAt }

        val suggestions = suggestionsFor(lines, transactions, rates)

        var actualIncome = 0.0
        var actualExpense = 0.0
        for (tx in transactions) {
            if (tx.amountMinor == 0L) continue
            if (TransactionCategory.fromKey(tx.category) == TransactionCategory.TRANSFER) continue
            // La dirección la da el signo del monto nativo, que es el
            // autoritativo; el equivalente en USD solo aporta la magnitud.
            val usd = kotlin.math.abs(usdOf(tx, rates))
            if (tx.amountMinor > 0) actualIncome += usd else actualExpense += usd
        }

        // Una moneda sin tasa se convierte a 1.0: el total sería una mentira
        // silenciosa, así que la vista lo advierte.
        val ratesApproximate = active.any { it.currency !in rates } ||
            transactions.any { it.usdEquivalentMinor == null && it.currency !in rates }

        return PlanMonthData(
            month = month,
            income = lines.filter { it.kind == PlanKind.INCOME }
                .map { it.copy(suggestion = suggestions[it.itemId]) },
            expenses = lines.filter { it.kind == PlanKind.EXPENSE }
                .map { it.copy(suggestion = suggestions[it.itemId]) },
            actualIncomeUsd = actualIncome,
            actualExpenseUsd = actualExpense,
            ratesApproximate = ratesApproximate,
        )
    }

    /**
     * Empareja cada línea pendiente con la transacción real que mejor le
     * corresponde. La asignación es greedy por confianza descendente: dos
     * líneas nunca se quedan con la misma transacción, y la que tiene la
     * coincidencia más fuerte gana. Las transacciones ya enlazadas a otra línea
     * del mes quedan fuera del pool, así que aceptar una sugerencia no la
     * vuelve a ofrecer.
     */
    private fun suggestionsFor(
        lines: List<PlanLineView>,
        transactions: List<TransactionEntity>,
        rates: Map<String, Double>,
    ): Map<Long, PlanSuggestion> {
        val alreadyLinked = lines.mapNotNull { it.txId }.toSet()
        val pool = transactions.filter { tx ->
            tx.amountMinor != 0L &&
                tx.id !in alreadyLinked &&
                TransactionCategory.fromKey(tx.category) != TransactionCategory.TRANSFER
        }
        if (pool.isEmpty()) return emptyMap()

        // Candidatas por línea, ya filtradas por signo: un gasto no se paga con
        // una entrada, ni un sueldo se concilia con una salida.
        val proposals = lines
            .filter { it.isPending }
            .mapNotNull { line ->
                val wanted = if (line.kind == PlanKind.INCOME) 1 else -1
                val candidates = pool
                    .filter { if (wanted > 0) it.amountMinor > 0 else it.amountMinor < 0 }
                    .map { PlanMatcher.Candidate(it.id, kotlin.math.abs(usdOf(it, rates)), it.occurredAt) }
                PlanMatcher.suggest(line.amountUsd, line.occursAt, candidates)
                    ?.let { line.itemId to it }
            }
            .sortedByDescending { it.second.confidence }

        val takenTx = mutableSetOf<Long>()
        val result = mutableMapOf<Long, PlanSuggestion>()
        for ((itemId, suggestion) in proposals) {
            if (result.containsKey(itemId)) continue
            if (!takenTx.add(suggestion.txId)) continue
            result[itemId] = suggestion
        }
        return result
    }

    /** ¿La línea tiene una ocurrencia en [month]? */
    fun appliesIn(item: PlanItemEntity, month: String, zone: TimeZone): Boolean {
        if (!item.isActive) return false
        return when (PlanRecurrence.fromKey(item.recurrence)) {
            PlanRecurrence.ONCE ->
                item.dueAt?.let { MonthBuckets.monthKey(it, zone) == month } == true

            PlanRecurrence.MONTHLY ->
                (item.startMonth == null || month >= item.startMonth) &&
                    (item.endMonth == null || month <= item.endMonth)
        }
    }

    /** Fecha de vencimiento de la ocurrencia de [item] en [month]. */
    fun occurrenceAt(item: PlanItemEntity, month: String, zone: TimeZone): Long =
        when (PlanRecurrence.fromKey(item.recurrence)) {
            PlanRecurrence.ONCE -> item.dueAt ?: MonthBuckets.dateIn(
                month,
                item.dayOfMonth ?: 1,
                zone,
            )

            // El día se recorta al último del mes: el 31 cae el 30 en abril.
            PlanRecurrence.MONTHLY -> MonthBuckets.dateIn(month, item.dayOfMonth ?: 1, zone)
        }

    /** Equivalente USD: congelado al día de la transacción si existe, si no la tasa vigente. */
    private fun usdOf(tx: TransactionEntity, rates: Map<String, Double>): Double =
        tx.usdEquivalentMinor?.let { it / Money.pow10(2) }
            ?: totalsCalculator.usdEquivalent(tx.amountMinor, tx.currency, rates)
}
