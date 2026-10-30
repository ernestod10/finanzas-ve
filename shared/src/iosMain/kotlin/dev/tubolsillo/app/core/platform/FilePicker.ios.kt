package dev.tubolsillo.app.core.platform

import androidx.compose.runtime.Composable

/**
 * Stub de pickers en iOS: UIDocumentPicker llega en M7.
 * Mientras tanto CSV y respaldo muestran un mensaje de no disponible.
 */
@Composable
actual fun rememberCsvImportPicker(onResult: FileResult): () -> Unit =
    { onResult(null, null) }

@Composable
actual fun rememberJsonImportPicker(onResult: FileResult): () -> Unit =
    { onResult(null, null) }

@Composable
actual fun rememberJsonExportPicker(defaultName: String): (String) -> Unit =
    { _ -> /* TODO(M7): UIDocumentPickerViewController */ }
