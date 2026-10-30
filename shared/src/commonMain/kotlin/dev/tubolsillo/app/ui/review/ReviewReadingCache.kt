package dev.tubolsillo.app.ui.review

import dev.tubolsillo.app.domain.ai.CaptureReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lecturas del modelo compartidas entre la cola de revisión y el análisis
 * oportunista de notificaciones.
 *
 * Vive en memoria: el texto crudo siempre está en la base, así que perder una
 * lectura solo obliga a volver a analizar, nunca a perder un movimiento.
 */
class ReviewReadingCache {

    private val _readings = MutableStateFlow<Map<Long, CaptureReading>>(emptyMap())
    val readings: StateFlow<Map<Long, CaptureReading>> = _readings

    fun put(eventId: Long, reading: CaptureReading) {
        _readings.value = _readings.value + (eventId to reading)
    }
}
