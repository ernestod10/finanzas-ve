package dev.tubolsillo.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class BudgetLevelTest {

    private fun row(spent: Double, limit: Double?) = TagBudgetView(
        tagName = "Comida",
        limitUsd = limit,
        spentUsd = spent,
    )

    @Test
    fun `sin limite no hay semaforo`() {
        assertEquals(BudgetLevel.OK, row(spent = 500.0, limit = null).level)
        assertEquals(BudgetLevel.OK, row(spent = 500.0, limit = 0.0).level)
    }

    @Test
    fun `el 74 por ciento esta bien y el 75 avisa`() {
        assertEquals(BudgetLevel.OK, row(spent = 74.0, limit = 100.0).level)
        assertEquals(BudgetLevel.WARN, row(spent = 75.0, limit = 100.0).level)
    }

    @Test
    fun `pasarse del limite es OVER`() {
        assertEquals(BudgetLevel.OVER, row(spent = 100.01, limit = 100.0).level)
    }

    @Test
    fun `el total del mes usa la misma regla`() {
        val data = BudgetMonthData(
            month = "2026-11",
            rows = emptyList(),
            totalSpentUsd = 120.0,
            totalLimitUsd = 100.0,
        )
        assertEquals(BudgetLevel.OVER, data.totalLevel)
    }
}
