package dev.tubolsillo.app.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import dev.tubolsillo.app.domain.ai.AiPrompts
import dev.tubolsillo.app.domain.ai.AiResponseParser
import dev.tubolsillo.app.domain.ai.AnalyzerAvailability
import dev.tubolsillo.app.domain.ai.CaptureAnalyzer
import dev.tubolsillo.app.domain.ai.CaptureReading
import dev.tubolsillo.app.domain.ai.CsvColumnMapping
import dev.tubolsillo.app.domain.ai.CsvMappingParser
import dev.tubolsillo.app.domain.ai.SearchFilter
import dev.tubolsillo.app.domain.ai.SearchQueryParser

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
        val response = model().generateContent(AiPrompts.capture(rawText, categories))
        val text = response.candidates.firstOrNull()?.text
        android.util.Log.d("NanoAnalyzer", "captura: " + (text ?: "null").take(600))
        if (text.isNullOrBlank()) null else AiResponseParser.reading(text)
    } catch (e: Exception) {
        android.util.Log.w("NanoAnalyzer", "analyze falló: " + e.javaClass.simpleName + ": " + e.message, e)
        null
    }

    override suspend fun interpretEntry(text: String): CaptureReading? = try {
        val response = model().generateContent(AiPrompts.entry(text))
        val raw = response.candidates.firstOrNull()?.text
        android.util.Log.d("NanoAnalyzer", "alta natural: " + (raw ?: "null").take(600))
        if (raw.isNullOrBlank()) null else AiResponseParser.reading(raw)
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

    override suspend fun parseSearch(text: String, categories: List<String>): SearchFilter? = try {
        val response = model().generateContent(AiPrompts.search(text, categories))
        val raw = response.candidates.firstOrNull()?.text
        android.util.Log.d("NanoAnalyzer", "búsqueda: " + (raw ?: "null").take(400))
        SearchQueryParser.parse(raw, categories)
    } catch (e: Exception) {
        android.util.Log.w("NanoAnalyzer", "parseSearch falló", e)
        null
    }

    override suspend fun classifyBatch(
        descriptions: List<String>,
        categories: List<String>,
    ): List<String?> = try {
        if (descriptions.isEmpty()) {
            emptyList()
        } else {
            val response = model().generateContent(AiPrompts.classify(descriptions, categories))
            val raw = response.candidates.firstOrNull()?.text
            android.util.Log.d("NanoAnalyzer", "clasificación: " + (raw ?: "null").take(400))
            val map = dev.tubolsillo.app.domain.ai.ClassificationParser.parse(raw, descriptions.size, categories)
            List(descriptions.size) { map[it] }
        }
    } catch (e: Exception) {
        android.util.Log.w("NanoAnalyzer", "classifyBatch falló", e)
        List(descriptions.size) { null }
    }

    override suspend fun mapCsvColumns(
        header: List<String>,
        sampleRows: List<String>,
    ): CsvColumnMapping? = try {
        val response = model().generateContent(AiPrompts.csvMapping(header, sampleRows))
        val raw = response.candidates.firstOrNull()?.text
        android.util.Log.d("NanoAnalyzer", "csv: " + (raw ?: "null").take(300))
        CsvMappingParser.parse(raw, header.size)
    } catch (e: Exception) {
        android.util.Log.w("NanoAnalyzer", "mapCsvColumns falló", e)
        null
    }
}
