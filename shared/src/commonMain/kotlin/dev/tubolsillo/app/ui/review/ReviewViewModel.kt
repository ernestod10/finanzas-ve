package dev.tubolsillo.app.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.db.dao.CaptureEventDao
import dev.tubolsillo.app.core.db.entity.CaptureEventEntity
import dev.tubolsillo.app.domain.ai.AnalyzerAvailability
import dev.tubolsillo.app.domain.ai.CaptureAnalyzer
import dev.tubolsillo.app.domain.ai.CaptureReading
import dev.tubolsillo.app.domain.model.ParseStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Cola de revisión: capturas que el parser de reglas no pudo leer
 * (UNMATCHED/FAILED). El modelo on-device, si está disponible, propone una
 * segunda lectura; el usuario decide qué hacer con ella.
 */
class ReviewViewModel(
    private val captureEventDao: CaptureEventDao,
    private val analyzer: CaptureAnalyzer,
    private val readingCache: ReviewReadingCache,
    categoryRepository: dev.tubolsillo.app.domain.repo.CategoryRepository,
) : ViewModel() {

    val categories: StateFlow<List<dev.tubolsillo.app.core.db.entity.CategoryEntity>> =
        categoryRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val events: StateFlow<List<CaptureEventEntity>> =
        captureEventDao.observeByStatus(
            statuses = listOf(ParseStatus.UNMATCHED.key, ParseStatus.FAILED.key),
            limit = 100,
        ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _availability = MutableStateFlow(AnalyzerAvailability.CHECKING)
    val availability: StateFlow<AnalyzerAvailability> = _availability

    /** Motivo técnico del estado (código de AICore o excepción). */
    private val _detail = MutableStateFlow<String?>(null)
    val detail: StateFlow<String?> = _detail

    /** Progreso de descarga 0..1, o null si no hay descarga en curso. */
    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress

    /** Lecturas del modelo. Compartidas con el listener para el análisis oportunista. */
    val analyses: StateFlow<Map<Long, CaptureReading>> = readingCache.readings

    private val _busy = MutableStateFlow<Set<Long>>(emptySet())
    val busy: StateFlow<Set<Long>> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    /** Progreso del lote: null cuando no hay uno corriendo. */
    data class BatchProgress(val done: Int, val total: Int)

    private val _batch = MutableStateFlow<BatchProgress?>(null)
    val batch: StateFlow<BatchProgress?> = _batch
    private var batchJob: kotlinx.coroutines.Job? = null

    init {
        refreshStatus()
    }

    private fun refreshStatus() {
        viewModelScope.launch {
            _availability.value = analyzer.availability()
            _detail.value = analyzer.statusDetail()
        }
    }

    fun analyze(event: CaptureEventEntity) {
        if (event.id in _busy.value) return
        viewModelScope.launch {
            _busy.value = _busy.value + event.id
            try {
                val reading = analyzer.analyze(event.rawText, categories.value.map { it.name })
                if (reading == null || (reading.isEmpty && !reading.isNoise)) {
                    _message.value = "El modelo no pudo leer esta captura."
                } else {
                    readingCache.put(event.id, reading)
                }
            } catch (e: Exception) {
                _message.value = "No se pudo analizar: " + (e.message ?: "error")
            } finally {
                _busy.value = _busy.value - event.id
                refreshStatus()
            }
        }
    }

    fun downloadModel() {
        viewModelScope.launch {
            _availability.value = AnalyzerAvailability.DOWNLOADING
            _downloadProgress.value = 0f
            val ok = runCatching {
                analyzer.ensureModel { downloaded, total ->
                    _downloadProgress.value = if (total > 0) {
                        (downloaded.toFloat() / total).coerceIn(0f, 1f)
                    } else 0f
                }
            }.getOrDefault(false)
            _downloadProgress.value = null
            refreshStatus()
            if (!ok) {
                _message.value = "No se pudo descargar el modelo. Revisá AICore en Play Store."
            }
        }
    }

    /** Saca la captura de la cola sin crear movimiento. */
    fun dismiss(event: CaptureEventEntity) {
        viewModelScope.launch {
            captureEventDao.setStatus(event.id, ParseStatus.IGNORED.key)
        }
    }

    /**
     * Analiza en lote las pendientes, en primer plano y con progreso. AICore no
     * permite inferir en segundo plano, así que el usuario mira el avance.
     */
    fun analyzePending(limit: Int = 20) {
        if (_batch.value != null) return
        batchJob = viewModelScope.launch {
            val pending = events.value
                .filter { it.id !in readingCache.readings.value }
                .take(limit)
            if (pending.isEmpty()) {
                _message.value = "No hay capturas pendientes de analizar."
                return@launch
            }
            _batch.value = BatchProgress(done = 0, total = pending.size)
            var noise = 0
            try {
                val categoryNames = categories.value.map { it.name }
                pending.forEachIndexed { index, event ->
                    val reading = runCatching { analyzer.analyze(event.rawText, categoryNames) }.getOrNull()
                    if (reading != null) {
                        readingCache.put(event.id, reading)
                        // El modelo marcó OTP/aviso/publicidad: sale de la cola.
                        if (reading.isNoise) {
                            captureEventDao.setStatus(event.id, ParseStatus.IGNORED.key)
                            noise++
                        }
                    }
                    _batch.value = BatchProgress(done = index + 1, total = pending.size)
                }
            } finally {
                _batch.value = null
                refreshStatus()
                if (noise > 0) {
                    _message.value = noise.toString() + " aviso(s) descartado(s) por no ser movimientos."
                }
            }
        }
    }

    fun cancelBatch() {
        batchJob?.cancel()
        batchJob = null
        _batch.value = null
    }

    fun consumeMessage() {
        _message.value = null
    }
}
