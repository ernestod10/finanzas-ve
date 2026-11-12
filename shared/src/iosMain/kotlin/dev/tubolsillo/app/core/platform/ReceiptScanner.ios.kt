package dev.tubolsillo.app.core.platform

import androidx.compose.runtime.Composable

/** iOS (M5): se implementará con Vision + UIDocumentPicker. */
@Composable
actual fun rememberReceiptScanner(onResult: (text: String?, error: String?) -> Unit): () -> Unit =
    { onResult(null, "El escaneo de facturas no está disponible en iOS todavía.") }
