package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.db.entity.TransactionEntity
import dev.tubolsillo.app.domain.model.TransactionCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PeriodMetricsTest {

    private val calculator = PeriodMetricsCalculator(TotalsCalculator())
    private val rates = mapOf("USD" to 1.0, "VES" to 0.02) // 50 Bs/$

    private fun tx(
        id: Long,
        amountMinor: Long,
        currency: String = "USD",
        category: TransactionCategory = TransactionCategory.MERCHANT,
        usdEquivalentMinor: Long? = null,
    ) = TransactionEntity(
        id = id,
        accountId = 1,
        occurredAt = 1_000_000L,
        amountMinor = amountMinor,
        currency = currency,
        description = "test",
        category = category.key,
        status = "CONFIRMED",
        source = "MANUAL",
        usdEquivalentMinor = usdEquivalentMinor,
    )

    @Test
    fun `separa ingresos de gastos`() {
        val metrics = calculator.compute(
            listOf(tx(1, +500_00), tx(2, -60_00), tx(3, -15_50)),
            rates,
        )
        assertEquals(500.0, metrics.incomeUsd, 0.001)
        assertEquals(75.5, metrics.expenseUsd, 0.001)
        assertEquals(424.5, metrics.netUsd, 0.001)
        assertEquals(3, metrics.txCount)
    }

    @Test
    fun `las transferencias internas no cuentan`() {
        val metrics = calculator.compute(
            listOf(
                tx(1, -100_00, category = TransactionCategory.TRANSFER),
                tx(2, +100_00, category = TransactionCategory.TRANSFER),
            ),
            rates,
        )
        assertEquals(0.0, metrics.incomeUsd, 0.001)
        assertEquals(0.0, metrics.expenseUsd, 0.001)
        assertEquals(0, metrics.txCount)
    }

    @Test
    fun `los montos en bolivares se convierten con la tasa`() {
        val metrics = calculator.compute(listOf(tx(1, -1000_00, currency = "VES")), rates)
        assertEquals(20.0, metrics.expenseUsd, 0.001)
    }

    @Test
    fun `el usd congelado manda sobre la tasa vigente`() {
        val metrics = calculator.compute(
            listOf(tx(1, -1000_00, currency = "VES", usdEquivalentMinor = -25_00)),
            rates,
        )
        assertEquals(25.0, metrics.expenseUsd, 0.001)
    }

    @Test
    fun `la direccion sale del monto nativo, no del convertido`() {
        // Equivalente USD con signo invertido a propósito: manda amountMinor.
        val metrics = calculator.compute(
            listOf(tx(1, -50_00, usdEquivalentMinor = 50_00)),
            rates,
        )
        assertEquals(50.0, metrics.expenseUsd, 0.001)
        assertEquals(0.0, metrics.incomeUsd, 0.001)
    }

    @Test
    fun `la variacion es nula cuando el periodo anterior es cero`() {
        assertNull(calculator.changePct(current = 100.0, previous = 0.0))
    }

    @Test
    fun `la variacion se calcula contra el valor absoluto anterior`() {
        assertEquals(50.0, calculator.changePct(150.0, 100.0)!!, 0.001)
        assertEquals(-50.0, calculator.changePct(50.0, 100.0)!!, 0.001)
    }
}
