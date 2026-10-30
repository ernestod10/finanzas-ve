@file:OptIn(ExperimentalTime::class)

package dev.tubolsillo.app.ui.components

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val cachedZone: TimeZone by lazy { TimeZone.currentSystemDefault() }

private val MONTHS = arrayOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)
private val MONTHS_SHORT = arrayOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

private fun monthName(n: Int): String = MONTHS.getOrElse(n - 1) { "" }
private fun monthShort(n: Int): String = MONTHS_SHORT.getOrElse(n - 1) { "" }

fun formatTime(epochMs: Long): String {
    val dt = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(cachedZone)
    val h = dt.hour
    val m = dt.minute
    return buildString(5) {
        if (h < 10) append('0')
        append(h)
        append(':')
        if (m < 10) append('0')
        append(m)
    }
}

fun formatDate(epochMs: Long): String {
    val dt = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(cachedZone)
    return "${dt.dayOfMonth} ${monthName(dt.monthNumber)} ${dt.year}"
}

/** Epoch days del día actual en la zona local. */
fun currentEpochDays(): Long =
    Clock.System.now().toLocalDateTime(cachedZone).date.toEpochDays()

/** Etiqueta de agrupación por día: "Hoy", "Ayer", "5 sep". */
fun dayLabel(epochMs: Long, todayEpochDays: Long = currentEpochDays()): String {
    val date = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(cachedZone).date
    return when (date.toEpochDays()) {
        todayEpochDays -> "Hoy"
        todayEpochDays - 1L -> "Ayer"
        else -> "${date.dayOfMonth} ${monthShort(date.monthNumber)}"
    }
}

/** "septiembre 2026" para el selector de mes del presupuesto. */
fun monthLabel(monthKey: String): String {
    val dash = monthKey.indexOf('-')
    if (dash <= 0 || dash + 1 >= monthKey.length) return monthKey
    val year = monthKey.substring(0, dash)
    val m = monthKey.substring(dash + 1).toIntOrNull() ?: return monthKey
    return "${monthName(m)} $year"
}

/** "28 oct": fecha corta para una cabecera. */
fun dayMonthLabel(epochMs: Long): String {
    val date = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(cachedZone).date
    return "${date.dayOfMonth} ${monthShort(date.monthNumber)}"
}

/** Saludo según la hora local, sin inventar un nombre de usuario. */
fun greetingForNow(): String =
    when (Clock.System.now().toLocalDateTime(cachedZone).hour) {
        in 5..11 -> "Buenos días"
        in 12..18 -> "Buenas tardes"
        else -> "Buenas noches"
    }
