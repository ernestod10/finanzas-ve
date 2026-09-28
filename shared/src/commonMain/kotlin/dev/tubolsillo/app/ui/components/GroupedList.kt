package dev.tubolsillo.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lista agrupada al estilo iOS: UNA superficie continua por grupo con
 * separadores sangrados, en vez de una tarjeta flotante por fila.
 *
 * Es la estructura más reconocible de iOS (`lists-and-tables.md › Style`):
 * "the grouped style uses headers, footers, and additional space to separate
 * groups of data". El radio es 10dp — no 28, que lee Material.
 */
private val GroupShape = RoundedCornerShape(10.dp)

/** Encabezado de sección: versalitas 13sp grises, como en Ajustes de iOS. */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 32.dp, end = 16.dp, top = 22.dp, bottom = 7.dp),
    )
}

/** Pie de sección: la letra pequeña explicativa. */
@Composable
fun GroupFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 32.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
    )
}

/** Grupo: una sola superficie con esquinas 10dp y margen 16dp a los lados. */
@Composable
fun GroupedGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = GroupShape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(content = content)
    }
}

/**
 * Separador entre filas, sangrado para alinearse con el texto (no con el icono).
 * La primera y la última fila no lo llevan.
 */
@Composable
fun GroupSeparator(insetStart: Int = 16) {
    HorizontalDivider(
        modifier = Modifier.padding(start = insetStart.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    )
}

/**
 * Fila de lista agrupada. Altura mínima 44dp (el mínimo táctil de iOS,
 * `accessibility.md › Specifications`).
 */
@Composable
fun GroupRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .fillMaxWidth()
        .heightIn(min = 44.dp)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 11.dp)

    Box(modifier = base) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (leading != null) {
                Box(modifier = Modifier.padding(end = 12.dp), contentAlignment = Alignment.Center) {
                    leading()
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                content()
            }
            if (trailing != null) {
                Box(modifier = Modifier.padding(start = 10.dp), contentAlignment = Alignment.Center) {
                    trailing()
                }
            }
        }
    }
}

/** Chevron de navegación (accessory disclosure de iOS). */
@Composable
fun DisclosureChevron() {
    Text(
        text = "›",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.size(14.dp, 22.dp),
    )
}
