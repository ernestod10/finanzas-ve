package dev.tubolsillo.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Barra de navegación al estilo iOS: 44dp de contenido, título centrado en
 * 17/Semibold y acciones en el acento a los lados.
 *
 * Sustituye a la barra de Material que alineaba el título a la izquierda con un
 * botón pill: en iOS el título va centrado y las acciones son texto, no botones
 * rellenos. El fondo es glass, así que el contenido se ve pasar por debajo.
 */
@Composable
fun AppHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actionIcon: ImageVector? = null,
    actionLabel: String? = null,
    actionContentDescription: String? = null,
    onAction: (() -> Unit)? = null,
) {
    GlassSurface(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(44.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (onBack != null) {
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onBack)
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBackIos,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(15.dp),
                        )
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onAction != null && (actionLabel != null || actionIcon != null)) {
                    Row(
                        modifier = Modifier
                            .clickable(onClick = onAction)
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (actionIcon != null) {
                            Icon(
                                imageVector = actionIcon,
                                contentDescription = actionContentDescription,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.height(17.dp),
                            )
                            if (actionLabel != null) Spacer(Modifier.width(4.dp))
                        }
                        actionLabel?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Acción de texto para la barra, cuando se compone a mano. */
@Composable
fun RowScope.NavBarAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    )
}
