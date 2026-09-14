@file:OptIn(kotlin.time.ExperimentalTime::class)

package dev.tubolsillo.app.data.source.portal

import dev.tubolsillo.app.domain.model.ParsedTransaction
import dev.tubolsillo.app.domain.usecase.DedupGate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.time.Instant

/**
 * Plan de búsqueda para decidir si una fila del portal YA está registrada.
 *
 * Por qué hace falta más que la clave exacta: el bucket de dedup es de 60 min
 * de piso fijo, y el portal suele dar solo la fecha (medianoche) mientras el
 * push trae la hora real. Sin ensanchar el lookup, cada consulta duplicaría lo
 * ya capturado por SMS/push. La clave que se ALMACENA no cambia.
 */
object PortalDedup {

    /** Ventana de día local: mismo importe, misma cuenta, mismo día. */
    data class DayWindow(val fromMs: Long, val toMs: Long, val amountMinor: Long)

    data class Lookup(
        val exactKey: String,
        val neighborKeys: List<String>,
        val dayWindow: DayWindow?,
    )

    fun plan(parsed: ParsedTransaction, recipe: PortalRecipe): Lookup {
        val exact = DedupGate.companionDedupKey(
            institution = parsed.institution,
            currency = parsed.currency,
            amountMinor = parsed.amountMinor,
            occurredAt = parsed.occurredAt,
            merchant = parsed.merchant,
        )
        val neighbors = DedupGate.companionNeighborKeys(
            institution = parsed.institution,
            currency = parsed.currency,
            amountMinor = parsed.amountMinor,
            occurredAt = parsed.occurredAt,
            merchant = parsed.merchant,
        )
        // Solo cuando la fila NO trae hora: ahí la medianoche del portal jamás
        // coincidiría con la hora real del push.
        val window = if (recipe.hasTimeOfDay) null else dayWindow(parsed, recipe)
        return Lookup(exactKey = exact, neighborKeys = neighbors, dayWindow = window)
    }

    private fun dayWindow(parsed: ParsedTransaction, recipe: PortalRecipe): DayWindow? {
        val zone = runCatching { TimeZone.of(recipe.timeZoneId) }.getOrElse { TimeZone.UTC }
        return runCatching {
            val date = Instant.fromEpochMilliseconds(parsed.occurredAt).toLocalDateTime(zone).date
            val from = date.atStartOfDayIn(zone).toEpochMilliseconds()
            DayWindow(
                fromMs = from,
                toMs = from + 24L * 60 * 60 * 1000,
                amountMinor = parsed.amountMinor,
            )
        }.getOrNull()
    }

    /** Magnitud usada al comparar contra la BD (las capturas guardan signo). */
    fun magnitudeOf(parsed: ParsedTransaction): Long = abs(parsed.amountMinor)
}
