package dev.tubolsillo.app.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import dev.tubolsillo.app.domain.ai.AnalyzerAvailability
import dev.tubolsillo.app.domain.ai.CaptureAnalyzer
import dev.tubolsillo.app.domain.ai.CaptureReading
import org.json.JSONObject

/**
 * Lector y asistente con Gemini Nano (AICore).
 *
 * El modelo corre dentro del servicio AICore: no ocupa RAM de la app ni viaja
 * en el APK. Solo está disponible en dispositivos/regiones soportadas y con el
 * build de AICore que habilita a terceros, así que cada llamada tolera ausencia.
 */
class NanoCaptureAnalyzer : CaptureAnalyzer {

    private var model: GenerativeModel? = null
    private var detail: String? = null

    private fun model(): GenerativeModel =
        model ?: Generation.getClient().also { model = it }

    override suspend fun statusDetail(): String? = detail

    override suspend fun availability(): AnalyzerAvailability {
        return try {
            val status = model().checkStatus()
            detail = "AICore checkStatus = " + status + statusName(status)
            android.util.Log.d("NanoAnalyzer", detail!!)
            when (status) {
                FeatureStatus.AVAILABLE -> AnalyzerAvailability.AVAILABLE
                FeatureStatus.DOWNLOADABLE -> AnalyzerAvailability.DOWNLOADABLE
                FeatureStatus.DOWNLOADING -> AnalyzerAvailability.DOWNLOADING
                else -> AnalyzerAvailability.UNAVAILABLE
            }
        } catch (e: Exception) {
            detail = e.javaClass.simpleName + ": " + (e.message ?: "sin mensaje")
            android.util.Log.w("NanoAnalyzer", "checkStatus falló", e)
            AnalyzerAvailability.UNAVAILABLE
        }
    }

    private fun statusName(status: Int): String = when (status) {
        FeatureStatus.AVAILABLE -> " (AVAILABLE)"
        FeatureStatus.DOWNLOADABLE -> " (DOWNLOADABLE)"
        FeatureStatus.DOWNLOADING -> " (DOWNLOADING)"
        FeatureStatus.UNAVAILABLE -> " (UNAVAILABLE)"
        else -> " (desconocido)"
    }

    override suspend fun ensureModel(onProgress: (Long, Long) -> Unit): Boolean {
        val m = model()
        if (m.checkStatus() == FeatureStatus.AVAILABLE) return true
        if (m.checkStatus() != FeatureStatus.DOWNLOADABLE) return false
        return runCatching {
            m.download().collect { status ->
                when (status) {
                    is DownloadStatus.DownloadStarted -> onProgress(0L, status.bytesToDownload)
                    is DownloadStatus.DownloadProgress -> onProgress(status.totalBytesDownloaded, 0L)
                    else -> Unit
                }
            }
            m.checkStatus() == FeatureStatus.AVAILABLE
        }.getOrDefault(false)
    }

    override suspend fun analyze(rawText: String, categories: List<String>): CaptureReading? = try {
        val response = model().generateContent(buildCapturePrompt(rawText, categories))
        val text = response.candidates.firstOrNull()?.text
        android.util.Log.d("NanoAnalyzer", "captura: " + (text ?: "null").take(600))
        if (text.isNullOrBlank()) null else parseReading(text, rawText)
    } catch (e: Exception) {
        android.util.Log.w("NanoAnalyzer", "analyze falló: " + e.javaClass.simpleName + ": " + e.message, e)
        null
    }

    override suspend fun interpretEntry(text: String): CaptureReading? = try {
        val response = model().generateContent(buildEntryPrompt(text))
        val raw = response.candidates.firstOrNull()?.text
        android.util.Log.d("NanoAnalyzer", "alta natural: " + (raw ?: "null").take(600))
        if (raw.isNullOrBlank()) null else parseReading(raw, text)
    } catch (e: Exception) {
        android.util.Log.w("NanoAnalyzer", "interpretEntry falló", e)
        null
    }

    override suspend fun summarize(instructions: String): String? = try {
        val response = model().generateContent(instructions)
        response.candidates.firstOrNull()?.text?.trim()?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        android.util.Log.w("NanoAnalyzer", "summarize falló", e)
        null
    }

    private fun buildCapturePrompt(rawText: String, categories: List<String>): String {
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

    private fun buildEntryPrompt(text: String): String = """
        El usuario describe un movimiento que quiere registrar. Responde SOLO con JSON.
        Ejemplo: "gasté 20 en taxi ayer"
        Salida: {"is_transaction":true,"amount":20.0,"currency":null,"merchant":"Taxi","kind":"expense","day_offset":-1}
        Claves:
        - amount: número, sin separador de miles.
        - currency: VES, USD, USDT o null (sin moneda explícita).
        - merchant: descripción corta del gasto o ingreso.
        - kind: income para plata que entra, expense para la que sale.
        - day_offset: 0 si es hoy, -1 ayer, -2 anteayer; null si no se menciona fecha.
        No inventes datos.
        Texto: ${text.take(500)}
        Salida:
    """.trimIndent()

    private fun parseReading(text: String, rawText: String): CaptureReading {
        val json = extractJson(text)
        if (json == null) {
            android.util.Log.d("NanoAnalyzer", "sin JSON en la respuesta")
            return CaptureReading(rawResponse = text)
        }
        val obj = runCatching { JSONObject(json) }.getOrNull()
            ?: return CaptureReading(rawResponse = text)

        fun str(key: String): String? =
            if (obj.isNull(key)) null else obj.optString(key).takeIf { it.isNotBlank() && it != "null" }

        val amount = if (obj.isNull("amount")) {
            null
        } else {
            obj.optDouble("amount", Double.NaN).takeIf { !it.isNaN() }
        }
        val isTransaction = if (obj.isNull("is_transaction")) {
            null
        } else {
            obj.optBoolean("is_transaction", true)
        }
        val dayOffset = if (obj.isNull("day_offset")) {
            null
        } else {
            obj.optInt("day_offset", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
        }
        val last4 = str("last4")?.filter { it.isDigit() }?.takeLast(4)?.takeIf { it.length == 4 }

        return CaptureReading(
            amount = amount,
            currency = str("currency")?.uppercase(),
            merchant = str("merchant"),
            last4 = last4,
            kind = str("kind")?.lowercase(),
            category = str("category"),
            isTransaction = isTransaction,
            dayOffset = dayOffset,
            rawResponse = text,
        )
    }

    /** El modelo a veces envuelve el JSON en markdown; se recorta al objeto. */
    private fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else null
    }
}
