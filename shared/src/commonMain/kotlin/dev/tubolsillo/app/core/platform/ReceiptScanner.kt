package dev.tubolsillo.app.core.platform

import androidx.compose.runtime.Composable

/**
 * Escáner de facturas: abre el selector de fotos del sistema, hace OCR
 * **en el teléfono** y devuelve el texto crudo del ticket. Nada sale del
 * dispositivo: no hay red ni servicio externo.
 *
 * El resultado es texto; quien lo interpreta es la capa de IA/reglas.
 */
@Composable
expect fun rememberReceiptScanner(onResult: (text: String?, error: String?) -> Unit): () -> Unit
