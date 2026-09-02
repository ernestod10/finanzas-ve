package dev.tubolsillo.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Conexión a Binance con llaves de SOLO LECTURA creadas en binance.com
 * (API Management → Create API → marcar únicamente "Enable Reading").
 * Las llaves se guardan cifradas en el Keystore (SecretStore).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: BinanceSettingsViewModel = hiltViewModel(),
) {
    val hasKeys by viewModel.hasKeys.collectAsState()
    val status by viewModel.status.collectAsState()
    val busy by viewModel.busy.collectAsState()
    var apiKey by remember { mutableStateOf("") }
    var apiSecret by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Binance") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            if (hasKeys) {
                Text(
                    text = "✓ Conectado con llaves de solo lectura",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Sincroniza cada 15 min: saldos, depósitos y retiros. " +
                        "También actualiza la tasa de BTC.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(modifier = Modifier.padding(top = 16.dp)) {
                    Button(
                        onClick = { viewModel.syncNow() },
                        enabled = !busy,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Sincronizar ahora") }
                    OutlinedButton(onClick = { viewModel.clearKeys() }) { Text("Quitar llaves") }
                }
            } else {
                Text(
                    text = "Cómo crear llaves de solo lectura:",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "1. Entra a binance.com → API Management\n" +
                        "2. Create API → marca SOLO 'Enable Reading'\n" +
                        "3. Copia API Key y Secret Key aquí. Nunca se suben a ningún servidor: " +
                        "quedan cifradas en tu teléfono.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = apiSecret,
                    onValueChange = { apiSecret = it },
                    label = { Text("Secret Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { viewModel.saveKeys(apiKey, apiSecret) },
                        enabled = apiKey.isNotBlank() && apiSecret.isNotBlank(),
                    ) { Text("Guardar llaves") }
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(start = 12.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
            if (!hasKeys && status != null && status != "Llaves guardadas.") {
                TextButton(onClick = { viewModel.testConnection() }, enabled = !busy) {
                    Text("Probar conexión")
                }
            }
            status?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it.startsWith("✗")) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}
