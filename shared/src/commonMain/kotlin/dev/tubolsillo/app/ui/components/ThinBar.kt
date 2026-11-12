package dev.tubolsillo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Barra de avance fina. El exceso no se recorta: cambia de color y se dibuja
 * llena, porque "me pasé" es la información, no un detalle de dibujo.
 */
@Composable
fun ThinBar(
    progress: Float,
    over: Boolean,
    modifier: Modifier = Modifier,
    /** Va camino del límite (≥75 %): ámbar en vez del color de marca. */
    warning: Boolean = false,
    /** Posición 0..1 del "hoy" dentro del período, como referencia visual. */
    marker: Float? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
                shape = RoundedCornerShape(50),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(5.dp)
                .background(
                    color = when {
                        over -> MaterialTheme.colorScheme.error
                        warning -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    shape = RoundedCornerShape(50),
                ),
        )
        if (marker != null && marker in 0f..1f) {
            androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                val x = (size.width - 2.dp.toPx()) * marker
                drawRect(
                    color = Color(0xFF8A8F8B),
                    topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height),
                )
            }
        }
    }
}
