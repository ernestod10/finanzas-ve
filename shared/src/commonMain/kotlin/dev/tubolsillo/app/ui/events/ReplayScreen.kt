package dev.tubolsillo.app.ui.events

import org.koin.compose.viewmodel.koinViewModel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.domain.model.CaptureOutcome

/**
 * Replay de depuración: pega un SMS/push real, corre el pipeline completo y
 * muestra el veredicto. Es el bucle de tuning de parsers: si un mensaje de un
 * banco no parsea, se pega aquí, se ajusta el parser y se re-prueba.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplayScreen(
    onBack: () -> Unit,
    viewModel: EventsViewModel = koinViewModel(),
) {
    var text by remember { mutableStateOf("") }
    var outcome by remember { mutableStateOf<CaptureOutcome?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Replay") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            },
        )
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Texto crudo del SMS o push") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = text.isNotBlank(),
                onClick = {
                    viewModel.replay(text) { result ->
                        outcome = result
                    }
                },
                modifier = Modifier.padding(top = 12.dp),
            ) { Text("Procesar") }

            outcome?.let { result ->
                val message = when (result) {
                    is CaptureOutcome.Parsed -> "✓ PARSED — transacción #${result.transactionId}"
                    CaptureOutcome.Duplicate -> "◌ DUPLICATE — ya existía (SMS+push del mismo evento)"
                    CaptureOutcome.Unmatched -> "✗ UNMATCHED — ningún parser reconoció el texto"
                    is CaptureOutcome.Failed -> "✗ FAILED — ${result.error}"
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (result is CaptureOutcome.Parsed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    text = "El evento crudo quedó guardado en Capturas; si es UNMATCHED, " +
                        "cópialo para afinar el parser de su banco.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
