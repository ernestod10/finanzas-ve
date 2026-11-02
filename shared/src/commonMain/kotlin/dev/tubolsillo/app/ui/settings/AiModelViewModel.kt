package dev.tubolsillo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.domain.ai.AiEngine
import dev.tubolsillo.app.domain.ai.CaptureAnalyzer
import dev.tubolsillo.app.domain.ai.LocalModelCatalog
import dev.tubolsillo.app.domain.ai.LocalModelManager
import dev.tubolsillo.app.domain.ai.LocalModelStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado de la IA on-device: quién responde y qué modelo local hay instalado. */
class AiModelViewModel(
    private val analyzer: CaptureAnalyzer,
    private val manager: LocalModelManager,
) : ViewModel() {

    val local: StateFlow<LocalModelStatus> = manager.state

    private val _engine = MutableStateFlow(AiEngine.NONE)
    val engine: StateFlow<AiEngine> = _engine.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            manager.refresh()
            _engine.value = runCatching { analyzer.engine() }.getOrDefault(AiEngine.NONE)
        }
    }

    /** [path] es el archivo ya copiado por el picker al almacenamiento de la app. */
    fun importModel(path: String?, name: String?) {
        if (path == null) return
        viewModelScope.launch {
            _busy.value = true
            val id = LocalModelCatalog.ALL
                .firstOrNull { model -> name?.contains(model.id, ignoreCase = true) == true }
                ?.id
            manager.installFromFile(path, id)
                .onSuccess { _message.value = "Modelo importado. Se usará en la próxima lectura." }
                .onFailure { _message.value = it.message ?: "No se pudo importar el modelo." }
            _engine.value = runCatching { analyzer.engine() }.getOrDefault(AiEngine.NONE)
            _busy.value = false
        }
    }

    private val _downloadingId = MutableStateFlow<String?>(null)
    val downloadingId: StateFlow<String?> = _downloadingId.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    /** Descarga el modelo dentro de la app (con barra de progreso). */
    fun download(model: dev.tubolsillo.app.domain.ai.LocalModel) {
        if (_downloadingId.value != null) return
        viewModelScope.launch {
            _downloadingId.value = model.id
            _progress.value = 0f
            manager.downloadModel(model) { downloaded, total ->
                _progress.value = if (total > 0) {
                    (downloaded.toFloat() / total).coerceIn(0f, 1f)
                } else 0f
            }
                .onSuccess { _message.value = "Modelo descargado: " + model.name }
                .onFailure { _message.value = it.message ?: "No se pudo descargar el modelo." }
            _engine.value = runCatching { analyzer.engine() }.getOrDefault(AiEngine.NONE)
            _downloadingId.value = null
            _progress.value = 0f
        }
    }

    fun removeModel() {
        viewModelScope.launch {
            _busy.value = true
            manager.remove()
            _message.value = "Modelo borrado."
            _engine.value = runCatching { analyzer.engine() }.getOrDefault(AiEngine.NONE)
            _busy.value = false
        }
    }

    /** Deja libre el espacio del .task temporal cuando el picker falla. */
    fun showMessage(text: String) {
        _message.value = text
    }

    fun clearMessage() {
        _message.value = null
    }
}
