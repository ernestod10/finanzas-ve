@file:OptIn(kotlin.time.ExperimentalTime::class)

package dev.tubolsillo.app.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class SubscriptionDatesTest {

    private val zone = TimeZone.of("America/Caracas")
    private fun day(y: Int, m: Int, d: Int) =
        LocalDate(y, m, d).atStartOfDayIn(zone).toEpochMilliseconds() + 12 * 3_600_000L

    private fun asDate(ms: Long): LocalDate =
        kotlin.time.Instant.fromEpochMilliseconds(ms).toLocalDateTime(zone).date

    @Test
    fun `si el dia ya paso, cae el mes siguiente`() {
        val next = SubscriptionDates.nextCharge(15, day(2026, 11, 20), zone)
        assertEquals(LocalDate(2026, 12, 15), asDate(next))
    }

    @Test
    fun `si el dia esta por venir, cae este mes`() {
        val next = SubscriptionDates.nextCharge(25, day(2026, 11, 20), zone)
        assertEquals(LocalDate(2026, 11, 25), asDate(next))
    }

    @Test
    fun `diciembre rola al ano siguiente`() {
        val next = SubscriptionDates.nextCharge(5, day(2026, 12, 20), zone)
        assertEquals(LocalDate(2027, 1, 5), asDate(next))
    }

    @Test
    fun `el dia 31 se ajusta al ultimo dia del mes`() {
        val next = SubscriptionDates.nextCharge(30, day(2027, 2, 1), zone)
        assertEquals(LocalDate(2027, 2, 28), asDate(next))
    }

    @Test
    fun `los dias que faltan son coherentes`() {
        assertEquals(5, SubscriptionDates.daysUntilNext(25, day(2026, 11, 20), zone))
    }
}
