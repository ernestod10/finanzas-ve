@file:OptIn(ExperimentalTime::class)

package dev.tubolsillo.app.ui.components

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val zone: TimeZone get() = TimeZone.currentSystemDefault()

private fun formatWith(epochMs: Long, pattern: String): String {
    val dt = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone)
    return pattern
        .replace("HH", dt.hour.toString().padStart(2, '0'))
        .replace("mm", dt.minute.toString().padStart(2, '0'))
        .replace("yyyy", dt.year.toString())
        .replace("d", dt.dayOfMonth.toString())
        .replace("MMMM", monthName(dt.monthNumber))
        .replace("MMM", monthShort(dt.monthNumber))
        .replace("MM", dt.monthNumber.toString().padStart(2, '0'))
}

private val MONTHS = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)
private val MONTHS_SHORT = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

private fun monthName(n: Int): String = MONTHS.getOrElse(n - 1) { "" }
private fun monthShort(n: Int): String = MONTHS_SHORT.getOrElse(n - 1) { "" }

fun formatTime(epochMs: Long): String = formatWith(epochMs, "HH:mm")

fun formatDate(epochMs: Long): String {
    val dt = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone)
    return "${dt.dayOfMonth} ${monthName(dt.monthNumber)} ${dt.year}"
}

/** Etiqueta de agrupación por día: "Hoy", "Ayer", "5 sep". */
fun dayLabel(epochMs: Long): String {
    val date = kotlinx.datetime.Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).date
    val today = Clock.System.now().toLocalDateTime(zone).date
    val yesterday = kotlinx.datetime.LocalDate.fromEpochDays(today.toEpochDays() - 1)
    return when (date) {
        today -> "Hoy"
        yesterday -> "Ayer"
        else -> "${date.dayOfMonth} ${monthShort(date.monthNumber)}"
    }
}

/** "septiembre 2026" para el selector de mes del presupuesto. */
fun monthLabel(monthKey: String): String {
    val parts = monthKey.split("-")
    val m = parts[1].toInt()
    return monthName(m) + " " + parts[0]
}
