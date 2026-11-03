package dev.tubolsillo.app.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import dev.tubolsillo.app.domain.ai.AiPrompts
import dev.tubolsillo.app.domain.ai.AiResponseParser
import dev.tubolsillo.app.domain.ai.AnalyzerAvailability
import dev.tubolsillo.app.domain.ai.CaptureAnalyzer
import dev.tubolsillo.app.domain.ai.CaptureReading
import dev.tubolsillo.app.domain.ai.CsvColumnMapping
import dev.tubolsillo.app.domain.ai.CsvMappingParser
import dev.tubolsillo.app.domain.ai.SearchFilter
import dev.tubolsillo.app.domain.ai.SearchQueryParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Lector con un modelo `.task` que el usuario importó (LiteRT + MediaPipe).
 *
 * Es el camino alternativo a Gemini Nano: mismos prompts y mismo contrato, así
 * el resto de la app no sabe cuál de los dos respondió. Cargar el modelo ocupa
 * cientos de MB y tarda unos segundos; se mantiene en memoria y se recarga solo
 * si cambia el archivo.
 */
class LocalLlmAnalyzer(
    private val context: Context,
    private val manager: AndroidLocalModelManager,
) : CaptureAnalyzer {

    private val lock = Mutex()
    private var engine: LlmInference? = null
    private var loadedPath: String? = null
    private var lastError: String? = null
    private var idleJob: kotlinx.coroutines.Job? = null
    private val idleScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.Default,
    )

    override suspend fun availability(): AnalyzerAvailability =
        if (manager.modelFile() != null) AnalyzerAvailability.AVAILABLE else AnalyzerAvailability.UNAVAILABLE

    override suspend fun statusDetail(): String? =
        lastError ?: manager.modelFile()?.let { "Modelo local: " + it.name } ?: "Sin modelo local importado."

    override suspend fun ensureModel(onProgress: (Long, Long) -> Unit): Boolean = model() != null

    private suspend fun model(): LlmInference? {
        val file = manager.modelFile() ?: return null
        return lock.withLock {
            if (engine != null && loadedPath == file.absolutePath) return@withLock engine
            engine?.let { runCatching { it.close() } }
            engine = null
            val created = withContext(Dispatchers.IO) {
                // GPU cuando se puede (mucho más rápido); si el teléfono no lo
                // soporta para este modelo, se cae a CPU.
                runCatching { create(file.absolutePath, LlmInference.Backend.GPU) }
                    .recoverCatching { create(file.absolutePath, LlmInference.Backend.CPU) }
                    .onFailure { lastError = "No se pudo cargar el modelo: " + (it.message ?: it.javaClass.simpleName) }
                    .getOrNull()
            }
            engine = created
            loadedPath = if (created != null) file.absolutePath else null
            created
        }
    }

    private fun create(path: String, backend: LlmInference.Backend): LlmInference {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(MAX_TOKENS)
            .setMaxTopK(40)
            .setPreferredBackend(backend)
            .build()
        return LlmInference.createFromOptions(context, options)
    }

    /**
     * Libera el modelo si pasa un rato sin usarse. Cargado ocupa entre 500 MB
     * y 1,5 GB: en segundo plano no tiene por qué seguir residente.
     */
    private fun scheduleIdleRelease() {
        idleJob?.cancel()
        idleJob = idleScope.launch {
            kotlinx.coroutines.delay(IDLE_RELEASE_MS)
            lock.withLock {
                engine?.let { runCatching { it.close() } }
                engine = null
                loadedPath = null
                android.util.Log.d("LocalLlm", "modelo liberado por inactividad")
            }
        }
    }

    private suspend fun generate(prompt: String): String? {
        val model = model() ?: return null
        scheduleIdleRelease()
        return withContext(Dispatchers.IO) {
            runCatching { model.generateResponse(prompt) }
                .onFailure { android.util.Log.w("LocalLlm", "generate falló", it) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }
    }

    override suspend fun analyze(rawText: String, categories: List<String>): CaptureReading? =
        generate(AiPrompts.capture(rawText, categories))?.let(AiResponseParser::reading)

    override suspend fun interpretEntry(text: String): CaptureReading? =
        generate(AiPrompts.entry(text))?.let(AiResponseParser::reading)

    override suspend fun summarize(instructions: String): String? =
        generate(instructions)?.trim()?.takeIf { it.isNotBlank() }

    override suspend fun parseSearch(text: String, categories: List<String>): SearchFilter? =
        SearchQueryParser.parse(generate(AiPrompts.search(text, categories)), categories)

    override suspend fun classifyBatch(
        descriptions: List<String>,
        categories: List<String>,
    ): List<String?> {
        if (descriptions.isEmpty()) return emptyList()
        val raw = generate(AiPrompts.classify(descriptions, categories))
        val map = dev.tubolsillo.app.domain.ai.ClassificationParser.parse(raw, descriptions.size, categories)
        return List(descriptions.size) { map[it] }
    }

    override suspend fun mapCsvColumns(
        header: List<String>,
        sampleRows: List<String>,
    ): CsvColumnMapping? = CsvMappingParser.parse(generate(AiPrompts.csvMapping(header, sampleRows)), header.size)

    private companion object {
        /** Suficiente para un JSON de lectura; más alto ralentiza el modelo pequeño. */
        const val MAX_TOKENS = 1024

        /** Tras este tiempo sin usarse, el modelo se descarga de la RAM. */
        const val IDLE_RELEASE_MS = 5 * 60 * 1000L
    }
}
