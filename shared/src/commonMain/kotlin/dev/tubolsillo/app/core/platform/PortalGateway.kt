package dev.tubolsillo.app.core.platform

import dev.tubolsillo.app.data.source.portal.PortalPayload

/**
 * Puente hacia el navegador del portal. La implementación es Android
 * (AndroidPortalGateway + PortalWebActivity) y se registra en Koin, igual que
 * CaptureNotifier. Nunca se invoca sola: siempre desde un botón del usuario.
 */
interface PortalGateway {
    /** Modo LINK: WebView visible; el usuario inicia sesión y guarda la página. */
    suspend fun link(institutionKey: String): PortalLinkResult

    /** Modo FETCH: abre la URL guardada y ejecuta la receta. */
    suspend fun fetch(institutionKey: String, url: String): PortalFetchResult
}

sealed interface PortalLinkResult {
    data class Linked(val movementsUrl: String, val payload: PortalPayload? = null) : PortalLinkResult
    data object Cancelled : PortalLinkResult
    data class Failed(val reason: String) : PortalLinkResult
}

sealed interface PortalFetchResult {
    data class Success(val payload: PortalPayload, val url: String) : PortalFetchResult

    /** Salió el formulario de login y el usuario no completó la sesión. */
    data object NeedsRelink : PortalFetchResult
    data object Cancelled : PortalFetchResult
    data class Failed(val reason: String) : PortalFetchResult
}
