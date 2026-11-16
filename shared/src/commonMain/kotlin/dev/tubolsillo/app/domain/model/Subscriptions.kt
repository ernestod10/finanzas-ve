package dev.tubolsillo.app.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.daysUntil

/**
 * Próximo cobro de una suscripción mensual: el día indicado de este mes si aún
 * no pasó, o del mes siguiente. Los meses cortos usan el último día disponible
 * (un cobro "día 31" cae el 28/29/30 según el mes).
 */
object SubscriptionDates {

    fun nextCharge(dayOfMonth: Int, nowMs: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Long {
        val today = kotlin.time.Instant.fromEpochMilliseconds(nowMs).toLocalDate(zone)
        val day = dayOfMonth.coerceIn(1, 31)
        val thisMonth = LocalDate(today.year, today.monthNumber, day.coerceAtMost(lastDay(today.year, today.monthNumber)))
        val candidate = if (thisMonth < today) {
            val (year, month) = if (today.monthNumber == 12) today.year + 1 to 1 else today.year to today.monthNumber + 1
            LocalDate(year, month, day.coerceAtMost(lastDay(year, month)))
        } else {
            thisMonth
        }
        return candidate.atStartOfDayIn(zone).toEpochMilliseconds()
    }

    fun daysUntilNext(dayOfMonth: Int, nowMs: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Int {
        val today = kotlin.time.Instant.fromEpochMilliseconds(nowMs).toLocalDate(zone)
        val next = kotlin.time.Instant.fromEpochMilliseconds(nextCharge(dayOfMonth, nowMs, zone)).toLocalDate(zone)
        return today.daysUntil(next)
    }

    fun lastDay(year: Int, month: Int): Int = when (month) {
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
}

private fun kotlin.time.Instant.toLocalDate(zone: TimeZone): LocalDate =
    toLocalDateTime(zone).date
