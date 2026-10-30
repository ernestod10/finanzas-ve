package dev.tubolsillo.app.core.time

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/** Aritmética de meses del plan. Todo con TimeZone.UTC para que sea determinista. */
class MonthBucketsTest {

    private val utc = TimeZone.UTC

    @Test
    fun `ultimo dia de febrero comun es 28`() {
        assertEquals(28, MonthBuckets.lastDayOf("2026-02"))
    }

    @Test
    fun `ultimo dia de febrero bisiesto es 29`() {
        assertEquals(29, MonthBuckets.lastDayOf("2028-02"))
    }

    @Test
    fun `ultimo dia de abril es 30`() {
        assertEquals(30, MonthBuckets.lastDayOf("2026-04"))
    }

    @Test
    fun `dia 31 en abril cae el 30`() {
        assertEquals(
            MonthBuckets.dateIn("2026-04", 30, utc),
            MonthBuckets.dateIn("2026-04", 31, utc),
        )
    }

    @Test
    fun `dia 31 en febrero cae el 28`() {
        assertEquals(
            MonthBuckets.dateIn("2026-02", 28, utc),
            MonthBuckets.dateIn("2026-02", 31, utc),
        )
    }

    @Test
    fun `dia 30 en febrero cae el 28`() {
        assertEquals(
            MonthBuckets.dateIn("2026-02", 28, utc),
            MonthBuckets.dateIn("2026-02", 30, utc),
        )
    }

    @Test
    fun `un dia valido no se mueve`() {
        assertEquals(
            MonthBuckets.dateIn("2026-09", 15, utc),
            MonthBuckets.dateIn("2026-09", 15, utc),
        )
        // El 15 de septiembre es anterior al 15 de octubre.
        kotlin.test.assertTrue(
            MonthBuckets.dateIn("2026-09", 15, utc) < MonthBuckets.dateIn("2026-10", 15, utc),
        )
    }

    @Test
    fun `mes siguiente cruza el ano`() {
        assertEquals("2027-01", MonthBuckets.plusMonths("2026-12", 1))
    }

    @Test
    fun `mes anterior cruza el ano`() {
        assertEquals("2025-12", MonthBuckets.plusMonths("2026-01", -1))
    }

    @Test
    fun `plusMonths conserva el mes con cero`() {
        assertEquals("2026-09", MonthBuckets.plusMonths("2026-09", 0))
    }

    @Test
    fun `dayOfMonthOf devuelve el dia del mes`() {
        val epoch = MonthBuckets.dateIn("2026-02", 28, utc)
        assertEquals(28, MonthBuckets.dayOfMonthOf(epoch, utc))
    }

    @Test
    fun `startOfDay coincide con dateIn de su propio dia`() {
        val epoch = MonthBuckets.dateIn("2026-09", 15, utc) + 13 * 60 * 60 * 1000L
        assertEquals(MonthBuckets.dateIn("2026-09", 15, utc), MonthBuckets.startOfDay(epoch, utc))
    }
}
