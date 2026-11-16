package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.db.entity.CategoryEntity
import dev.tubolsillo.app.core.db.entity.TransactionSplitEntity
import dev.tubolsillo.app.core.db.entity.TransactionEntity
import dev.tubolsillo.app.core.money.Money
import dev.tubolsillo.app.domain.model.TransactionCategory
import dev.tubolsillo.app.domain.model.UNTAGGED_BUCKET

/** Gasto de una categoría en el período, con su color para la dona. */
data class CategorySpend(
    val name: String,
    val amountUsd: Double,
    val colorHex: String?,
)

/**
 * Reparte el gasto de un período por categoría, en USD y de mayor a menor.
 * Mismo criterio de exclusión que el presupuesto: las transferencias internas
 * no son gasto y el monto usa el equivalente USD congelado cuando existe.
 */
object ExpenseBreakdown {

    fun compute(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        rates: Map<String, Double>,
        totalsCalculator: TotalsCalculator,
        splitsByTx: Map<Long, List<TransactionSplitEntity>> = emptyMap(),
    ): List<CategorySpend> {
        val byName = mutableMapOf<String, Double>()
        val colorByName = categories.associate { it.name to it.colorHex }
        val nameById = categories.associate { it.id to it.name }

        for (tx in transactions) {
            if (tx.amountMinor >= 0) continue
            if (TransactionCategory.fromKey(tx.category) == TransactionCategory.TRANSFER) continue
            val usd = tx.usdEquivalentMinor?.let { it / Money.pow10(2) }
                ?: totalsCalculator.usdEquivalent(tx.amountMinor, tx.currency, rates)
            val parts = splitsByTx[tx.id].orEmpty().filter { it.amountMinor > 0 }
            if (parts.isNotEmpty()) {
                val total = parts.sumOf { it.amountMinor }.toDouble().coerceAtLeast(1.0)
                parts.forEach { part ->
                    val name = part.categoryId?.let { nameById[it] } ?: UNTAGGED_BUCKET
                    byName[name] = (byName[name] ?: 0.0) + kotlin.math.abs(usd) * (part.amountMinor / total)
                }
            } else {
                val name = tx.categoryId?.let { nameById[it] } ?: UNTAGGED_BUCKET
                byName[name] = (byName[name] ?: 0.0) + kotlin.math.abs(usd)
            }
        }

        return byName
            .filterValues { it > 0.0 }
            .map { (name, amount) -> CategorySpend(name, amount, colorByName[name]) }
            .sortedByDescending { it.amountUsd }
    }
}
