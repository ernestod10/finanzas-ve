package dev.tubolsillo.app.core.platform

import platform.Foundation.NSBundle

actual fun appVersion(): String =
    (NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String).orEmpty()

/** iOS no instala APK: la actualización pasa por App Store o TestFlight. */
actual suspend fun installApkFromUrl(url: String): Result<Unit> =
    Result.failure(IllegalStateException("En iOS las actualizaciones llegan por la App Store."))
