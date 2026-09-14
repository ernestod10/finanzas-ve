package dev.tubolsillo.app.ui.portal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tubolsillo.app.data.source.portal.PortalImportResult
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.ui.components.AppHeader
import dev.tubolsillo.app.ui.components.initialsFor
import org.koin.compose.viewmodel.koinViewModel

/**
 * Portales de banco: vincular una vez y consultar a demanda. Sin HTML crudo ni
 * URLs en pantalla (llevan identificadores de cuenta).
 */
@Composable
fun PortalScreen(
    onBack: () -> Unit,
    viewModel: PortalViewModel = koinViewModel(),
) {
    val banks by viewModel.banks.collectAsState()
    val busyKey by viewModel.busyKey.collectAsState()
    val message by viewModel.message.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(title = "Portales")
        Row(modifier = Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = onBack) { Text("Volver") }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "Inicias sesión una vez en el portal del banco y la app consulta " +
                        "tus movimientos cuando tú lo pidas. Nada se sincroniza solo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            message?.let { current ->
                item {
                    MessageCard(message = current, onDismiss = viewModel::dismissMessage)
                }
            }

            items(banks.size, key = { banks[it].institution.key }) { index ->
                val state = banks[index]
                BankPortalCard(
                    state = state,
                    busy = busyKey == state.institution.key,
                    anyBusy = busyKey != null,
                    onLink = { viewModel.link(state.institution) },
                    onSync = { viewModel.sync(state.institution) },
                    onUnlink = { viewModel.unlink(state.institution) },
                )
            }
        }
    }
}

@Composable
private fun BankPortalCard(
    state: BankPortalState,
    busy: Boolean,
    anyBusy: Boolean,
    onLink: () -> Unit,
    onSync: () -> Unit,
    onUnlink: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PortalMonogram(state.institution)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.institution.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = statusLabel(state),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                        color = when (state.status) {
                            LinkStatus.LINKED -> MaterialTheme.colorScheme.primary
                            LinkStatus.EXPIRED -> MaterialTheme.colorScheme.error
                            LinkStatus.UNLINKED -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.padding(top = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = if (state.status == LinkStatus.UNLINKED) onLink else onSync,
                    enabled = !anyBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        when (state.status) {
                            LinkStatus.UNLINKED -> "Vincular"
                            LinkStatus.LINKED -> "Consultar"
                            LinkStatus.EXPIRED -> "Reconectar"
                        }
                    )
                }
                if (state.status != LinkStatus.UNLINKED) {
                    TextButton(onClick = onLink, enabled = !anyBusy) { Text("Re-vincular") }
                    TextButton(onClick = onUnlink, enabled = !anyBusy) { Text("Quitar") }
                }
            }
        }
    }
}

/** Monograma de la institución (los logos reales no se usan: licencias). */
@Composable
private fun PortalMonogram(institution: Institution) {
    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.size(40.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialsFor(institution.displayName),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun MessageCard(message: PortalMessage, onDismiss: () -> Unit) {
    val isError = message is PortalMessage.Error
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (message) {
                    is PortalMessage.Error -> message.text
                    is PortalMessage.Info -> message.text
                    is PortalMessage.Imported -> importSummary(message.institution, message.result)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

private fun statusLabel(state: BankPortalState): String = when (state.status) {
    LinkStatus.UNLINKED -> "SIN VINCULAR"
    LinkStatus.EXPIRED -> "SESIÓN EXPIRADA"
    LinkStatus.LINKED -> "VINCULADO"
}

private fun importSummary(institution: Institution, result: PortalImportResult): String {
    val parts = mutableListOf(
        result.inserted.toString() + " movimientos nuevos",
        result.reconciled.toString() + " ya registrados",
    )
    if (result.balanceUpdated) parts.add("saldo actualizado")
    if (result.skipped > 0) parts.add(result.skipped.toString() + " filas ignoradas")
    if (result.failed > 0) parts.add(result.failed.toString() + " con error")
    return institution.displayName + ": " + parts.joinToString(" · ")
}
