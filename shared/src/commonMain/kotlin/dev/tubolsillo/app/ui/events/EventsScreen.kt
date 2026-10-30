package dev.tubolsillo.app.ui.events

import org.koin.compose.viewmodel.koinViewModel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.db.entity.CaptureEventEntity
import dev.tubolsillo.app.domain.model.ParseStatus
import dev.tubolsillo.app.domain.model.SourceType
import dev.tubolsillo.app.ui.components.formatDate
import dev.tubolsillo.app.ui.components.formatTime

/**
 * Log de capturas crudas: cada SMS/push con su veredicto. Es la mina de oro
 * del tuning de parsers y la red de seguridad: el raw nunca se pierde.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    onBack: () -> Unit,
    onReplayClick: () -> Unit,
    viewModel: EventsViewModel = koinViewModel(),
) {
    val events by viewModel.events.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Capturas") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            },
            actions = {
                Text(
                    text = "Replay",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clickable(onClick = onReplayClick),
                )
            },
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (events.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    Text(
                        text = "Aún no hay capturas. Cuando llegue un SMS o push de un banco, aparecerá aquí.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            items(
                items = events,
                key = { it.id },
                contentType = { "event" },
            ) { event ->
                EventCard(event)
            }
        }
    }
}

@Composable
private fun EventCard(event: CaptureEventEntity) {
    val status = event.parseStatus
    val statusColor = when (status) {
        ParseStatus.PARSED.key -> MaterialTheme.colorScheme.primary
        ParseStatus.DUPLICATE.key -> MaterialTheme.colorScheme.secondary
        ParseStatus.UNMATCHED.key, ParseStatus.FAILED.key -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = SourceType.fromKey(event.source).displayName +
                    " · " + formatDate(event.capturedAt) + " " + formatTime(event.capturedAt) +
                    (event.sender?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = event.rawText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = status + (event.errorMessage?.let { ": $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
