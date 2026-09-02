package dev.tubolsillo.app.core.time

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * Utilidades de meses calendario (para presupuestos) y buckets de tiempo
 * (para dedup en Fase 3). Todo el almacenamiento sigue siendo epoch ms UTC.
 */
object MonthBuckets {

    /** Clave "yyyy-MM" del mes local al que pertenece el epoch. */
    fun monthKey(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        YearMonth.from(Instant.ofEpochMilli(epochMs).atZone(zone)).toString()

    fun monthKeyNow(): String = YearMonth.now().toString()

    /** [inicio, fin) del mes "yyyy-MM" en epoch ms (zona local). */
    fun bounds(month: String, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val ym = YearMonth.parse(month)
        val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    /** Inicio del bucket de [bucketMinutes] al que pertenece el epoch. */
    fun bucketStart(epochMs: Long, bucketMinutes: Long): Long {
        val bucketMs = bucketMinutes * 60_000L
        return Math.floorDiv(epochMs, bucketMs) * bucketMs
    }
}
