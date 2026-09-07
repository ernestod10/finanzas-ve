package dev.tubolsillo.app.ui.portal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.platform.openPortalConnect
import dev.tubolsillo.app.ui.components.AppHeader
import org.koin.compose.viewmodel.koinViewModel

/**
 * Conexión por portal (session-replay, beta): vincula el banco en un WebView
 * y consulta movimientos con la sesión guardada. Solo Mercantil Panamá por ahora.
 */
@Composable
fun PortalScreen(
    onBack: () -> Unit,
    viewModel: PortalViewModel = koinViewModel(),
) {
    val session by viewModel.session.collectAsState()
    val dump by viewModel.dump.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

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
                text = "Mercantil Panamá — lee movimientos iniciando sesión 1 vez " +
                    "en el portal (tú resuelves tu 2FA) y guardando la sesión cifrada.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val s = session
            if (s == null) {
                Button(
                    onClick = { openPortalConnect("MERCANTIL_PA") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.Link,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text("Abrir portal y vincular")
                }
                Text(
                    text = "Tras iniciar sesión, navega hasta la página de movimientos " +
                        "y toca 'Guardar esta página'.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "✓ Sesión guardada de " + s.second.take(60) + "…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.refresh() },
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
                    onClick = { viewModel.clear(); onBack() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Quitar sesión") }
                OutlinedButton(
                    onClick = { openPortalConnect("MERCANTIL_PA") },
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
                    text = "Candidatas detectadas: " + d.candidateRows.size,
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
                    text = "HTML (muestra):\n" + d.htmlSample.take(1500),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
