package dev.tubolsillo.app.core.platform

import androidx.compose.runtime.Composable

/** Callback genérico de selección de archivo: null si el usuario canceló. */
typealias FileResult = (content: String?, name: String?) -> Unit

/**
 * Pickers de archivos de la plataforma, como composables expect.
 * Android: SAF (OpenDocument / CreateDocument). iOS (M5): UIDocumentPicker.
 */
@Composable
expect fun rememberCsvImportPicker(onResult: FileResult): () -> Unit

@Composable
expect fun rememberJsonImportPicker(onResult: FileResult): () -> Unit

@Composable
expect fun rememberJsonExportPicker(defaultName: String): (String) -> Unit
