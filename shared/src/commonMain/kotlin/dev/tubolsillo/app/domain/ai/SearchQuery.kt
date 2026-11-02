package dev.tubolsillo.app.domain.ai

import dev.tubolsillo.app.core.time.DateParser
import kotlinx.datetime.TimeZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Filtro que sale de una pregunta en lenguaje natural ("¿cuánto gasté en
 * farmacia en septiembre?"). El modelo NO ve los movimientos: solo traduce la
 * pregunta a este filtro y la app hace la consulta determinista, así ninguna
 * cifra puede inventarse.
 */
data class SearchFilter(
    /** Texto libre para la descripción/comercio. */
    val text: String? = null,
    /** Nombre de una categoría de la app, ya validado contra la lista real. */
    val categoryName: String? = null,
    val fromMs: Long? = null,
    val toMs: Long? = null,
    /** true = solo ingresos, false = solo gastos, null = todos. */
    val income: Boolean? = null,
) {
    val isEmpty: Boolean
        get() = text.isNullOrBlank() && categoryName.isNullOrBlank() && fromMs == null &&
            toMs == null && income == null

    /** Texto corto para mostrar qué entendió ("gastos · farmacia · septiembre"). */
    fun describe(monthLabel: String? = null): String {
        val parts = mutableListOf<String>()
        income?.let { parts.add(if (it) "ingresos" else "gastos") }
        categoryName?.let(parts::add)
        text?.takeIf { it.isNotBlank() }?.let { parts.add("\"" + it + "\"") }
        monthLabel?.let(parts::add)
        return parts.joinToString(" · ").ifBlank { "todo" }
    }
}

@Serializable
private data class RawSearch(
    val text: String? = null,
    val category: String? = null,
    /** yyyy-MM-dd */
    val from: String? = null,
    /** yyyy-MM-dd, inclusivo */
    val to: String? = null,
    @SerialName("income") val income: Boolean? = null,
)

/**
 * Convierte la respuesta JSON del modelo en [SearchFilter]. Tolerante a texto
 * alrededor del JSON y a fechas raras; si no hay nada usable devuelve null.
 */
object SearchQueryParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(
        response: String?,
        categories: List<String>,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): SearchFilter? {
        val body = extractJsonObject(response) ?: return null
        val raw = runCatching { json.decodeFromString<RawSearch>(body) }.getOrNull() ?: return null

        val category = raw.category?.trim()?.takeIf { it.isNotBlank() }
            ?.let { wanted -> categories.firstOrNull { it.equals(wanted, ignoreCase = true) } }

        val filter = SearchFilter(
            text = raw.text?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", true) },
            categoryName = category,
            fromMs = dateOf(raw.from, zone, endOfDay = false),
            toMs = dateOf(raw.to, zone, endOfDay = true),
            income = raw.income,
        )
        return filter.takeUnless { it.isEmpty }
    }

    /** El modelo a veces envuelve el JSON en texto o en ```json … ```. */
    private fun extractJsonObject(response: String?): String? {
        val text = response?.trim().orEmpty()
        if (text.isEmpty()) return null
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun dateOf(value: String?, zone: TimeZone, endOfDay: Boolean): Long? {
        val clean = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val start = DateParser.parse(clean, "yyyy-MM-dd", zone) ?: return null
        // "hasta" incluye el día completo.
        return if (endOfDay) start + 86_399_999L else start
    }
}

/** Mapeo de columnas que propone el modelo para un CSV desconocido. */
@Serializable
data class CsvColumnMapping(
    val date: Int? = null,
    val description: Int? = null,
    val amount: Int? = null,
    val debit: Int? = null,
    val credit: Int? = null,
) {
    /** Sin fecha y sin monto no hay nada que importar. */
    val isUsable: Boolean
        get() = date != null && (amount != null || debit != null || credit != null)
}

object CsvMappingParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(response: String?, columnCount: Int): CsvColumnMapping? {
        val text = response?.trim().orEmpty()
        if (text.isEmpty()) return null
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val mapping = runCatching {
            json.decodeFromString<CsvColumnMapping>(text.substring(start, end + 1))
        }.getOrNull() ?: return null
        fun ok(index: Int?) = index != null && index in 0 until columnCount
        val clean = CsvColumnMapping(
            date = mapping.date?.takeIf(::ok),
            description = mapping.description?.takeIf(::ok),
            amount = mapping.amount?.takeIf(::ok),
            debit = mapping.debit?.takeIf(::ok),
            credit = mapping.credit?.takeIf(::ok),
        )
        return clean.takeIf { it.isUsable }
    }
}
