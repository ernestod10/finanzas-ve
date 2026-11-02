package dev.tubolsillo.app.domain.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Los prompts viven aquí y no en cada motor: Gemini Nano (AICore) y un modelo
 * `.task` local deben comportarse igual, o el usuario ve dos apps distintas
 * según el teléfono.
 *
 * Todos piden JSON corto y prohíben inventar: son modelos pequeños.
 */
object AiPrompts {

    fun capture(rawText: String, categories: List<String>): String {
        val cats = if (categories.isEmpty()) "ninguna" else categories.joinToString(", ")
        return """
            Extrae los datos de un mensaje bancario. Responde SOLO con JSON, sin explicaciones.
            Ejemplo:
            Mensaje: Compra por Bs. 25,00 en FARMACIA X. Saldo: Bs. 1.000,00
            Salida: {"is_transaction":true,"amount":25.0,"currency":"VES","merchant":"FARMACIA X","last4":null,"kind":"purchase","category":null}
            Claves:
            - is_transaction: false para claves/OTP, alertas de saldo, publicidad o avisos que no sean un movimiento.
            - amount: número con punto decimal, sin separador de miles. null si no hay monto.
            - currency: VES, USD o USDT.
            - merchant: nombre del comercio o contraparte.
            - last4: últimos 4 dígitos del instrumento, o null.
            - kind: purchase, payment, transfer, income o unknown.
            - category: una de [$cats], o null si ninguna encaja.
            Si un dato no aparece, usa null. No inventes.
            Mensaje: ${rawText.take(1200)}
            Salida:
        """.trimIndent()
    }

    fun entry(text: String, categories: List<String> = emptyList()): String {
        val cats = if (categories.isEmpty()) "ninguna" else categories.joinToString(", ")
        return """
        El usuario describe un movimiento que quiere registrar. Responde SOLO con JSON.
        Ejemplo: "gasté 20 en taxi ayer"
        Salida: {"is_transaction":true,"amount":20.0,"currency":null,"merchant":"Taxi","kind":"expense","day_offset":-1}
        Claves:
        - amount: número, sin separador de miles.
        - currency: VES, USD, USDT o null (sin moneda explícita).
        - merchant: descripción corta del gasto o ingreso.
        - kind: income para plata que entra, expense para la que sale.
        - day_offset: 0 si es hoy, -1 ayer, -2 anteayer; null si no se menciona fecha.
        - category: una de [$cats], o null si ninguna encaja.
        No inventes datos.
        Texto: ${text.take(500)}
        Salida:
    """.trimIndent()
    }

    fun search(query: String, categories: List<String>): String {
        val cats = if (categories.isEmpty()) "ninguna" else categories.joinToString(", ")
        return """
            Traduce una pregunta sobre movimientos a un filtro. Responde SOLO con JSON.
            Ejemplo: "cuánto gasté en farmacia en septiembre"
            Salida: {"text":"farmacia","category":null,"from":"2026-09-01","to":"2026-09-30","income":false}
            Ejemplo: "ingresos de este mes"
            Salida: {"text":null,"category":null,"from":"2026-11-01","to":"2026-11-30","income":true}
            Claves:
            - text: palabra clave de comercio o descripción, o null.
            - category: una de [$cats], o null.
            - from y to: fechas yyyy-MM-dd. Si la pregunta no da fechas, null en ambas.
            - income: true solo ingresos, false solo gastos, null si no lo dice.
            No inventes datos que no estén en la pregunta.
            Pregunta: ${query.take(300)}
            Salida:
        """.trimIndent()
    }

    fun csvMapping(header: List<String>, sampleRows: List<String>): String = """
        Te doy el encabezado y dos filas de un estado de cuenta. Indica qué columna es cada dato.
        Responde SOLO con JSON usando índices que empiezan en 0 (o null si no existe).
        Ejemplo: {"date":0,"description":1,"amount":null,"debit":3,"credit":4}
        Reglas:
        - Si hay una sola columna de monto con signo, usa amount.
        - Si hay columnas separadas, usa debit y credit.
        - date: fecha del movimiento. description: concepto o detalle.
        Encabezado: ${header.joinToString(" | ").take(400)}
        Filas: ${sampleRows.joinToString(" // ").take(600)}
        Salida:
    """.trimIndent()
}

/**
 * Lee la respuesta JSON de cualquiera de los dos motores. Los modelos pequeños
 * a veces devuelven el número como texto ("25,00") o el booleano como cadena,
 * así que se lee campo por campo en vez de deserializar de golpe.
 */
object AiResponseParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun reading(response: String?): CaptureReading? {
        val obj = jsonObject(response) ?: return null
        val amount = obj.double("amount")
        val last4 = obj.string("last4")?.filter { it.isDigit() }?.takeLast(4)?.takeIf { it.length == 4 }
        return CaptureReading(
            amount = amount,
            currency = obj.string("currency")?.uppercase(),
            merchant = obj.string("merchant"),
            last4 = last4,
            kind = obj.string("kind")?.lowercase(),
            category = obj.string("category"),
            isTransaction = obj.boolean("is_transaction"),
            dayOffset = obj.int("day_offset"),
            rawResponse = response,
        )
    }

    fun jsonObject(response: String?): JsonObject? {
        val text = response?.trim().orEmpty()
        if (text.isEmpty()) return null
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            json.parseToJsonElement(text.substring(start, end + 1)) as? JsonObject
        }.getOrNull()
    }

    private fun JsonObject.primitive(key: String): JsonPrimitive? {
        val value = this[key] ?: return null
        val p = runCatching { value.jsonPrimitive }.getOrNull() ?: return null
        if (!p.isString && p.content == "null") return null
        return p
    }

    fun JsonObject.string(key: String): String? =
        primitive(key)?.content?.trim()?.takeIf { it.isNotBlank() && it != "null" }

    fun JsonObject.double(key: String): Double? {
        val p = primitive(key) ?: return null
        p.doubleOrNull?.let { return it }
        // "25,00" o "1.234,56": se normaliza a mano.
        val raw = p.content.replace(" ", "")
        if (raw.isBlank()) return null
        val normalized = when {
            raw.contains(',') && raw.contains('.') -> raw.replace(".", "").replace(',', '.')
            raw.contains(',') -> raw.replace(',', '.')
            else -> raw
        }
        return normalized.toDoubleOrNull()
    }

    fun JsonObject.int(key: String): Int? {
        val p = primitive(key) ?: return null
        return p.intOrNull ?: p.content.toDoubleOrNull()?.toInt()
    }

    fun JsonObject.boolean(key: String): Boolean? {
        val p = primitive(key) ?: return null
        p.booleanOrNull?.let { return it }
        return when (p.content.lowercase()) {
            "true", "1", "yes", "si", "sí" -> true
            "false", "0", "no" -> false
            else -> null
        }
    }
}
