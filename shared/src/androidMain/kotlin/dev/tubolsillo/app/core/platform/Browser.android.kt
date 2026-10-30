package dev.tubolsillo.app.core.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.koin.core.context.GlobalContext

private fun viewIntent(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

actual fun openUrl(url: String) {
    val context = GlobalContext.get().get<Context>()
    runCatching { context.startActivity(viewIntent(url)) }
}

/**
 * `startActivity` lanza ActivityNotFoundException cuando nadie reclama el
 * esquema: ese fallo es la señal para abrir la versión web.
 *
 * Con la app de destino cerrada (proceso frío) muchas apps descartan el
 * destino: el intento solo la abre por su pantalla inicial. Por eso, cuando el
 * esquema sí tiene dueño, el enlace se reenvía un par de veces en segundo
 * plano; con la app ya viva su enrutador abre la pantalla pedida. Reenviarlo
 * cuando ya funciona no molesta: vuelve a mostrar la misma pantalla.
 */
actual fun openAppOrUrl(appUrl: String, webUrl: String) {
    val context = GlobalContext.get().get<Context>()
    val opened = runCatching { context.startActivity(viewIntent(appUrl)) }.isSuccess
    if (!opened) {
        openUrl(webUrl)
        return
    }
    Thread {
        // Intentos a ~2,5 s y ~6 s del primero.
        for (waitMs in longArrayOf(2_500L, 3_500L)) {
            runCatching { Thread.sleep(waitMs) }
            runCatching { context.startActivity(viewIntent(appUrl)) }
        }
    }.apply { isDaemon = true }.start()
}
