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

    /**
     * Exporta el respaldo. Con [password] el archivo sale cifrado (AES-GCM,
     * sobre "TUBS1:"); sin contraseña, JSON plano como antes.
     */
    fun exportWithPicker(picker: (String) -> Unit, password: String? = null) {
        viewModelScope.launch {
            _status.value = null
            runCatching {
                val json = backupExporter.exportJson()
                if (password.isNullOrBlank()) json
                else dev.tubolsillo.app.core.crypto.BackupCipher.encrypt(json, password)
            }
                .onSuccess { payload ->
                    picker(payload)
                    _status.value = if (password.isNullOrBlank()) {
                        "✓ Respaldo generado"
                    } else {
                        "✓ Respaldo cifrado generado — guarda la contraseña"
                    }
                }
                .onFailure { e -> _status.value = "✗ " + (e.message ?: "Error exportando") }
        }
    }

    /** Importa un respaldo; si viene cifrado, [password] es obligatoria. */
    fun importBackup(rawJson: String?, fileName: String?, password: String? = null) {
        if (rawJson.isNullOrBlank()) return
        viewModelScope.launch {
            _status.value = null
            runCatching {
                if (dev.tubolsillo.app.core.crypto.isEncryptedBackup(rawJson)) {
                    if (password.isNullOrBlank()) {
                        throw IllegalArgumentException("El respaldo está cifrado: falta la contraseña.")
                    }
                    dev.tubolsillo.app.core.crypto.BackupCipher.decrypt(rawJson, password)
                } else {
                    rawJson
                }
            }
                .mapCatching { json -> backupExporter.importJson(json) }
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
