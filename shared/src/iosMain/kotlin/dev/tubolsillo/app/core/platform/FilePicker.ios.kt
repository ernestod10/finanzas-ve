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

/** iOS no corre MediaPipe LLM: los modelos locales son solo de Android. */
@Composable
actual fun rememberModelImportPicker(onResult: (path: String?, name: String?) -> Unit): () -> Unit =
    { onResult(null, null) }
