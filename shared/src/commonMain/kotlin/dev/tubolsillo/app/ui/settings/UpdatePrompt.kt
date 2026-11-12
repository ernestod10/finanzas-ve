package dev.tubolsillo.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.domain.update.UpdateInfo

/** Texto plano a partir del cuerpo Markdown de la release. */
internal fun plainChangelog(notes: String?): String {
    val raw = notes?.trim().orEmpty()
    if (raw.isEmpty()) return ""
    return raw
        .lines()
        .joinToString("\n") { line ->
            line.trimStart()
                .removePrefix("### ")
                .removePrefix("## ")
                .removePrefix("# ")
                .replace("**", "")
                .replace("__", "")
                .trimEnd()
        }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

/**
 * Aviso de actualización al abrir la app: versión nueva, changelog de la
 * release y dos salidas claras. "Ahora no" pospone; "No avisar de esta
 * versión" la silencia hasta que salga la siguiente.
 */
@Composable
fun UpdatePrompt(
    info: UpdateInfo,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onSkipVersion: () -> Unit,
) {
    val changelog = plainChangelog(info.notes)
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Versión " + info.versionName + " disponible", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (changelog.isEmpty()) {
                    Text(
                        text = "Hay una versión nueva de TuBolsillo.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        text = "Novedades",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = changelog,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = "Se descarga el APK y el navegador abre el instalador.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate) { Text("Actualizar", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                TextButton(onClick = onLater) { Text("Ahora no") }
                TextButton(onClick = onSkipVersion) {
                    Text(
                        text = "No avisar de esta versión",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
