package dev.tubolsillo.app.ui.components.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Línea de evolución con relleno degradado debajo, sin grilla ni eje Y: el
 * número de arriba ya dice cuánto, y la forma dice cómo se movió.
 *
 * La curva se suaviza con puntos de control en la mitad horizontal de cada
 * tramo; no se interpola entre puntos reales, así que ningún dato inventado
 * queda por encima de la serie.
 */
@Composable
fun TrendChart(
    values: List<Double>,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    chartHeight: Dp = 140.dp,
) {
    if (values.size < 2) return

    // La gráfica es decorativa sin esto: se resume la serie para TalkBack.
    val description = "Serie de " + values.size + " puntos, de " +
        values.first().toString().take(8) + " a " + values.last().toString().take(8)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .semantics { contentDescription = description },
        ) {
            val min = values.min()
            val max = values.max()
            val span = (max - min).takeIf { it > 0.0 } ?: 1.0
            val verticalPad = size.height * 0.12f
            val usable = size.height - verticalPad * 2
            val steps = (values.size - 1).toFloat()

            fun x(index: Int): Float = size.width * index / steps
            fun y(value: Double): Float =
                verticalPad + usable * (1f - ((value - min) / span)).toFloat()

            val line = Path().apply {
                moveTo(x(0), y(values[0]))
                for (i in 1 until values.size) {
                    val mid = (x(i - 1) + x(i)) / 2f
                    cubicTo(mid, y(values[i - 1]), mid, y(values[i]), x(i), y(values[i]))
                }
            }

            val area = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }

            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.24f), lineColor.copy(alpha = 0f)),
                    startY = 0f,
                    endY = size.height,
                ),
            )
            drawPath(
                path = line,
                color = lineColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            drawCircle(
                color = lineColor,
                radius = 3.dp.toPx(),
                center = Offset(size.width, y(values.last())),
            )
        }

        if (xLabels.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                xLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
