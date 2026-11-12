package dev.tubolsillo.app.ui.classifier

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.money.MoneyFormatter
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
    val categories by viewModel.categories.collectAsState()
    var editing by remember { mutableStateOf<ClassProposal?>(null) }

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
                                .clickable { editing = proposal }
                                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = proposal.merchantType ?: proposal.description.take(46),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = proposal.categoryName + " · " +
                                        MoneyFormatter.formatMinor(proposal.amountMinor, proposal.currency),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = { viewModel.remove(proposal.txId) }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Quitar " + proposal.description,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
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

    // Tocar una propuesta abre el selector: la categoría se puede corregir
    // antes de aplicar y solo así se guarda cuando el usuario confirme.
    editing?.let { proposal ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Categoría para " + (proposal.merchantType ?: proposal.description.take(40))) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    categories.forEach { category ->
                        val selected = category.id == proposal.categoryId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.recategorize(proposal.txId, category.id)
                                    editing = null
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Actual",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editing = null }) { Text("Cerrar") }
            },
        )
    }
}
