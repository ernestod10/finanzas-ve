package dev.tubolsillo.app.core.platform

/** Versión instalada ("0.1.0"), la que se compara contra la de GitHub. */
expect fun appVersion(): String

/**
 * Descarga el APK de [url] y abre el instalador del sistema. Devuelve error
 * con un mensaje mostrable cuando falta el permiso de "instalar apps
 * desconocidas" o cuando la descarga falla; en iOS siempre falla (la app se
 * actualiza por App Store o TestFlight, no por APK).
 */
expect suspend fun installApkFromUrl(url: String): Result<Unit>
