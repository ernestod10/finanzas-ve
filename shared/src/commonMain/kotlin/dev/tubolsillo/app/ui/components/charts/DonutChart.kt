package dev.tubolsillo.app.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.ui.theme.TNUM

/** Un segmento de la dona. */
data class DonutSlice(
    val label: String,
    val value: Double,
    val color: Color,
)

/**
 * Dona de composición con el dato principal en el centro. Los segmentos se
 * separan con un hueco de 2° para que dos colores contiguos no se fundan.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    centerTitle: String?,
    centerValue: String?,
    modifier: Modifier = Modifier,
    centerDelta: String? = null,
    centerDeltaColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    diameter: Dp = 176.dp,
    strokeWidth: Dp = 26.dp,
) {
    val total = slices.sumOf { it.value }
    if (total <= 0.0) return

    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            var startAngle = -90f

            slices.forEach { slice ->
                val sweep = (slice.value / total * 360.0).toFloat()
                // Hueco de 2° repartido entre los dos bordes del segmento.
                val gap = if (slices.size > 1) 2f else 0f
                drawArc(
                    color = slice.color,
                    startAngle = startAngle + gap / 2f,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            centerTitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
            centerValue?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = TNUM,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
            centerDelta?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TNUM),
                    color = centerDeltaColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
