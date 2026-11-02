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

/**
 * Importa un modelo `.task` (cientos de MB). Copia el archivo al almacenamiento
 * privado de la app y devuelve su ruta; null si el usuario canceló o falló.
 */
@Composable
expect fun rememberModelImportPicker(onResult: (path: String?, name: String?) -> Unit): () -> Unit
