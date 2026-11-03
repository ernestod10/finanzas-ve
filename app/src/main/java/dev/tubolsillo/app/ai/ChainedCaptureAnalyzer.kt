package dev.tubolsillo.app.ai

import dev.tubolsillo.app.domain.ai.AiEngine
import dev.tubolsillo.app.domain.ai.AnalyzerAvailability
import dev.tubolsillo.app.domain.ai.CaptureAnalyzer
import dev.tubolsillo.app.domain.ai.CaptureReading
import dev.tubolsillo.app.domain.ai.CsvColumnMapping
import dev.tubolsillo.app.domain.ai.SearchFilter

/**
 * Elige quién responde: Gemini Nano si el dispositivo lo tiene, si no el
 * modelo `.task` que importó el usuario. Si no hay ninguno, la app sigue
 * funcionando sin las funciones asistidas.
 */
class ChainedCaptureAnalyzer(
    private val nano: CaptureAnalyzer,
    private val local: CaptureAnalyzer,
) : CaptureAnalyzer {

    private suspend fun active(): CaptureAnalyzer =
        if (nano.availability() == AnalyzerAvailability.AVAILABLE) nano else local

    override suspend fun availability(): AnalyzerAvailability =
        if (nano.availability() == AnalyzerAvailability.AVAILABLE) AnalyzerAvailability.AVAILABLE
        else local.availability()

    override suspend fun statusDetail(): String? {
        val nanoState = nano.availability()
        return if (nanoState == AnalyzerAvailability.AVAILABLE) "Gemini Nano (" + (nano.statusDetail() ?: "AICore") + ")"
        else "Sin Nano (" + nanoState + "). " + (local.statusDetail() ?: "")
    }

    override suspend fun ensureModel(onProgress: (Long, Long) -> Unit): Boolean =
        if (nano.availability() == AnalyzerAvailability.AVAILABLE) true
        else if (nano.availability() == AnalyzerAvailability.DOWNLOADABLE) nano.ensureModel(onProgress)
        else local.ensureModel(onProgress)

    override suspend fun analyze(rawText: String, categories: List<String>): CaptureReading? =
        active().analyze(rawText, categories)

    override suspend fun interpretEntry(text: String): CaptureReading? = active().interpretEntry(text)

    override suspend fun summarize(instructions: String): String? = active().summarize(instructions)

    override suspend fun parseSearch(text: String, categories: List<String>): SearchFilter? =
        active().parseSearch(text, categories)

    override suspend fun mapCsvColumns(
        header: List<String>,
        sampleRows: List<String>,
    ): CsvColumnMapping? = active().mapCsvColumns(header, sampleRows)

    override suspend fun classifyBatch(
        descriptions: List<String>,
        categories: List<String>,
    ): List<String?> = active().classifyBatch(descriptions, categories)

    override suspend fun engine(): AiEngine = when {
        nano.availability() == AnalyzerAvailability.AVAILABLE -> AiEngine.NANO
        local.availability() == AnalyzerAvailability.AVAILABLE -> AiEngine.LOCAL
        else -> AiEngine.NONE
    }
}
