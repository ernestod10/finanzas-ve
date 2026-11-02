package dev.tubolsillo.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.platform.openUrl
import dev.tubolsillo.app.core.platform.rememberModelImportPicker
import dev.tubolsillo.app.domain.ai.AiEngine
import dev.tubolsillo.app.domain.ai.LocalModel
import dev.tubolsillo.app.domain.ai.LocalModelCatalog
import dev.tubolsillo.app.domain.ai.LocalModelStatus
import dev.tubolsillo.app.ui.components.GroupFooter
import dev.tubolsillo.app.ui.components.GroupHeader
import dev.tubolsillo.app.ui.components.GroupSeparator
import dev.tubolsillo.app.ui.components.GroupedGroup
import dev.tubolsillo.app.ui.components.IOSScreen
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/**
 * Modelo de lenguaje on-device. Nano lo pone el sistema; si el teléfono no lo
 * tiene, aquí se importa un `.task` (LiteRT) y la app lo usa igual.
 */
@Composable
fun AiModelScreen(
    onBack: () -> Unit,
    viewModel: AiModelViewModel = koinViewModel(),
) {
    val local by viewModel.local.collectAsState()
    val engine by viewModel.engine.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()

    val picker = rememberModelImportPicker { path, name ->
        viewModel.importModel(path, name)
    }

    IOSScreen(title = "Modelo de lenguaje", onBack = onBack) {
        item(key = "estado-h") { GroupHeader("Estado") }
        item(key = "estado") {
            GroupedGroup {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = when (engine) {
                            AiEngine.NANO -> "Gemini Nano (AICore)"
                            AiEngine.LOCAL -> "Modelo local: " + (local.installedModel?.name ?: "")
                            AiEngine.NONE -> "Sin modelo"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (engine == AiEngine.NONE) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when (engine) {
                            AiEngine.NANO -> "Es el servicio del sistema: no ocupa espacio ni RAM de TuBolsillo."
                            AiEngine.LOCAL -> "Se carga en este teléfono. Puede tardar unos segundos la primera lectura."
                            AiEngine.NONE -> "Las capturas se revisan a mano. Importa un modelo abajo para tener las funciones asistidas."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (local.totalRamGb > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "RAM del teléfono: " + formatRam(local.totalRamGb) +
                                (local.recommended?.let { " · recomendado: " + it.name } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (busy) {
                        Spacer(Modifier.height(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    message?.let { text ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        item(key = "local-h") { GroupHeader("Modelo local") }
        item(key = "local") {
            GroupedGroup {
                if (local.hasLocalModel) {
                    ModelRow(
                        title = local.installedModel?.name ?: "Modelo importado",
                        subtitle = local.installedSizeMb.toString() + " MB en el teléfono",
                        action = "Borrar",
                        onAction = viewModel::removeModel,
                        enabled = !busy,
                    )
                    GroupSeparator()
                }
                ModelRow(
                    title = "Importar archivo .task",
                    subtitle = "Descárgalo en el navegador y elígelo aquí",
                    action = "Elegir",
                    onAction = picker,
                    enabled = !busy,
                )
            }
        }
        item(key = "local-foot") {
            GroupFooter(
                "Los modelos pesan entre 500 MB y 1,5 GB. Se guardan en el almacenamiento " +
                    "privado de la app y nunca salen del teléfono."
            )
        }

        item(key = "catalogo-h") { GroupHeader("Recomendados para tu RAM") }
        item(key = "catalogo") {
            GroupedGroup {
                LocalModelCatalog.ALL.forEachIndexed { index, model ->
                    if (index > 0) GroupSeparator()
                    CatalogRow(model = model, status = local, onOpen = { openUrl(model.downloadUrl) })
                }
            }
        }
        item(key = "catalogo-foot") {
            GroupFooter(
                "Se recomienda el modelo más capaz que aguanta la RAM. Deja margen: " +
                    "el sistema y la app también necesitan memoria."
            )
        }
    }
    message?.let { viewModel.clearMessage() }
}

@Composable
private fun ModelRow(
    title: String,
    subtitle: String,
    action: String,
    onAction: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onAction, enabled = enabled) { Text(action) }
    }
}

@Composable
private fun CatalogRow(model: LocalModel, status: LocalModelStatus, onOpen: () -> Unit) {
    val isRecommended = status.recommended?.id == model.id
    val compatible = model.minRamGb <= status.totalRamGb || status.totalRamGb == 0.0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isRecommended) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = model.sizeMb.toString() + " MB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = buildString {
                append("Pide ")
                append(model.minRamGb)
                append(" GB de RAM")
                if (isRecommended) append(" · recomendado")
                if (!compatible) append(" · tu teléfono no llega")
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (isRecommended) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = model.note + if (model.requiresAccount) " (requiere iniciar sesión en Hugging Face)" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Abrir la descarga ↗",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun formatRam(gb: Double): String {
    val rounded = (gb * 10).roundToInt() / 10.0
    return if (rounded == rounded.toInt().toDouble()) rounded.toInt().toString() + " GB"
    else rounded.toString().replace('.', ',') + " GB"
}
