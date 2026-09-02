package dev.tubolsillo.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tubolsillo.app.domain.usecase.BackupExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupExporter: BackupExporter,
) : ViewModel() {

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    /** Escribe el respaldo JSON completo en el Uri elegido por el usuario (SAF). */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            try {
                val json = backupExporter.exportJson()
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("No se pudo abrir el destino")
                _status.value = "✓ Respaldo guardado"
            } catch (e: Exception) {
                _status.value = "✗ " + (e.message ?: "Error exportando")
            }
        }
    }
}
