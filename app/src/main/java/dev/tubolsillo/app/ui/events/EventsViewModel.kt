package dev.tubolsillo.app.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.tubolsillo.app.core.db.dao.CaptureEventDao
import dev.tubolsillo.app.core.db.entity.CaptureEventEntity
import dev.tubolsillo.app.domain.model.CaptureOutcome
import dev.tubolsillo.app.domain.model.SourceType
import dev.tubolsillo.app.domain.usecase.CapturePipeline
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventsViewModel @Inject constructor(
    captureEventDao: CaptureEventDao,
    private val pipeline: CapturePipeline,
) : ViewModel() {

    val events: StateFlow<List<CaptureEventEntity>> = captureEventDao.observeRecent(200)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Replay de depuración: pegar texto crudo y ver el veredicto del pipeline. */
    fun replay(text: String, onResult: (CaptureOutcome) -> Unit) {
        viewModelScope.launch {
            val outcome = pipeline.ingest(
                rawText = text,
                source = SourceType.SMS,
                sender = "replay",
                hintedInstitution = null,
            )
            onResult(outcome)
        }
    }
}
