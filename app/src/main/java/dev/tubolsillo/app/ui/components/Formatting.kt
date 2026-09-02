package dev.tubolsillo.app.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zone: ZoneId get() = ZoneId.systemDefault()

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es"))
private val fullDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es"))
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es"))

fun formatTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(zone).format(timeFormatter)

fun formatDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(zone).format(fullDateFormatter)

/** Etiqueta de agrupación por día: "Hoy", "Ayer", "5 sep". */
fun dayLabel(epochMs: Long): String {
    val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> "Hoy"
        today.minusDays(1) -> "Ayer"
        else -> date.format(dateFormatter).replaceFirstChar { it.uppercase() }
    }
}

/** "septiembre 2026" para el selector de mes del presupuesto. */
fun monthLabel(monthKey: String): String {
    val ym = java.time.YearMonth.parse(monthKey)
    return ym.format(monthFormatter).replaceFirstChar { it.uppercase() }
}
