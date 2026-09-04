package dev.tubolsillo.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.ui.components.parseAccountColor

/** Chip pequeño con el nombre de una etiqueta de gasto. */
@Composable
fun TagChip(
    name: String,
    modifier: Modifier = Modifier,
    leadingColorHex: String? = null,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingColorHex != null) {
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = parseAccountColor(leadingColorHex) ?: MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(8.dp),
                    ) {}
                }
            }
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
