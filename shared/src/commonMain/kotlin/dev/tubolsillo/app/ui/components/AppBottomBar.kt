package dev.tubolsillo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tubolsillo.app.ui.theme.TubolsilloColors

/** Un destino de la barra inferior. */
data class BottomBarItem(val label: String, val icon: ImageVector)

/** Acción del speed dial del FAB: icono, etiqueta corta y qué abre. */
data class QuickActionItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * Barra inferior con cuatro destinos y el FAB central de alta, como la
 * referencia. El FAB se superpone hacia arriba: no es un destino más, es la
 * acción principal de la app.
 *
 * Al tocarlo no abre una hoja: despliega un speed dial horizontal sobre el
 * botón con las altas posibles. Se cierra tocando el mismo botón (que pasa a
 * ×), tocando fuera (el scrim lo dibuja el llamador) o eligiendo una acción.
 */
@Composable
fun AppBottomBar(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    fabIndex: Int = items.size / 2,
    quickActions: List<QuickActionItem> = emptyList(),
    quickActionsOpen: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
    ) {
        Surface(
            color = TubolsilloColors.card,
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        if (index == fabIndex) Spacer(Modifier.weight(1f))
                        BottomBarTab(
                            item = item,
                            selected = selectedIndex == index,
                            onClick = { onSelect(index) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        val haptics = LocalHapticFeedback.current
        Surface(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onAdd()
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-18).dp)
                .size(56.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (quickActionsOpen) Icons.Filled.Close else Icons.Filled.Add,
                    contentDescription = if (quickActionsOpen) "Cerrar" else "Nuevo",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        if (quickActions.isNotEmpty()) {
            // Un renglón por encima del FAB: cada acción es un círculo con su
            // etiqueta debajo. Se dibuja fuera del Box de la barra (no lo
            // recorta) y entra con un deslizamiento corto desde el botón.
            AnimatedVisibility(
                visible = quickActionsOpen,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-104).dp),
                enter = fadeIn(tween(110)) + slideInVertically(
                    animationSpec = tween(160),
                    initialOffsetY = { it / 2 },
                ),
                exit = fadeOut(tween(80)) + slideOutVertically(
                    animationSpec = tween(110),
                    targetOffsetY = { it / 2 },
                ),
            ) {
                // Panel detrás de los círculos: sobre una tarjeta verde de la
                // pantalla las etiquetas quedaban ilegibles.
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = TubolsilloColors.card,
                    shadowElevation = 12.dp,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    ) {
                        quickActions.forEach { action ->
                            QuickActionBubble(action)
                        }
                    }
                }
            }
        }
    }
}

/** Círculo del speed dial con su etiqueta corta debajo. */
@Composable
private fun QuickActionBubble(action: QuickActionItem) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.width(46.dp),
    ) {
        Surface(
            onClick = action.onClick,
            shape = CircleShape,
            color = TubolsilloColors.card,
            shadowElevation = 4.dp,
            modifier = Modifier
                .size(44.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = action.label
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun BottomBarTab(
    item: BottomBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .then(
                    if (selected) {
                        Modifier.background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                        )
                    } else Modifier,
                )
                .padding(horizontal = 10.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
        )
    }
}
