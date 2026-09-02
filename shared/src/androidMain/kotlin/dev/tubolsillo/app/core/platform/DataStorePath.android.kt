package dev.tubolsillo.app.core.platform

import okio.Path
import okio.Path.Companion.toOkioPath
import org.koin.core.context.GlobalContext

actual fun dataStorePath(): Path =
    GlobalContext.get().get<android.content.Context>().filesDir.resolve("tubolsillo_settings.preferences_pb").toOkioPath()
