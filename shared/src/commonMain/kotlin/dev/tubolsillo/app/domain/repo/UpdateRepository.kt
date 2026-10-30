package dev.tubolsillo.app.domain.repo

import dev.tubolsillo.app.core.platform.appVersion
import dev.tubolsillo.app.core.platform.installApkFromUrl
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
        val info = api.latest()
        val result = when {
            info == null -> UpdateState.Failed("No se pudo consultar GitHub. Revisa tu conexión.")
            isNewerVersion(info.versionName, currentVersion) -> UpdateState.Available(info)
            else -> UpdateState.UpToDate
        }
        _state.value = result
        return result
    }

    /** Descarga e instala; el sistema toma el control cuando el APK está listo. */
    suspend fun install(info: UpdateInfo): UpdateState {
        _state.value = UpdateState.Installing
        val next = installApkFromUrl(info.apkUrl).fold(
            onSuccess = { UpdateState.Installing },
            onFailure = { UpdateState.Failed(it.message ?: "No se pudo descargar la actualización.") },
        )
        _state.value = next
        return next
    }

    fun reset() {
        _state.value = UpdateState.Idle
    }
}
