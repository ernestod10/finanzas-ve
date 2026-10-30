package dev.tubolsillo.app.core.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

actual fun appVersion(): String {
    val context = GlobalContext.get().get<Context>()
    return runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
}

actual suspend fun installApkFromUrl(url: String): Result<Unit> = withContext(Dispatchers.IO) {
    val context = GlobalContext.get().get<Context>()
    runCatching {
        // Desde Android 8 el sistema exige que el usuario autorice instalar
        // apps desde TuBolsillo; sin ese permiso el instalador no abre.
        if (!context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + context.packageName),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            error("Autoriza 'Instalar apps desconocidas' para TuBolsillo y vuelve a tocar Actualizar.")
        }

        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val apk = File(dir, "TuBolsillo-update.apk")

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("GitHub respondió $code al descargar la actualización.")
            connection.inputStream.use { input ->
                apk.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        if (apk.length() <= 0L) error("La descarga quedó vacía.")

        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }
}
