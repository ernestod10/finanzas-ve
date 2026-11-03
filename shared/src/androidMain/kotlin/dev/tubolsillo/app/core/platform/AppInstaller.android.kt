package dev.tubolsillo.app.core.platform

import android.content.Context
import org.koin.core.context.GlobalContext

actual fun appVersion(): String {
    val context = GlobalContext.get().get<Context>()
    return runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()
}
