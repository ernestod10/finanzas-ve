package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.db.entity.PlanItemEntity
import dev.tubolsillo.app.core.db.entity.PlanItemStateEntity
import dev.tubolsillo.app.core.db.entity.TransactionEntity
import dev.tubolsillo.app.core.time.MonthBuckets
import dev.tubolsillo.app.domain.model.PlanKind
import dev.tubolsillo.app.domain.model.PlanRecurrence
import dev.tubolsillo.app.domain.model.PlanStatus
import dev.tubolsillo.app.domain.model.TransactionCategory
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanCalculatorTest {

    private val calculator = PlanCalculator(TotalsCalculator())
    private val utc = TimeZone.UTC
    private val rates = mapOf("USD" to 1.0, "VES" to 0.02) // 50 Bs/$

    private fun item(
        id: Long,
        kind: PlanKind = PlanKind.EXPENSE,
        label: String = "Seguro",
        amountMinor: Long = 60_00,
        currency: String = "USD",
        recurrence: PlanRecurrence = PlanRecurrence.MONTHLY,
        dueAt: Long? = null,
        dayOfMonth: Int? = 10,
        startMonth: String? = "2026-09",
        endMonth: String? = null,
        categoryId: Long? = null,
        isActive: Boolean = true,
    ) = PlanItemEntity(
        id = id,
        kind = kind.key,
        label = label,
        amountMinor = amountMinor,
        currency = currency,
        recurrence = recurrence.key,
        dueAt = dueAt,
        dayOfMonth = dayOfMonth,
        startMonth = startMonth,
        endMonth = endMonth,
        categoryId = categoryId,
        isActive = isActive,
    )

    private fun tx(
        id: Long,
        amountMinor: Long,
        occurredAt: Long = MonthBuckets.dateIn("2026-09", 12, utc),
        currency: String = "USD",
        category: TransactionCategory = TransactionCategory.MERCHANT,
        usdEquivalentMinor: Long? = null,
    ) = TransactionEntity(
        id = id,
        accountId = 1,
        occurredAt = occurredAt,
        amountMinor = amountMinor,
        currency = currency,
        description = "test",
        category = category.key,
        status = "CONFIRMED",
        source = "MANUAL",
        usdEquivalentMinor = usdEquivalentMinor,
    )

    private fun state(
        itemId: Long,
        month: String = "2026-09",
        status: PlanStatus = PlanStatus.PENDING,
        txId: Long? = null,
        amountMinor: Long? = null,
    ) = PlanItemStateEntity(
        itemId = itemId,
        month = month,
        status = status.key,
        txId = txId,
        amountMinor = amountMinor,
    )

    /** Día 20 de septiembre: cualquier vencimiento anterior ya pasó. */
    private val hoy = MonthBuckets.dateIn("2026-09", 20, utc)

    private fun month(
        month: String = "2026-09",
        items: List<PlanItemEntity>,
        states: List<PlanItemStateEntity> = emptyList(),
        txs: List<TransactionEntity> = emptyList(),
    ) = calculator.monthData(month, items, states, txs, rates, today = hoy, zone = utc)

    // --- Recurrencia -------------------------------------------------------

    @Test
    fun `linea mensual aparece en su mes de inicio y en los siguientes`() {
        val items = listOf(item(1, startMonth = "2026-09"))
        assertEquals(1, month("2026-09", items).expenses.size)
        assertEquals(1, month("2026-10", items).expenses.size)
        assertEquals(1, month("2027-03", items).expenses.size)
    }

    @Test
    fun `linea mensual no aparece antes de su mes de inicio`() {
        val items = listOf(item(1, startMonth = "2026-09"))
        assertTrue(month("2026-08", items).isEmpty)
    }

    @Test
    fun `linea mensual no aparece despues de su mes final`() {
        val items = listOf(item(1, startMonth = "2026-09", endMonth = "2026-10"))
        assertEquals(1, month("2026-10", items).expenses.size)
        assertTrue(month("2026-11", items).isEmpty)
    }

    @Test
    fun `linea puntual solo aparece en su mes`() {
        val matricula = item(
            id = 1,
            label = "Matrícula",
            recurrence = PlanRecurrence.ONCE,
            dueAt = MonthBuckets.dateIn("2026-09", 10, utc),
            dayOfMonth = null,
            startMonth = null,
        )
        assertEquals(1, month("2026-09", listOf(matricula)).expenses.size)
        assertTrue(month("2026-10", listOf(matricula)).isEmpty)
    }

    @Test
    fun `una linea inactiva no aparece`() {
        assertTrue(month("2026-09", listOf(item(1, isActive = false))).isEmpty)
    }

    @Test
    fun `un recurrente con dia 31 cae el 30 en abril`() {
        val items = listOf(item(1, dayOfMonth = 31, startMonth = "2026-04"))
        val abril = month("2026-04", items)
        assertEquals(
            MonthBuckets.dateIn("2026-04", 30, utc),
            abril.expenses.single().occursAt,
        )
    }

    // --- Estimado y real ---------------------------------------------------

    @Test
    fun `el estimado separa ingresos de gastos`() {
        val items = listOf(
            item(1, kind = PlanKind.INCOME, label = "Sueldo", amountMinor = 500_00),
            item(2, kind = PlanKind.EXPENSE, label = "Seguro", amountMinor = 60_00),
        )
        val data = month(items = items)
        assertEquals(500.0, data.estimatedIncomeUsd, 0.001)
        assertEquals(60.0, data.estimatedExpenseUsd, 0.001)
        assertEquals(440.0, data.estimatedBalanceUsd(), 0.001)
    }

    @Test
    fun `el real suma las transacciones del mes`() {
        val txs = listOf(
            tx(1, +500_00),
            tx(2, -60_00),
            tx(3, -15_50),
        )
        val data = month(items = listOf(item(1)), txs = txs)
        assertEquals(500.0, data.actualIncomeUsd, 0.001)
        assertEquals(75.5, data.actualExpenseUsd, 0.001)
    }

    @Test
    fun `las transferencias internas no son ingreso ni gasto`() {
        val txs = listOf(
            tx(1, -100_00, category = TransactionCategory.TRANSFER),
            tx(2, +100_00, category = TransactionCategory.TRANSFER),
        )
        val data = month(items = listOf(item(1)), txs = txs)
        assertEquals(0.0, data.actualIncomeUsd, 0.001)
        assertEquals(0.0, data.actualExpenseUsd, 0.001)
    }

    @Test
    fun `un gasto en bolivares se convierte con la tasa vigente`() {
        val txs = listOf(tx(1, -1000_00, currency = "VES"))
        val data = month(items = listOf(item(1)), txs = txs)
        assertEquals(20.0, data.actualExpenseUsd, 0.001)
    }

    @Test
    fun `el usd congelado de la transaccion manda sobre la tasa vigente`() {
        // Bs 1000 darían $20 con la tasa de hoy, pero quedó congelado en $25.
        val txs = listOf(tx(1, -1000_00, currency = "VES", usdEquivalentMinor = -25_00))
        val data = month(items = listOf(item(1)), txs = txs)
        assertEquals(25.0, data.actualExpenseUsd, 0.001)
    }

    @Test
    fun `una moneda sin tasa marca el total como aproximado`() {
        val items = listOf(item(1, currency = "BTC"))
        assertTrue(month(items = items).ratesApproximate)
    }

    @Test
    fun `con todas las tasas el total no es aproximado`() {
        assertTrue(!month(items = listOf(item(1))).ratesApproximate)
    }

    // --- Estado del mes ----------------------------------------------------

    @Test
    fun `el monto del mes sobreescribe el estimado de la regla`() {
        val items = listOf(item(1, amountMinor = 60_00))
        val states = listOf(state(1, amountMinor = 72_00))
        assertEquals(72.0, month(items = items, states = states).estimatedExpenseUsd, 0.001)
        // La regla no se toca: el mes siguiente vuelve al estimado original.
        assertEquals(60.0, month("2026-10", items, states).estimatedExpenseUsd, 0.001)
    }

    @Test
    fun `una linea pendiente con fecha pasada esta vencida`() {
        val data = month(items = listOf(item(1, dayOfMonth = 10)))
        assertTrue(data.expenses.single().isOverdue)
        assertEquals(1, data.overdueCount)
    }

    @Test
    fun `una linea con fecha futura no esta vencida`() {
        val data = month(items = listOf(item(1, dayOfMonth = 28)))
        assertTrue(!data.expenses.single().isOverdue)
    }

    @Test
    fun `una linea pagada no esta vencida`() {
        val items = listOf(item(1, dayOfMonth = 10))
        val states = listOf(state(1, status = PlanStatus.PAID))
        val line = month(items = items, states = states).expenses.single()
        assertTrue(!line.isOverdue)
        assertTrue(line.isPaid)
    }

    @Test
    fun `una linea pagada sin enlace usa su monto como real`() {
        val items = listOf(item(1, amountMinor = 60_00))
        val states = listOf(state(1, status = PlanStatus.PAID))
        val line = month(items = items, states = states).expenses.single()
        assertEquals(60.0, line.settledUsd!!, 0.001)
        assertEquals(0.0, line.varianceUsd!!, 0.001)
        assertTrue(!line.isSettledWithVariance)
    }

    @Test
    fun `pendiente de pago y pendiente de cobro se suman aparte`() {
        val items = listOf(
            item(1, kind = PlanKind.INCOME, amountMinor = 500_00),
            item(2, kind = PlanKind.EXPENSE, amountMinor = 60_00),
            item(3, kind = PlanKind.EXPENSE, amountMinor = 40_00),
        )
        val states = listOf(state(2, status = PlanStatus.PAID))
        val data = month(items = items, states = states)
        assertEquals(500.0, data.pendingIncomeUsd, 0.001)
        assertEquals(40.0, data.pendingExpenseUsd, 0.001)
        assertEquals(1, data.settledCount)
        assertEquals(3, data.totalCount)
    }

    @Test
    fun `un mes sin lineas ni transacciones da cero`() {
        val data = month(items = emptyList())
        assertTrue(data.isEmpty)
        assertEquals(0.0, data.actualIncomeUsd, 0.001)
        assertEquals(0.0, data.actualExpenseUsd, 0.001)
    }

    // --- Conciliación ------------------------------------------------------

    @Test
    fun `sugiere el movimiento que coincide en monto y fecha`() {
        val items = listOf(item(1, amountMinor = 60_00, dayOfMonth = 10))
        val txs = listOf(tx(9, -58_00, occurredAt = MonthBuckets.dateIn("2026-09", 11, utc)))
        val line = month(items = items, txs = txs).expenses.single()
        assertNotNull(line.suggestion)
        assertEquals(9, line.suggestion!!.txId)
    }

    @Test
    fun `una linea de gasto no sugiere un movimiento positivo`() {
        val items = listOf(item(1, amountMinor = 60_00, dayOfMonth = 10))
        val txs = listOf(tx(9, +60_00, occurredAt = MonthBuckets.dateIn("2026-09", 10, utc)))
        assertNull(month(items = items, txs = txs).expenses.single().suggestion)
    }

    @Test
    fun `un ingreso no sugiere un movimiento negativo`() {
        val items = listOf(item(1, kind = PlanKind.INCOME, amountMinor = 500_00, dayOfMonth = 10))
        val txs = listOf(tx(9, -500_00, occurredAt = MonthBuckets.dateIn("2026-09", 10, utc)))
        assertNull(month(items = items, txs = txs).income.single().suggestion)
    }

    @Test
    fun `dos lineas no se quedan con la misma transaccion`() {
        val items = listOf(
            item(1, label = "Seguro", amountMinor = 60_00, dayOfMonth = 10),
            item(2, label = "Internet", amountMinor = 60_00, dayOfMonth = 10),
        )
        val txs = listOf(tx(9, -60_00, occurredAt = MonthBuckets.dateIn("2026-09", 10, utc)))
        val lines = month(items = items, txs = txs).expenses
        assertEquals(1, lines.count { it.suggestion != null })
    }

    @Test
    fun `una transaccion ya enlazada no se vuelve a sugerir`() {
        val items = listOf(
            item(1, label = "Seguro", amountMinor = 60_00, dayOfMonth = 10),
            item(2, label = "Internet", amountMinor = 60_00, dayOfMonth = 10),
        )
        val states = listOf(state(1, status = PlanStatus.PAID, txId = 9))
        val txs = listOf(tx(9, -60_00, occurredAt = MonthBuckets.dateIn("2026-09", 10, utc)))
        val lines = month(items = items, states = states, txs = txs).expenses
        assertNull(lines.first { it.itemId == 2L }.suggestion)
    }

    @Test
    fun `conciliada con diferencia reporta la variacion`() {
        val items = listOf(item(1, amountMinor = 60_00, dayOfMonth = 10))
        val states = listOf(state(1, status = PlanStatus.PAID, txId = 9))
        val txs = listOf(tx(9, -58_00, occurredAt = MonthBuckets.dateIn("2026-09", 11, utc)))
        val line = month(items = items, states = states, txs = txs).expenses.single()
        assertEquals(58.0, line.settledUsd!!, 0.001)
        assertEquals(-2.0, line.varianceUsd!!, 0.001)
        assertTrue(line.isSettledWithVariance)
    }

    @Test
    fun `una linea ya pagada no vuelve a sugerir`() {
        val items = listOf(item(1, amountMinor = 60_00, dayOfMonth = 10))
        val states = listOf(state(1, status = PlanStatus.PAID))
        val txs = listOf(tx(9, -60_00, occurredAt = MonthBuckets.dateIn("2026-09", 10, utc)))
        assertNull(month(items = items, states = states, txs = txs).expenses.single().suggestion)
    }
}
