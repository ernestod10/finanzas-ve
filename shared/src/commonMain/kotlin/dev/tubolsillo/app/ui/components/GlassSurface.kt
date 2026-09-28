package dev.tubolsillo.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Interruptor global del material. Se puede forzar desde Ajustes ("Reducir
 * transparencia") sin tocar cada barra: cuando está activo, el glass cae a
 * relleno opaco, que es la respuesta que pide el guide para esa preferencia.
 */
object TubolsilloGlass {
    var reduceTransparency: Boolean = false
}

/**
 * Material de la capa funcional (Liquid Glass aproximado).
 *
 * Regla de las HIG: el glass vive SOLO en la capa funcional — barras, hojas,
 * diálogos — nunca en el contenido. Si se usa sobre una tarjeta o una fila, es
 * un defecto (`liquid-glass.md › Review checklist`, punto 1).
 *
 * Aproximación realista en Compose: iOS logra el efecto desenfocando lo que hay
 * DETRÁS de la barra. Android no expone un backdrop blur para vistas dentro de
 * una Activity, así que aquí se compone con lo que sí es fiel: relleno
 * translúcido (el contenido se ve pasar por debajo), borde de brillo hairline
 * en el canto y ausencia de sombra proyectada. Las sombras no son el material.
 *
 * Accesibilidad: con texto de alto contraste activado cae a relleno opaco, que
 * es la señal que recomienda el guide cuando no hay un ajuste de "reducir
 * transparencia" disponible.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(0.dp),
    tonal: Color = MaterialTheme.colorScheme.surface,
    reduceTransparency: Boolean = TubolsilloGlass.reduceTransparency,
    content: @Composable () -> Unit,
) {
    val fill = if (reduceTransparency) tonal else tonal.copy(alpha = 0.78f)
    val highlight = if (reduceTransparency) {
        MaterialTheme.colorScheme.outline
    } else {
        Color.White.copy(alpha = 0.28f)
    }
    Surface(
        modifier = modifier,
        shape = shape,
        color = fill,
        border = BorderStroke(0.5.dp, highlight),
        content = { Box(content = { content() }) },
    )
}
