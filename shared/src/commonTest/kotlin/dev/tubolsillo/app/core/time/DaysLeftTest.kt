@file:OptIn(kotlin.time.ExperimentalTime::class)

package dev.tubolsillo.app.core.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DaysLeftTest {

    private val zone = TimeZone.UTC
    private fun day(y: Int, m: Int, d: Int) = LocalDate(y, m, d).atStartOfDayIn(zone).toEpochMilliseconds()

    @Test
    fun `un mes que no es el actual no tiene cuenta regresiva`() {
        assertNull(MonthBuckets.daysLeftIn("2026-10", day(2026, 11, 16), zone))
        assertNull(MonthBuckets.daysLeftIn("2026-12", day(2026, 11, 16), zone))
    }

    @Test
    fun `cuenta los dias que quedan incluido hoy`() {
        assertEquals(15, MonthBuckets.daysLeftIn("2026-11", day(2026, 11, 16), zone))
        assertEquals(30, MonthBuckets.daysLeftIn("2026-11", day(2026, 11, 1), zone))
        assertEquals(1, MonthBuckets.daysLeftIn("2026-11", day(2026, 11, 30), zone))
    }
}
