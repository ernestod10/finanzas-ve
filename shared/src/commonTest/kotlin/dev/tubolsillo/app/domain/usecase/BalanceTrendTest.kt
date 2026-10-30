package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.db.entity.AccountEntity
import dev.tubolsillo.app.core.db.entity.BalanceSnapshotEntity
import dev.tubolsillo.app.core.time.MonthBuckets
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BalanceTrendTest {

    private val builder = BalanceTrendBuilder(TotalsCalculator())
    private val utc = TimeZone.UTC
    private val rates = mapOf("USD" to 1.0, "VES" to 0.02)
    private val day = 86_400_000L

    private fun account(id: Long, currency: String = "USD", kind: String = "CHECKING") =
        AccountEntity(id = id, institution = "BANESCO", name = "Cuenta $id", currency = currency, kind = kind)

    private fun snapshot(accountId: Long, at: Long, amountMinor: Long, currency: String = "USD") =
        BalanceSnapshotEntity(
            accountId = accountId,
            capturedAt = at,
            amountMinor = amountMinor,
            currency = currency,
            source = "MANUAL",
        )

    private val from = MonthBuckets.dateIn("2026-09", 1, utc)
    private val now = MonthBuckets.dateIn("2026-09", 5, utc)

    @Test
    fun `sin snapshots no hay serie`() {
        val trend = builder.build(listOf(account(1)), emptyList(), rates, from, now, utc)
        assertFalse(trend.hasEnoughPoints)
        assertTrue(trend.points.isEmpty())
    }

    @Test
    fun `sin cuentas no hay serie`() {
        val snapshots = listOf(snapshot(1, from, 100_00))
        val trend = builder.build(emptyList(), snapshots, rates, from, now, utc)
        assertTrue(trend.points.isEmpty())
    }

    @Test
    fun `un rango de un solo dia no alcanza para la linea`() {
        val trend = builder.build(
            accounts = listOf(account(1)),
            snapshots = listOf(snapshot(1, from, 100_00)),
            rates = rates,
            fromEpochMs = from,
            nowEpochMs = from, // mismo día: un único punto
            zone = utc,
        )
        assertEquals(1, trend.points.size)
        assertFalse(trend.hasEnoughPoints)
        assertEquals(100.0, trend.lastUsd, 0.001)
    }

    @Test
    fun `los dias sin captura arrastran el ultimo saldo`() {
        val trend = builder.build(
            accounts = listOf(account(1)),
            snapshots = listOf(
                snapshot(1, from, 100_00),
                snapshot(1, from + 3 * day, 130_00),
            ),
            rates = rates,
            fromEpochMs = from,
            nowEpochMs = now,
            zone = utc,
        )
        // 1 al 5 de septiembre = 5 puntos; el salto solo aparece donde ocurre.
        assertEquals(5, trend.points.size)
        assertEquals(100.0, trend.points[0].valueUsd, 0.001)
        assertEquals(100.0, trend.points[2].valueUsd, 0.001)
        assertEquals(130.0, trend.points[3].valueUsd, 0.001)
        assertEquals(130.0, trend.lastUsd, 0.001)
    }

    @Test
    fun `suma varias cuentas y convierte monedas`() {
        val trend = builder.build(
            accounts = listOf(account(1), account(2, currency = "VES")),
            snapshots = listOf(
                snapshot(1, from, 100_00),
                snapshot(2, from, 5000_00, currency = "VES"), // Bs 5000 = $100
                snapshot(1, from + day, 110_00),
            ),
            rates = rates,
            fromEpochMs = from,
            nowEpochMs = from + day,
            zone = utc,
        )
        assertEquals(200.0, trend.firstUsd, 0.001)
        assertEquals(210.0, trend.lastUsd, 0.001)
    }

    @Test
    fun `una deuda resta del total`() {
        val trend = builder.build(
            accounts = listOf(account(1), account(2, kind = "CREDIT_CARD")),
            snapshots = listOf(
                snapshot(1, from, 500_00),
                snapshot(2, from, -120_00),
                snapshot(1, from + day, 500_00),
            ),
            rates = rates,
            fromEpochMs = from,
            nowEpochMs = from + day,
            zone = utc,
        )
        assertEquals(380.0, trend.firstUsd, 0.001)
        assertEquals(380.0, trend.lastUsd, 0.001)
    }

    @Test
    fun `un snapshot anterior al rango se arrastra al primer dia`() {
        val trend = builder.build(
            accounts = listOf(account(1)),
            snapshots = listOf(snapshot(1, from - 10 * day, 90_00)),
            rates = rates,
            fromEpochMs = from,
            nowEpochMs = from + day,
            zone = utc,
        )
        assertEquals(2, trend.points.size)
        assertEquals(90.0, trend.firstUsd, 0.001)
    }

    @Test
    fun `la variacion porcentual se calcula sobre el saldo inicial`() {
        val trend = builder.build(
            accounts = listOf(account(1)),
            snapshots = listOf(
                snapshot(1, from, 100_00),
                snapshot(1, from + day, 110_00),
            ),
            rates = rates,
            fromEpochMs = from,
            nowEpochMs = from + day,
            zone = utc,
        )
        assertEquals(10.0, trend.changePct!!, 0.001)
    }

    @Test
    fun `un rango invertido devuelve serie vacia`() {
        val trend = builder.build(
            accounts = listOf(account(1)),
            snapshots = listOf(snapshot(1, from, 100_00)),
            rates = rates,
            fromEpochMs = now,
            nowEpochMs = from,
            zone = utc,
        )
        assertTrue(trend.points.isEmpty())
    }
}
