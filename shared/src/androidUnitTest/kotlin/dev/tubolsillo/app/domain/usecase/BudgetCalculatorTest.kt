package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.db.dao.TransactionWithTags
import dev.tubolsillo.app.core.db.entity.BudgetEntity
import dev.tubolsillo.app.core.db.entity.TagEntity
import dev.tubolsillo.app.core.db.entity.TransactionEntity
import dev.tubolsillo.app.domain.model.TransactionCategory
import dev.tubolsillo.app.domain.model.UNTAGGED_BUCKET
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class BudgetCalculatorTest {

    private val calculator = BudgetCalculator(TotalsCalculator())

    private val rates = mapOf("USD" to 1.0, "VES" to 0.02) // 50 Bs/$

    private fun tx(
        id: Long,
        amountMinor: Long,
        currency: String = "USD",
        category: TransactionCategory = TransactionCategory.MERCHANT,
        tags: List<String> = emptyList(),
    ) = TransactionWithTags(
        transaction = TransactionEntity(
            id = id,
            accountId = 1,
            occurredAt = System.currentTimeMillis(),
            amountMinor = amountMinor,
            currency = currency,
            description = "test",
            category = category.key,
            status = "CONFIRMED",
            source = "MANUAL",
        ),
        tags = tags.mapIndexed { i, name -> TagEntity(id = (i + 1).toLong(), name = name) },
    )

    @Test
    fun `agrupa gastos por etiqueta y enfrenta presupuesto`() {
        val txs = listOf(
            tx(1, -25_00, tags = listOf("Comida")),
            tx(2, -10_00, tags = listOf("Comida")),
            tx(3, -5_00, tags = listOf("Transporte")),
            tx(4, -7_00), // sin etiqueta
        )
        val budgets = listOf(
            BudgetEntity(month = "2026-09", tagName = "Comida", limitMinor = 50_00, currency = "USD"),
        )
        val data = calculator.monthData("2026-09", txs, budgets, rates)
        val comida = data.rows.first { it.tagName == "Comida" }
        assertEquals(35.0, comida.spentUsd, 0.001)
        assertEquals(50.0, comida.limitUsd!!, 0.001)
        assertEquals(15.0, comida.remainingUsd!!, 0.001)
        val sinEtiqueta = data.rows.first { it.tagName == UNTAGGED_BUCKET }
        assertEquals(7.0, sinEtiqueta.spentUsd, 0.001)
        assertEquals(47.0, data.totalSpentUsd, 0.001)
    }

    @Test
    fun `transferencias internas no son gasto`() {
        val txs = listOf(
            tx(1, -100_00, category = TransactionCategory.TRANSFER),
            tx(2, +100_00, category = TransactionCategory.TRANSFER),
        )
        val data = calculator.monthData("2026-09", txs, emptyList(), rates)
        assertEquals(0.0, data.totalSpentUsd, 0.001)
    }

    @Test
    fun `ingresos no son gasto`() {
        val txs = listOf(tx(1, +50_00))
        val data = calculator.monthData("2026-09", txs, emptyList(), rates)
        assertEquals(0.0, data.totalSpentUsd, 0.001)
    }

    @Test
    fun `multi-moneda convierte a USD`() {
        val txs = listOf(
            tx(1, -25_00, currency = "USD", tags = listOf("Comida")),
            tx(2, -1000_00, currency = "VES", tags = listOf("Comida")), // Bs 1000 = $20
        )
        val data = calculator.monthData("2026-09", txs, emptyList(), rates)
        val comida = data.rows.first { it.tagName == "Comida" }
        assertEquals(45.0, comida.spentUsd, 0.001)
    }

    @Test
    fun `total limite nulo si falta algun presupuesto`() {
        val txs = listOf(tx(1, -25_00, tags = listOf("Comida")))
        val data = calculator.monthData("2026-09", txs, emptyList(), rates)
        assertNull(data.totalLimitUsd)
    }

    @Test
    fun `sobrepresupuesto detectado`() {
        val txs = listOf(tx(1, -60_00, tags = listOf("Comida")))
        val budgets = listOf(
            BudgetEntity(month = "2026-09", tagName = "Comida", limitMinor = 50_00, currency = "USD"),
        )
        val data = calculator.monthData("2026-09", txs, budgets, rates)
        val comida = data.rows.first { it.tagName == "Comida" }
        assertEquals(true, comida.isOverBudget)
        assertEquals(-10.0, comida.remainingUsd!!, 0.001)
    }
}
