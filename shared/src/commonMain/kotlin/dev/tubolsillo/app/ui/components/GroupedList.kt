package dev.tubolsillo.app.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tubolsillo.app.ui.theme.TubolsilloColors

/**
 * Lista agrupada al estilo iOS: UNA superficie continua por grupo con
 * separadores sangrados, en vez de una tarjeta flotante por fila.
 *
 * Es la estructura más reconocible de iOS (`lists-and-tables.md › Style`):
 * "the grouped style uses headers, footers, and additional space to separate
 * groups of data". El radio es 10dp — no 28, que lee Material.
 */
private val GroupShape = RoundedCornerShape(10.dp)
private val GroupTopShape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
private val GroupBottomShape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
private val GroupMiddleShape = RoundedCornerShape(0.dp)

/** Forma de cada fila cuando un grupo se virtualiza fila por fila en LazyColumn. */
fun groupItemShape(isFirst: Boolean, isLast: Boolean) = when {
    isFirst && isLast -> GroupShape
    isFirst -> GroupTopShape
    isLast -> GroupBottomShape
    else -> GroupMiddleShape
}

/** Superficie para una fila individual dentro de un grupo virtualizado en LazyColumn. */
@Composable
fun GroupedItemSurface(
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = groupItemShape(isFirst, isLast),
        color = MaterialTheme.colorScheme.surface,
        content = content,
    )
}

/** Encabezado de sección: título en tinta, como la referencia. */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = 32.dp, end = 16.dp, top = 20.dp, bottom = 7.dp),
    )
}

/**
 * Encabezado de sección con una acción a la derecha: el "Ver todos" de iOS.
 * Evita gastar una fila entera de la lista en un enlace.
 */
@Composable
fun GroupHeaderAction(
    text: String,
    actionText: String,
    modifier: Modifier = Modifier,
    onAction: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 20.dp, top = 22.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = actionText,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onAction)
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        )
    }
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

/** Grupo: una tarjeta del sistema nuevo (radio 16, sombra suave, borde hairline). */
@Composable
fun GroupedGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RoundedCornerShape(16.dp),
        color = TubolsilloColors.card,
        border = BorderStroke(1.dp, TubolsilloColors.cardBorder),
        shadowElevation = if (dark) 3.dp else 6.dp,
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
