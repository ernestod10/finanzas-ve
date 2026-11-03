package dev.tubolsillo.app.ui.classifier

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
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
import dev.tubolsillo.app.ui.components.GroupFooter
import dev.tubolsillo.app.ui.components.GroupHeader
import dev.tubolsillo.app.ui.components.GroupSeparator
import dev.tubolsillo.app.ui.components.GroupedGroup
import dev.tubolsillo.app.ui.components.IOSScreen
import org.koin.compose.viewmodel.koinViewModel

/**
 * Clasificación del historial: los movimientos viejos sin categoría se
 * proponen con el modelo y el usuario decide si se aplican.
 */
@Composable
fun ClassifierScreen(
    onBack: () -> Unit,
    viewModel: ClassifierViewModel = koinViewModel(),
) {
    val pending by viewModel.pendingCount.collectAsState()
    val proposals by viewModel.proposals.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val message by viewModel.message.collectAsState()

    IOSScreen(title = "Clasificar historial", onBack = onBack) {
        item(key = "intro") {
            GroupFooter(
                "El modelo propone una categoría para los movimientos que quedaron sin ella. " +
                    "Nada se guarda hasta que toques Aplicar, y lo que aceptes enseña reglas nuevas."
            )
        }
        item(key = "count-h") { GroupHeader("Pendientes") }
        item(key = "count") {
            GroupedGroup {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (pending == 0) "Todo está clasificado" else pending.toString() + " sin categoría",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (busy) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                    if (pending > 0 && !busy) {
                        Button(
                            onClick = { viewModel.scan() },
                            modifier = Modifier.padding(top = 10.dp),
                        ) { Text("Proponer categorías") }
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

        if (proposals.isNotEmpty()) {
            item(key = "props-h") { GroupHeader("Propuestas (" + proposals.size + ")") }
            item(key = "props") {
                GroupedGroup {
                    proposals.forEachIndexed { index, proposal ->
                        if (index > 0) GroupSeparator()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = proposal.description.take(46),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                )
                                Text(
                                    text = proposal.categoryName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
            item(key = "props-actions") {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    TextButton(onClick = { viewModel.discard() }) { Text("Descartar") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { viewModel.apply() }) {
                        Text("Aplicar " + proposals.size)
                    }
                }
            }
        }
    }
}
