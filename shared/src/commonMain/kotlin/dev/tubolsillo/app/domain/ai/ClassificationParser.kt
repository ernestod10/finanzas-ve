package dev.tubolsillo.app.domain.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Lee la respuesta del modelo al clasificar un lote de movimientos.
 *
 * Devuelve una categoría **solo si existe** en la lista real de la app: un
 * nombre inventado se descarta en vez de crear categorías fantasma.
 */
object ClassificationParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(response: String?, itemCount: Int, categories: List<String>): Map<Int, String> {
        val text = response?.trim().orEmpty()
        if (text.isEmpty()) return emptyMap()
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        val raw = if (start >= 0 && end > start) text.substring(start, end + 1) else text
        val array = runCatching { json.parseToJsonElement(raw) as? JsonArray }.getOrNull() ?: return emptyMap()

        val valid = categories.associateBy { it.lowercase() }
        val out = mutableMapOf<Int, String>()
        array.forEach { element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
            val index = indexOf(obj) ?: return@forEach
            if (index !in 0 until itemCount) return@forEach
            val name = obj["category"]?.jsonPrimitive?.content?.trim() ?: return@forEach
            if (name.equals("null", true) || name.isBlank()) return@forEach
            valid[name.lowercase()]?.let { out[index] = it }
        }
        return out
    }

    private fun indexOf(obj: JsonObject): Int? =
        obj["i"]?.jsonPrimitive?.intOrNull
            ?: obj["index"]?.jsonPrimitive?.intOrNull
            ?: obj["id"]?.jsonPrimitive?.intOrNull
}
