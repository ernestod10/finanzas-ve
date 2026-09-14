@file:OptIn(kotlin.time.ExperimentalTime::class)

package dev.tubolsillo.app.core.time

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * Parseo de fechas sin java.time: epoch (s o ms), ISO, o un subconjunto de
 * patrones tipo java.time (yyyy, MM, dd, HH, mm, ss con separadores literales).
 * Extraído de CsvImporter para que lo compartan CSV y portal.
 *
 * TRAMPA CONOCIDA: el motor exige que el número de grupos de dígitos del texto
 * coincida con el número de tokens del patrón. "15/08/2026 10:30" con patrón
 * "dd/MM/yyyy" devuelve null (la hora sobra). Si el portal muestra hora, el
 * dateFormat de la receta tiene que incluirla.
 */
object DateParser {

    private val VALID_TOKENS = setOf("yyyy", "MM", "dd", "HH", "mm", "ss")

    fun parse(raw: String, pattern: String?, zone: TimeZone): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        trimmed.toLongOrNull()?.let { epoch ->
            return if (epoch < 10_000_000_000L) epoch * 1000 else epoch
        }
        runCatching { return Instant.parse(trimmed).toEpochMilliseconds() }

        val pat = pattern ?: "yyyy-MM-dd'T'HH:mm:ss"
        val intGroups = Regex("(\\d+)").findAll(trimmed).map { it.value.toIntOrNull() }.toList()
        val tokens = Regex("[a-zA-Z]+").findAll(pat).map { it.value }.filter { it in VALID_TOKENS }.toList()
        if (tokens.isEmpty() || intGroups.size != tokens.size) return null

        var year = 0; var month = 1; var day = 1
        var hour = 0; var minute = 0; var second = 0
        tokens.forEachIndexed { index, token ->
            val value = intGroups[index] ?: return null
            when (token) {
                "yyyy" -> year = if (value < 100) 2000 + value else value
                "MM" -> month = value
                "dd" -> day = value
                "HH" -> hour = value
                "mm" -> minute = value
                "ss" -> second = value
            }
        }
        if (year == 0 || month !in 1..12 || day !in 1..31) return null

        return runCatching {
            if (tokens.any { it == "HH" }) {
                LocalDateTime(year, month, day, hour, minute, second).toInstant(zone).toEpochMilliseconds()
            } else {
                LocalDate(year, month, day).atStartOfDayIn(zone).toEpochMilliseconds()
            }
        }.getOrNull()
    }
}
