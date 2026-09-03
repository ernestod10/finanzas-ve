package dev.tubolsillo.app.core.platform

import okio.Path
import okio.Path.Companion.toOkioPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.URLByAppendingPathComponent

actual fun dataStorePath(): Path {
    val documents = NSFileManager.defaultManager.URLForDirectory(
        NSDocumentDirectory,
        NSUserDomainMask,
        null,
        true,
        null,
    )?.path ?: error("Sin directorio de documentos")
    return (documents + "/tubolsillo_settings.preferences_pb").toOkioPath()
}
