package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.db.entity.TransactionEntity
import dev.tubolsillo.app.core.money.Money
import dev.tubolsillo.app.domain.model.TransactionCategory

/** Ingresos, gastos y movimientos de un período, todo en USD. */
data class PeriodMetrics(
    val incomeUsd: Double,
    val expenseUsd: Double,
    val txCount: Int,
) {
    val netUsd: Double get() = incomeUsd - expenseUsd
}

/**
 * Métricas de un período a partir de sus transacciones.
 *
 * Mismo criterio que [BudgetCalculator] y [PlanCalculator]: las
 * transferencias internas no son ingreso ni gasto, cada monto usa su
 * equivalente USD congelado y solo si no existe se convierte con la tasa
 * vigente, y la dirección la da el signo del monto nativo (no el convertido).
 */
class PeriodMetricsCalculator(
    private val totalsCalculator: TotalsCalculator,
) {

    fun compute(transactions: List<TransactionEntity>, rates: Map<String, Double>): PeriodMetrics {
        var income = 0.0
        var expense = 0.0
        var count = 0
        for (tx in transactions) {
            if (tx.amountMinor == 0L) continue
            if (TransactionCategory.fromKey(tx.category) == TransactionCategory.TRANSFER) continue
            val usd = kotlin.math.abs(usdOf(tx, rates))
            if (tx.amountMinor > 0) income += usd else expense += usd
            count++
        }
        return PeriodMetrics(incomeUsd = income, expenseUsd = expense, txCount = count)
    }

    /**
     * Variación relativa (en %) frente al período anterior. Null cuando el
     * período anterior es cero: un "+∞ %" no informa nada.
     */
    fun changePct(current: Double, previous: Double): Double? =
        if (previous == 0.0) null else (current - previous) / kotlin.math.abs(previous) * 100.0

    private fun usdOf(tx: TransactionEntity, rates: Map<String, Double>): Double =
        tx.usdEquivalentMinor?.let { it / Money.pow10(2) }
            ?: totalsCalculator.usdEquivalent(tx.amountMinor, tx.currency, rates)
}
