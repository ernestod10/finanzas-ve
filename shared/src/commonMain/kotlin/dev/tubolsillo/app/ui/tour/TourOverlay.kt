package dev.tubolsillo.app.ui.tour

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Tour de primer uso con "dimming zones": el fondo se oscurece y solo queda
 * iluminado el botón del que se habla. Tres paradas — el "+", la barra de
 * destinos y cómo se vinculan las cuentas del banco.
 */
@Composable
fun TourOverlay(
    anchors: TourAnchors,
    onDone: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    val accent = MaterialTheme.colorScheme.primary

    val spotlight: Rect? = when (step) {
        0 -> anchors.fab
        1 -> anchors.bar
        else -> null
    }
    val padding = 10.dp

    val title = when (step) {
        0 -> "Todo empieza en el +"
        else -> if (step == 1) "Cuatro destinos" else "Tu banco se conecta solo"
    }
    val body = when (step) {
        0 -> "Registra un gasto, un ingreso, una factura escaneada, una cuenta, una " +
            "tarjeta o un objetivo de ahorro."
        1 -> "Inicio, Registros, Estadísticas y Menú. Desliza a los lados o toca la " +
            "pestaña para cambiar de pantalla."
        else -> "Cuando vinculas el portal de tu banco (Menú → Portales), la app busca " +
            "la cuenta por sus últimos dígitos y usa esa; si todavía no existe, la crea " +
            "con el nombre y la moneda que publica el banco. También aplica a los SMS y " +
            "notificaciones del banco."
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim + hueco. Se dibuja en cuatro rectángulos alrededor del objetivo:
        // no usa BlendMode.Clear, que necesita capa propia y falla en algunos
        // fabricantes dentro de un Box normal.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scrim = Color.Black.copy(alpha = 0.74f)
            val hole = spotlight?.inflate(padding.toPx())
            if (hole == null) {
                drawRect(scrim)
            } else {
                val clampedTop = hole.top.coerceIn(0f, size.height)
                val clampedBottom = hole.bottom.coerceIn(0f, size.height)
                val clampedLeft = hole.left.coerceIn(0f, size.width)
                val clampedRight = hole.right.coerceIn(0f, size.width)
                drawRect(scrim, size = Size(size.width, clampedTop))
                drawRect(
                    scrim,
                    topLeft = Offset(0f, clampedBottom),
                    size = Size(size.width, (size.height - clampedBottom).coerceAtLeast(0f)),
                )
                drawRect(
                    scrim,
                    topLeft = Offset(0f, clampedTop),
                    size = Size(clampedLeft, (clampedBottom - clampedTop).coerceAtLeast(0f)),
                )
                drawRect(
                    scrim,
                    topLeft = Offset(clampedRight, clampedTop),
                    size = Size(
                        (size.width - clampedRight).coerceAtLeast(0f),
                        (clampedBottom - clampedTop).coerceAtLeast(0f),
                    ),
                )
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(clampedLeft, clampedTop),
                    size = Size(
                        (clampedRight - clampedLeft).coerceAtLeast(1f),
                        (clampedBottom - clampedTop).coerceAtLeast(1f),
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f, 22f),
                    style = Stroke(width = 3f),
                )
            }
        }

        // Tocar fuera de la tarjeta avanza; así el tour no se siente atascado.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (step < 2) step++ else onDone()
                },
        )

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 14.dp,
            modifier = Modifier
                .align(if (step == 1) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = if (step == 1) 120.dp else 0.dp,
                    bottom = if (step == 1) 0.dp else 190.dp,
                ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDone) { Text("Saltar") }
                    Box(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { if (step < 2) step++ else onDone() },
                    ) { Text(if (step < 2) "Siguiente" else "Entendido") }
                }
            }
        }
    }
}

private fun Rect.inflate(px: Float): Rect =
    Rect(left - px, top - px, right + px, bottom + px)
