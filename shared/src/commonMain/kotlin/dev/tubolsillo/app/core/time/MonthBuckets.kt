package dev.tubolsillo.app.core.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
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

    private val defaultZone: TimeZone by lazy { TimeZone.currentSystemDefault() }

    /** Clave "yyyy-MM" del mes local al que pertenece el epoch. */
    fun monthKey(epochMs: Long, zone: TimeZone = defaultZone): String {
        val dt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone)
        return dt.year.toString() + "-" + dt.monthNumber.toString().padStart(2, '0')
    }

    fun monthKeyNow(): String =
        monthKey(Clock.System.now().toEpochMilliseconds())

    /** [inicio, fin) del mes "yyyy-MM" en epoch ms (zona local). */
    fun bounds(month: String, zone: TimeZone = defaultZone): Pair<Long, Long> {
        val (year, monthNumber) = yearMonth(month)
        val start = LocalDate(year, monthNumber, 1)
        val end = start.plus(1, DateTimeUnit.MONTH)
        return start.atStartOfDayIn(zone).toEpochMilliseconds() to
            end.atStartOfDayIn(zone).toEpochMilliseconds()
    }

    /** Mes "yyyy-MM" desplazado [delta] meses (negativo hacia atrás). */
    fun plusMonths(month: String, delta: Int): String {
        val (year, monthNumber) = yearMonth(month)
        val moved = LocalDate(year, monthNumber, 1).plus(delta, DateTimeUnit.MONTH)
        return moved.year.toString() + "-" + moved.monthNumber.toString().padStart(2, '0')
    }

    /**
     * Epoch ms local → medianoche UTC de esa misma fecha, que es lo que espera
     * el DatePicker de Material (trabaja en UTC para no correr el día).
     */
    fun toPickerMillis(epochMs: Long, zone: TimeZone = defaultZone): Long =
        Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).date
            .atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

    /** Valor del DatePicker (medianoche UTC) → inicio del día local de esa fecha. */
    fun fromPickerMillis(pickerMillis: Long, zone: TimeZone = defaultZone): Long =
        Instant.fromEpochMilliseconds(pickerMillis).toLocalDateTime(TimeZone.UTC).date
            .atStartOfDayIn(zone).toEpochMilliseconds()

    /** Día del mes (1..31) al que pertenece el epoch, en la zona dada. */
    fun dayOfMonthOf(epochMs: Long, zone: TimeZone = defaultZone): Int =
        Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).dayOfMonth

    /** Inicio del día local al que pertenece el epoch. */
    fun startOfDay(epochMs: Long, zone: TimeZone = defaultZone): Long =
        Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).date
            .atStartOfDayIn(zone).toEpochMilliseconds()

    /** Año y número de mes de la clave "yyyy-MM". */
    fun yearMonth(month: String): Pair<Int, Int> {
        val dash = month.indexOf('-')
        if (dash <= 0) {
            val parts = month.split("-")
            return parts[0].toInt() to parts[1].toInt()
        }
        return month.substring(0, dash).toInt() to month.substring(dash + 1).toInt()
    }

    /** Último día del mes "yyyy-MM" (28..31). */
    fun lastDayOf(month: String): Int {
        val (year, monthNumber) = yearMonth(month)
        val start = LocalDate(year, monthNumber, 1)
        val next = start.plus(1, DateTimeUnit.MONTH)
        return next.minus(1, DateTimeUnit.DAY).dayOfMonth
    }

    /**
     * Inicio del día [day] dentro del mes "yyyy-MM", en epoch ms.
     * Si el mes es más corto que [day] se recorta al último día: el día 31 de
     * un recurrente cae el 30 en abril y el 28 (o 29) en febrero.
     */
    fun dateIn(month: String, day: Int, zone: TimeZone = defaultZone): Long {
        val (year, monthNumber) = yearMonth(month)
        val safeDay = day.coerceIn(1, lastDayOf(month))
        return LocalDate(year, monthNumber, safeDay).atStartOfDayIn(zone).toEpochMilliseconds()
    }

    /** Inicio del bucket de [bucketMinutes] al que pertenece el epoch. */
    fun bucketStart(epochMs: Long, bucketMinutes: Long): Long {
        val bucketMs = bucketMinutes * 60_000L
        return Math.floorDiv(epochMs, bucketMs) * bucketMs
    }
}
