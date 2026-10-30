package dev.tubolsillo.app.ui.settings

import dev.tubolsillo.app.core.platform.openAppOrUrl
import dev.tubolsillo.app.core.platform.openUrl

import org.koin.compose.viewmodel.koinViewModel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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

/**
 * Conexión a Binance con llaves de SOLO LECTURA creadas en binance.com
 * (API Management → Create API → marcar únicamente "Enable Reading").
 * Las llaves se guardan cifradas en el Keystore (SecretStore).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: BinanceSettingsViewModel = koinViewModel(),
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
                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { viewModel.syncNow() },
                        enabled = !busy,
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Sincronizar ahora") }
                    OutlinedButton(
                        onClick = { viewModel.clearKeys() },
                        enabled = !busy,
                    ) { Text("Quitar llaves") }
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(start = 12.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                // Con las llaves ya guardadas también se puede volver a la
                // pantalla de la app para revisarlas o revocarlas.
                TextButton(
                    onClick = { openAppOrUrl(BINANCE_APP_API_MANAGEMENT_LINK, BINANCE_API_MANAGEMENT_URL) },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                ) { Text("Gestionar API en la app de Binance ↗") }
            } else {
                Text(
                    text = "Conectar en 3 pasos:",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "1. Toca 'Abrir Gestión de API en la app' → Crear API\n" +
                        "2. Create API → elige 'System generated' → marca SOLO 'Enable Reading'\n" +
                        "3. Pega abajo la API Key y la Secret Key.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedButton(
                    onClick = { openAppOrUrl(BINANCE_APP_API_MANAGEMENT_LINK, BINANCE_API_MANAGEMENT_URL) },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Abrir Gestión de API en la app ↗") }
                // Si la app no está instalada (o no atiende el enlace) se abre
                // la misma página en el navegador; este botón es la salida
                // manual para cuando se prefiera el navegador.
                TextButton(
                    onClick = { openUrl(BINANCE_API_MANAGEMENT_URL) },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                ) { Text("Abrir en el navegador") }
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

private const val BINANCE_API_MANAGEMENT_URL = "https://www.binance.com/en/my/settings/api-management"

/**
 * Enlace profundo a la app de Binance, verificado en dispositivo (oct-2026).
 *
 * El enrutador exige que el host sea `app.binance.com` y solo acepta rutas de
 * su lista blanca: `account/security` abre una pantalla nativa, pero
 * `api-management` y compañía caen en NoSupportRouterPathActivity. La pantalla
 * "Gestión de API" de la app es una página web dentro de su navegador interno
 * (`com.binance.hybrid.activity.BardActivity`, dominio binance.info), y el
 * router sí la abre con la acción `web?url=<url codificada>`.
 */
private const val BINANCE_APP_API_MANAGEMENT_LINK =
    "binance://app.binance.com/web?url=" +
        "https%3A%2F%2Fwww.binance.info%2Fes-LA%2Fmy%2Fsettings%2Fapi-management"
