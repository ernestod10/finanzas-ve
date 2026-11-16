package dev.tubolsillo.app.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PayoffPlannerTest {

    private fun item(
        id: Long,
        name: String,
        debt: Long,
        currency: String = "VES",
        due: Long? = null,
    ) = CreditItem(
        accountId = id,
        institutionKey = "BNC",
        name = name,
        kind = AccountKind.CREDIT_CARD,
        currency = currency,
        debtMinor = debt,
        minPaymentMinor = due,
    )

    @Test
    fun `ordena de menor a mayor saldo dentro de la moneda`() {
        val plan = PayoffPlanner.plan(
            listOf(
                item(1, "Grande", 900_000),
                item(2, "Chica", 100_000),
                item(3, "Media", 400_000),
            )
        )
        assertEquals(listOf("Chica", "Media", "Grande"), plan.map { it.item.name })
        assertEquals(listOf(1, 2, 3), plan.map { it.rank })
    }

    @Test
    fun `estima los meses con el pago minimo`() {
        val plan = PayoffPlanner.plan(listOf(item(1, "Tarjeta", 1_000_000, due = 250_000)))
        assertEquals(4, plan.single().months)
    }

    @Test
    fun `sin pago minimo no inventa meses`() {
        val plan = PayoffPlanner.plan(listOf(item(1, "Tarjeta", 500_000)))
        assertNull(plan.single().months)
    }

    @Test
    fun `no mezcla monedas al ordenar`() {
        val plan = PayoffPlanner.plan(
            listOf(
                item(1, "USD grande", 5_000, "USD"),
                item(2, "VES chica", 1_000, "VES"),
            )
        )
        assertEquals(listOf("USD grande", "VES chica").sorted(), plan.map { it.item.name }.sorted())
        assertEquals(plan.map { it.item.currency }.distinct().size, 2)
    }

    @Test
    fun `deudas en cero no entran`() {
        assertTrue(PayoffPlanner.plan(listOf(item(1, "Pagada", 0))).isEmpty())
    }
}
