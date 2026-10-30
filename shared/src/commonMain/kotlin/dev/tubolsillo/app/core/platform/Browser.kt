package dev.tubolsillo.app.core.platform

/** Abre una URL en el navegador de la plataforma. */
expect fun openUrl(url: String)

/**
 * Abre primero [appUrl] (un enlace profundo tipo `app://ruta`) y solo si
 * ninguna app lo atiende cae a [webUrl]. Sirve para entrar a la app nativa del
 * banco/bolsa cuando está instalada sin dejar al usuario sin salida cuando no
 * lo está.
 */
expect fun openAppOrUrl(appUrl: String, webUrl: String)
