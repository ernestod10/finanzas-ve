package dev.tubolsillo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.domain.usecase.BackupExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Respaldo JSON: produce el contenido y lo entrega al picker de la plataforma. */
class ExportViewModel(
    private val backupExporter: BackupExporter,
) : ViewModel() {

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    fun exportWithPicker(picker: (String) -> Unit) {
        viewModelScope.launch {
            _status.value = null
            runCatching { backupExporter.exportJson() }
                .onSuccess { json ->
                    picker(json)
                    _status.value = "✓ Respaldo generado"
                }
                .onFailure { e -> _status.value = "✗ " + (e.message ?: "Error exportando") }
        }
    }

    fun importBackup(rawJson: String?, fileName: String?) {
        if (rawJson.isNullOrBlank()) return
        viewModelScope.launch {
            _status.value = null
            runCatching { backupExporter.importJson(rawJson) }
                .onSuccess { s ->
                    _status.value = "✓ Restaurado (${fileName ?: "JSON"}): " +
                        "${s.accounts} cuentas, ${s.transactions} movimientos, ${s.planItems} líneas del plan"
                }
                .onFailure { e ->
                    _status.value = "✗ Error restaurando respaldo: " + (e.message ?: "archivo inválido")
                }
        }
    }
}
