@file:OptIn(kotlin.time.ExperimentalTime::class)

package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.db.entity.TransactionEntity
import dev.tubolsillo.app.domain.model.SourceType
import dev.tubolsillo.app.domain.model.TxStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubscriptionDetectorTest {

    private fun day(year: Int, month: Int, day: Int): Long =
        LocalDate(year, month, day).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

    private fun tx(
        id: Long,
        description: String,
        amount: Long,
        occurredAt: Long,
        currency: String = "VES",
    ) = TransactionEntity(
        id = id,
        accountId = 1,
        occurredAt = occurredAt,
        amountMinor = amount,
        currency = currency,
        description = description,
        category = "OTHER",
        status = TxStatus.CONFIRMED.key,
        source = SourceType.PORTAL.key,
    )

    @Test
    fun `detecta un cobro mensual estable`() {
        val now = day(2026, 11, 20)
        val txs = listOf(
            tx(1, "NETFLIX.COM 1234", -1_500, day(2026, 8, 15)),
            tx(2, "NETFLIX.COM 1234", -1_500, day(2026, 9, 14)),
            tx(3, "NETFLIX.COM 1234", -1_500, day(2026, 10, 15)),
            tx(4, "NETFLIX.COM 1234", -1_500, day(2026, 11, 15)),
        )
        val found = SubscriptionDetector.detect(txs, now)
        assertEquals(1, found.size)
        val netflix = found.first()
        assertEquals(1_500, netflix.amountMinor)
        assertEquals(4, netflix.occurrences)
        // El día se calcula en la zona local (el epoch del test es medianoche UTC).
        val expectedDay = kotlin.time.Instant.fromEpochMilliseconds(day(2026, 11, 15))
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.dayOfMonth
        assertEquals(expectedDay, netflix.dayOfMonth)
        assertTrue(netflix.periodDays in 28..32)
        assertTrue(netflix.nextAt > day(2026, 11, 15))
    }

    @Test
    fun `no inventa con montos que varían`() {
        val now = day(2026, 11, 20)
        val txs = listOf(
            tx(1, "SUPERMERCADO", -1_500, day(2026, 8, 15)),
            tx(2, "SUPERMERCADO", -9_800, day(2026, 9, 14)),
            tx(3, "SUPERMERCADO", -3_100, day(2026, 10, 15)),
        )
        assertTrue(SubscriptionDetector.detect(txs, now).isEmpty())
    }

    @Test
    fun `no detecta con dos cobros ni con intervalos cortos`() {
        val now = day(2026, 11, 20)
        val dos = listOf(
            tx(1, "SPOTIFY", -990, day(2026, 10, 15)),
            tx(2, "SPOTIFY", -990, day(2026, 11, 15)),
        )
        assertTrue(SubscriptionDetector.detect(dos, now).isEmpty())

        val semanal = listOf(
            tx(3, "PANADERIA", -500, day(2026, 11, 1)),
            tx(4, "PANADERIA", -500, day(2026, 11, 8)),
            tx(5, "PANADERIA", -500, day(2026, 11, 15)),
        )
        assertTrue(SubscriptionDetector.detect(semanal, now).isEmpty())
    }

    @Test
    fun `los ingresos no son suscripciones`() {
        val now = day(2026, 11, 20)
        val txs = listOf(
            tx(1, "NOMINA", 20_000_000, day(2026, 8, 30)),
            tx(2, "NOMINA", 20_000_000, day(2026, 9, 30)),
            tx(3, "NOMINA", 20_000_000, day(2026, 10, 30)),
        )
        assertTrue(SubscriptionDetector.detect(txs, now).isEmpty())
    }

    @Test
    fun `el nombre sale legible`() {
        val now = day(2026, 11, 20)
        val txs = listOf(
            tx(1, "NETFLIX.COM", -1_500, day(2026, 8, 15)),
            tx(2, "NETFLIX.COM", -1_500, day(2026, 9, 15)),
            tx(3, "NETFLIX.COM", -1_500, day(2026, 10, 15)),
        )
        val found = SubscriptionDetector.detect(txs, now).single()
        assertEquals("Netflix.com", found.merchant)
    }
}
