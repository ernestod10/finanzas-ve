package dev.tubolsillo.app.core.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl)
}

/**
 * `canOpenURL` solo responde que sí para los esquemas declarados en
 * `LSApplicationQueriesSchemes`; si no lo está, se abre la web y la pantalla
 * sigue siendo útil (no se pierde la acción).
 */
actual fun openAppOrUrl(appUrl: String, webUrl: String) {
    val app = UIApplication.sharedApplication
    val appUri = NSURL.URLWithString(appUrl)
    if (appUri != null && app.canOpenURL(appUri)) {
        app.openURL(appUri)
        return
    }
    NSURL.URLWithString(webUrl)?.let { app.openURL(it) }
}
