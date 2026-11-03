package dev.tubolsillo.app.core.platform

import platform.Foundation.NSBundle

actual fun appVersion(): String =
    (NSBundle.mainBundle.infoDictionary?.get("CFBundleShortVersionString") as? String).orEmpty()
