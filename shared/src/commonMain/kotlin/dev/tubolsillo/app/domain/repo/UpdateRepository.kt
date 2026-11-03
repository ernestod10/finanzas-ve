package dev.tubolsillo.app.domain.repo

import dev.tubolsillo.app.core.platform.appVersion
import dev.tubolsillo.app.core.platform.openUrl
import dev.tubolsillo.app.data.source.github.GitHubReleasesApi
import dev.tubolsillo.app.domain.update.UpdateInfo
import dev.tubolsillo.app.domain.update.isNewerVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado de la búsqueda/instalación de actualizaciones. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data object Installing : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Actualizaciones fuera de la Play Store: la release más reciente del repo de
 * GitHub es la fuente de verdad y el propio APK se descarga e instala.
 */
class UpdateRepository(
    private val api: GitHubReleasesApi,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String get() = appVersion()

    suspend fun check(): UpdateState {
        if (_state.value is UpdateState.Checking) return _state.value
        _state.value = UpdateState.Checking
        val result = api.latest().fold(
            onSuccess = { info ->
                when {
                    info == null -> UpdateState.UpToDate
                    isNewerVersion(info.versionName, currentVersion) -> UpdateState.Available(info)
                    else -> UpdateState.UpToDate
                }
            },
            onFailure = {
                UpdateState.Failed("No se pudo consultar GitHub. Revisa tu conexión.")
            },
        )
        _state.value = result
        return result
    }

    /**
     * Abre el navegador en la descarga del APK. La app no descarga ni instala
     * nada: eso es lo que Play Protect castiga (patrón dropper), y además el
     * usuario ve de dónde baja el archivo.
     */
    suspend fun download(info: UpdateInfo): UpdateState {
        openUrl(info.apkUrl)
        _state.value = UpdateState.Available(info)
        return _state.value
    }

    fun reset() {
        _state.value = UpdateState.Idle
    }
}
