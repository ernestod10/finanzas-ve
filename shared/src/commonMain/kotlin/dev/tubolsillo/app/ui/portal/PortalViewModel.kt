package dev.tubolsillo.app.ui.portal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.crypto.SecretStore
import dev.tubolsillo.app.data.source.portal.PortalDump
import dev.tubolsillo.app.data.source.portal.PortalSync
import dev.tubolsillo.app.domain.model.Institution
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado de la vinculación por portal (session-replay) para varios bancos. */
class PortalViewModel(
    private val secretStore: SecretStore,
    private val portalSync: PortalSync,
) : ViewModel() {

    /** Instituciones con portal web soportado. */
    val supported: List<Institution> =
        Institution.entries.filter { it.portalStartUrl != null && it != Institution.OTHER }

    private val _sessions = MutableStateFlow<Map<String, Pair<String, String>?>>(emptyMap())
    val sessions: StateFlow<Map<String, Pair<String, String>?>> = _sessions.asStateFlow()

    private val _dump = MutableStateFlow<PortalDump?>(null)
    val dump: StateFlow<PortalDump?> = _dump.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        _sessions.value = supported.associate { it.key to secretStore.portalSession(it.key) }
    }

    fun sessionOf(key: String): Pair<String, String>? = _sessions.value[key]

    fun refresh(key: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val d = portalSync.fetchDump(key)
                if (d == null) {
                    _error.value = "Sin sesión válida o la consulta falló. Re-vincula el portal."
                    _sessions.value = _sessions.value + (key to null)
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

    fun clear(key: String) {
        secretStore.clearPortalSession(key)
        _sessions.value = _sessions.value + (key to null)
        _dump.value = null
    }
}
