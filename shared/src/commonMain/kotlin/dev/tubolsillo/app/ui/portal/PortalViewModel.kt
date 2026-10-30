@file:OptIn(kotlin.time.ExperimentalTime::class)

package dev.tubolsillo.app.ui.portal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.crypto.PortalLink
import dev.tubolsillo.app.core.crypto.SecretStore
import dev.tubolsillo.app.core.platform.PortalFetchResult
import dev.tubolsillo.app.core.platform.PortalGateway
import dev.tubolsillo.app.core.platform.PortalLinkResult
import dev.tubolsillo.app.core.time.nowMillis
import dev.tubolsillo.app.data.source.portal.PortalImportResult
import dev.tubolsillo.app.data.source.portal.PortalImporter
import dev.tubolsillo.app.data.source.portal.PortalRecipes
import dev.tubolsillo.app.data.source.portal.PortalRowMapper
import dev.tubolsillo.app.domain.model.Institution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estado de vinculación de un banco. */
enum class LinkStatus { UNLINKED, LINKED, EXPIRED }

data class BankPortalState(
    val institution: Institution,
    val status: LinkStatus,
    val lastSyncAt: Long?,
)

sealed interface PortalMessage {
    data class Imported(val institution: Institution, val result: PortalImportResult) : PortalMessage
    data class Info(val text: String) : PortalMessage
    data class Error(val text: String) : PortalMessage
}

/**
 * Portales por banco: vincular una vez, consultar cuando el usuario lo pida.
 * Nada corre solo: no hay refrescos automáticos ni trabajo en segundo plano.
 */
class PortalViewModel(
    private val secretStore: SecretStore,
    private val gateway: PortalGateway,
    private val importer: PortalImporter,
) : ViewModel() {

    private val supported: List<Institution> =
        Institution.entries.filter { it.portalStartUrl != null && it != Institution.OTHER }

    private val _banks = MutableStateFlow<List<BankPortalState>>(
        supported.map { BankPortalState(it, LinkStatus.UNLINKED, null) }
    )
    val banks: StateFlow<List<BankPortalState>> = _banks.asStateFlow()

    private val _busyKey = MutableStateFlow<String?>(null)
    val busyKey: StateFlow<String?> = _busyKey.asStateFlow()

    private val _message = MutableStateFlow<PortalMessage?>(null)
    val message: StateFlow<PortalMessage?> = _message.asStateFlow()

    init {
        reload()
    }

    /** Relee el estado guardado fuera del hilo principal para no frenar la transición. */
    fun reload() {
        viewModelScope.launch {
            _banks.value = withContext(Dispatchers.Default) {
                supported.map { institution ->
                    val rawLink = secretStore.portalLink(institution.key)
                    val link = rawLink?.takeUnless { isDeprecatedPortalUrl(it.movementsUrl) }
                    if (rawLink != null && link == null) {
                        secretStore.clearPortalLink(institution.key)
                    }
                    BankPortalState(
                        institution = institution,
                        status = if (link == null) LinkStatus.UNLINKED else LinkStatus.LINKED,
                        lastSyncAt = link?.lastSyncAtMs,
                    )
                }
            }
        }
    }

    private fun isDeprecatedPortalUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("banesconline.banesco.com") ||
            lower.contains("banesconecta.banesco.com") ||
            lower.contains("bdvenlinea.bancodevenezuela.com") ||
            lower.contains("bnconline.com.ve") ||
            lower.contains("www.todoticket.com")
    }

    fun link(institution: Institution) {
        if (_busyKey.value != null) return
        viewModelScope.launch {
            _busyKey.value = institution.key
            _message.value = null
            when (val result = gateway.link(institution.key)) {
                is PortalLinkResult.Linked -> {
                    val now = nowMillis()
                    val recipe = PortalRecipes.forInstitution(institution)
                    val imported = if (result.payload != null && recipe != null) {
                        val mapped = PortalRowMapper.map(result.payload, recipe, now)
                        importer.import(recipe, mapped)
                    } else null
                    secretStore.savePortalLink(
                        institution.key,
                        PortalLink(
                            movementsUrl = result.movementsUrl,
                            linkedAtMs = now,
                            lastSyncAtMs = if (imported != null) now else null,
                        ),
                    )
                    _message.value = if (imported != null && (imported.inserted > 0 || imported.balanceUpdated)) {
                        PortalMessage.Imported(institution, imported)
                    } else {
                        PortalMessage.Info(institution.displayName + " vinculado.")
                    }
                }
                is PortalLinkResult.Failed ->
                    _message.value = PortalMessage.Error(result.reason)
                PortalLinkResult.Cancelled -> Unit
            }
            _busyKey.value = null
            reload()
        }
    }

    fun sync(institution: Institution) {
        if (_busyKey.value != null) return
        val link = secretStore.portalLink(institution.key)
        if (link == null) {
            _message.value = PortalMessage.Error("Vincula " + institution.displayName + " primero.")
            return
        }
        val recipe = PortalRecipes.forInstitution(institution)
        if (recipe == null) {
            _message.value = PortalMessage.Error("Sin receta de extracción para este banco.")
            return
        }
        viewModelScope.launch {
            _busyKey.value = institution.key
            _message.value = null
            when (val result = gateway.fetch(institution.key, link.movementsUrl)) {
                is PortalFetchResult.Success -> {
                    val mapped = PortalRowMapper.map(result.payload, recipe, nowMillis())
                    val imported = importer.import(recipe, mapped)
                    secretStore.savePortalLink(
                        institution.key,
                        link.copy(lastSyncAtMs = nowMillis()),
                    )
                    _message.value = PortalMessage.Imported(institution, imported)
                }
                PortalFetchResult.NeedsRelink -> {
                    _message.value = PortalMessage.Error(
                        "La sesión de " + institution.displayName + " expiró. Vincula de nuevo."
                    )
                    markExpired(institution)
                }
                is PortalFetchResult.Failed ->
                    _message.value = PortalMessage.Error(result.reason)
                PortalFetchResult.Cancelled -> Unit
            }
            _busyKey.value = null
            reload()
        }
    }

    fun unlink(institution: Institution) {
        secretStore.clearPortalLink(institution.key)
        secretStore.clearPortalCredentials(institution.key)
        _message.value = PortalMessage.Info(
            "Se borró la vinculación y las credenciales de " + institution.displayName + "."
        )
        reload()
    }

    fun dismissMessage() {
        _message.value = null
    }

    private fun markExpired(institution: Institution) {
        _banks.value = _banks.value.map { state ->
            if (state.institution == institution) state.copy(status = LinkStatus.EXPIRED) else state
        }
    }
}
