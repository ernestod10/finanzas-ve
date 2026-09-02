package dev.tubolsillo.app.core.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Utilidades de meses calendario (para presupuestos) y buckets de tiempo
 * (para dedup). Todo el almacenamiento sigue siendo epoch ms UTC.
 * Sin dependencias JVM (java.time) para poder vivir en commonMain.
 */
@OptIn(ExperimentalTime::class)
object MonthBuckets {

    /** Clave "yyyy-MM" del mes local al que pertenece el epoch. */
    fun monthKey(epochMs: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
        val dt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone)
        return dt.year.toString() + "-" + dt.monthNumber.toString().padStart(2, '0')
    }

    fun monthKeyNow(): String =
        monthKey(Clock.System.now().toEpochMilliseconds())

    /** [inicio, fin) del mes "yyyy-MM" en epoch ms (zona local). */
    fun bounds(month: String, zone: TimeZone = TimeZone.currentSystemDefault()): Pair<Long, Long> {
        val parts = month.split("-")
        val year = parts[0].toInt()
        val monthNumber = parts[1].toInt()
        val start = LocalDate(year, monthNumber, 1)
        val end = start.plus(1, DateTimeUnit.MONTH)
        return start.atStartOfDayIn(zone).toEpochMilliseconds() to
            end.atStartOfDayIn(zone).toEpochMilliseconds()
    }

    /** Inicio del bucket de [bucketMinutes] al que pertenece el epoch. */
    fun bucketStart(epochMs: Long, bucketMinutes: Long): Long {
        val bucketMs = bucketMinutes * 60_000L
        return Math.floorDiv(epochMs, bucketMs) * bucketMs
    }
}
