package dev.tubolsillo.app.ui.portal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.platform.openPortalConnect
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.ui.components.AppHeader
import org.koin.compose.viewmodel.koinViewModel

/**
 * Conexión por portal (session-replay, beta): vincula un banco en un WebView
 * y consulta movimientos con la sesión guardada, por institución.
 */
@Composable
fun PortalScreen(
    onBack: () -> Unit,
    viewModel: PortalViewModel = koinViewModel(),
) {
    val supported by remember { mutableStateOf(viewModel.supported) }
    val sessions by viewModel.sessions.collectAsState()
    val dump by viewModel.dump.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    var selectedKey by remember { mutableStateOf(supported.firstOrNull()?.key ?: "MERCANTIL_PA") }
    val selected = Institution.fromKey(selectedKey)

    Column(modifier = Modifier.fillMaxSize()) {
        AppHeader(title = "Portal (beta)")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Inicia sesión 1 vez en el portal (tú resuelves tu 2FA) y guarda " +
                    "la sesión cifrada; la app consulta movimientos con ella.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(supported.size) { i ->
                    val inst = supported[i]
                    FilterChip(
                        selected = inst.key == selectedKey,
                        onClick = { selectedKey = inst.key },
                        label = { Text(inst.displayName, maxLines = 1) },
                    )
                }
            }

            val session = sessions[selectedKey]
            if (session == null) {
                Button(
                    onClick = { openPortalConnect(selectedKey) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.Link,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text("Abrir " + selected.displayName + " y vincular")
                }
                Text(
                    text = "Tras iniciar sesión, navega hasta la página de movimientos " +
                        "y toca 'Guardar esta página'.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "✓ Sesión guardada de " + session.second.take(70) + "…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.refresh(selectedKey) },
                        enabled = !loading,
                        modifier = Modifier.weight(1f),
                    ) { Text("Consultar ahora") }
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(start = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                OutlinedButton(
                    onClick = { viewModel.clear(selectedKey) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Quitar sesión") }
                OutlinedButton(
                    onClick = { openPortalConnect(selectedKey) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Re-vincular (si expiró)") }
            }
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            dump?.let { d ->
                Text(
                    text = "Candidatas detectadas (" + d.institutionKey + "): " + d.candidateRows.size,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                d.candidateRows.take(8).forEach { row ->
                    Text(
                        text = row,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "HTML (muestra):\n" + d.htmlSample.take(1200),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
