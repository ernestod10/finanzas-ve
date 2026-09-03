package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.db.dao.TransactionWithTags
import dev.tubolsillo.app.core.db.entity.BudgetEntity
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
    ): BudgetMonthData {
        val spentByTag = mutableMapOf<String, Double>()
        for (txWithTags in transactions) {
            val tx = txWithTags.transaction
            if (tx.amountMinor >= 0) continue
            if (TransactionCategory.fromKey(tx.category) == TransactionCategory.TRANSFER) continue
            val usd = totalsCalculator.usdEquivalent(tx.amountMinor, tx.currency, rates)
            val spentUsd = -usd
            val tag = txWithTags.tags.firstOrNull()?.name ?: UNTAGGED_BUCKET
            spentByTag[tag] = (spentByTag[tag] ?: 0.0) + spentUsd
        }

        val limitByTag = budgets.associate { budget ->
            budget.tagName to budget.limitMinor / Money.pow10(Currency.of(budget.currency).decimals) *
                (rates[budget.currency] ?: 1.0)
        }

        val tagNames = (spentByTag.keys + limitByTag.keys).sorted()
        val rows = tagNames.map { name ->
            TagBudgetView(
                tagName = name,
                limitUsd = limitByTag[name],
                spentUsd = spentByTag[name] ?: 0.0,
            )
        }
        val totalSpent = rows.sumOf { it.spentUsd }
        val totalLimit = if (rows.any { it.limitUsd == null }) null else rows.sumOf { it.limitUsd!! }
        return BudgetMonthData(month = month, rows = rows, totalSpentUsd = totalSpent, totalLimitUsd = totalLimit)
    }
}
