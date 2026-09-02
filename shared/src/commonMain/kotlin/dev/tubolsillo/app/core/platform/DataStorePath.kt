package dev.tubolsillo.app.core.platform

import okio.Path

/**
 * Ruta del archivo de preferencias (DataStore KMP usa okio.Path).
 * Android: filesDir. iOS (M5): NSDocumentDirectory.
 */
expect fun dataStorePath(): Path
