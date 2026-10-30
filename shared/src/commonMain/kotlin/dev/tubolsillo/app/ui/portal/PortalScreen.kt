package dev.tubolsillo.app.ui.portal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import dev.tubolsillo.app.data.source.portal.PortalImportResult
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.ui.components.GroupFooter
import dev.tubolsillo.app.ui.components.GroupHeader
import dev.tubolsillo.app.ui.components.GroupSeparator
import dev.tubolsillo.app.ui.components.GroupedGroup
import dev.tubolsillo.app.ui.components.IOSScreen
import dev.tubolsillo.app.ui.components.formatDate
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

    IOSScreen(
        title = "Portales",
        onBack = onBack,
    ) {
        message?.let { current ->
            item(key = "msg") {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    MessageCard(message = current, onDismiss = viewModel::dismissMessage)
                }
            }
        }

        item(key = "banks-h") { GroupHeader("Bancos disponibles") }
        item(key = "banks") {
            GroupedGroup {
                banks.forEachIndexed { index, state ->
                    BankPortalRow(
                        state = state,
                        busy = busyKey == state.institution.key,
                        anyBusy = busyKey != null,
                        onLink = { viewModel.link(state.institution) },
                        onSync = { viewModel.sync(state.institution) },
                        onUnlink = { viewModel.unlink(state.institution) },
                    )
                    if (index != banks.lastIndex) {
                        GroupSeparator(insetStart = 68)
                    }
                }
            }
        }
        item(key = "banks-f") {
            GroupFooter(
                "Inicias sesión una vez en el portal del banco (con tu 2FA) y guardas la " +
                    "página de movimientos. Luego consultas cuando tú lo pidas."
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BankPortalRow(
    state: BankPortalState,
    busy: Boolean,
    anyBusy: Boolean,
    onLink: () -> Unit,
    onSync: () -> Unit,
    onUnlink: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PortalMonogram(state.institution)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.institution.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = statusLabel(state),
                    style = MaterialTheme.typography.bodySmall,
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
        Spacer(Modifier.height(8.dp))
        // FlowRow, no Row: con letra grande o pantalla angosta las tres acciones
        // no caben y "Quitar" se partía letra por letra en vertical. Aquí baja
        // a la línea siguiente y el texto se queda legible.
        FlowRow(
            modifier = Modifier.padding(start = 52.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = if (state.status == LinkStatus.UNLINKED) onLink else onSync,
                enabled = !anyBusy,
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

/** Monograma de la institución (los logos reales no se usan: licencias). */
@Composable
private fun PortalMonogram(institution: Institution) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.size(40.dp),
    ) {
        Box(
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
        shape = RoundedCornerShape(12.dp),
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
    LinkStatus.UNLINKED -> "Sin vincular"
    LinkStatus.EXPIRED -> "Sesión expirada"
    LinkStatus.LINKED -> state.lastSyncAt?.let { "Vinculado · última consulta " + formatDate(it) } ?: "Vinculado"
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
