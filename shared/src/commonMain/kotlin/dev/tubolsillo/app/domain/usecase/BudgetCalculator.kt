package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.db.dao.TransactionWithTags
import dev.tubolsillo.app.core.db.entity.BudgetEntity
import dev.tubolsillo.app.core.db.entity.CategoryEntity
import dev.tubolsillo.app.core.money.Currency
import dev.tubolsillo.app.core.money.Money
import dev.tubolsillo.app.domain.model.BudgetMonthData
import dev.tubolsillo.app.domain.model.TagBudgetView
import dev.tubolsillo.app.domain.model.TransactionCategory
import dev.tubolsillo.app.domain.model.UNTAGGED_BUCKET

/**
 * Agrupa los gastos del mes por etiqueta y los enfrenta a los presupuestos.
 * Gasto = transacción negativa que no es TRANSFER (las transferencias internas
 * no son gasto). Los montos se convierten a USD con las tasas vigentes porque
 * las cuentas mezclan USD/VES/crypto y sumar en moneda nativa sería un error.
 */
class BudgetCalculator(
    private val totalsCalculator: TotalsCalculator,
) {

    fun monthData(
        month: String,
        transactions: List<TransactionWithTags>,
        budgets: List<BudgetEntity>,
        rates: Map<String, Double>,
        categoryById: Map<Long, CategoryEntity>,
    ): BudgetMonthData {
        val spentByCat = mutableMapOf<String, Double>()
        for (txWithTags in transactions) {
            val tx = txWithTags.transaction
            if (tx.amountMinor >= 0) continue
            if (TransactionCategory.fromKey(tx.category) == TransactionCategory.TRANSFER) continue
            // Usa el equivalente en USD congelado al día de la transacción;
            // si no existe (transacciones viejas), calcula con la tasa vigente.
            val usd = tx.usdEquivalentMinor?.let { it / dev.tubolsillo.app.core.money.Money.pow10(2) }
                ?: totalsCalculator.usdEquivalent(tx.amountMinor, tx.currency, rates)
            val spentUsd = -usd
            val catName = tx.categoryId?.let { categoryById[it]?.name } ?: UNTAGGED_BUCKET
            spentByCat[catName] = (spentByCat[catName] ?: 0.0) + spentUsd
        }

        val limitByCat = budgets.associate { budget ->
            budget.tagName to budget.limitMinor / Money.pow10(Currency.of(budget.currency).decimals) *
                (rates[budget.currency] ?: 1.0)
        }

        val definedNames = categoryById.values
            .sortedWith(compareBy<CategoryEntity> { it.sortOrder }.thenBy { it.name })
            .map { it.name }
        val extraNames = ((spentByCat.keys + limitByCat.keys) - definedNames.toSet())
            .sortedWith(compareBy<String> { it == UNTAGGED_BUCKET }.thenBy { it })
        val catNames = definedNames + extraNames

        val rows = catNames.map { name ->
            TagBudgetView(
                tagName = name,
                limitUsd = limitByCat[name],
                spentUsd = spentByCat[name] ?: 0.0,
                colorHex = categoryById.values.firstOrNull { it.name == name }?.colorHex,
            )
        }
        val totalSpent = rows.sumOf { it.spentUsd }
        val totalLimit = if (limitByCat.isEmpty()) null else limitByCat.values.sum()
        return BudgetMonthData(month = month, rows = rows, totalSpentUsd = totalSpent, totalLimitUsd = totalLimit)
    }
}
