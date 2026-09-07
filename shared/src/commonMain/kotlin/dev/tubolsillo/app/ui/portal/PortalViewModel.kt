package dev.tubolsillo.app.ui.portal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.crypto.SecretStore
import dev.tubolsillo.app.data.source.portal.PortalDump
import dev.tubolsillo.app.data.source.portal.PortalSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Estado de la vinculación por portal (session-replay). */
class PortalViewModel(
    private val secretStore: SecretStore,
    private val portalSync: PortalSync,
) : ViewModel() {

    private val _session = MutableStateFlow<Pair<String, String>?>(null)
    val session: StateFlow<Pair<String, String>?> = _session

    private val _dump = MutableStateFlow<PortalDump?>(null)
    val dump: StateFlow<PortalDump?> = _dump

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        _session.value = secretStore.portalSession("MERCANTIL_PA")
    }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val d = portalSync.fetchDump("MERCANTIL_PA")
                if (d == null) {
                    _error.value = "Sin sesión válida o la consulta falló. Re-vincula el portal."
                    _session.value = null
                } else {
                    _dump.value = d
                }
            } catch (e: Exception) {
                _error.value = "Error: " + (e.message ?: "desconocido")
            } finally {
                _loading.value = false
            }
        }
    }

    fun clear() {
        secretStore.clearPortalSession("MERCANTIL_PA")
        _session.value = null
        _dump.value = null
    }
}
