package dev.tubolsillo.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.model.RateSource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Editor de la tasa VES→USD con fetch automático desde el endpoint
 * (BCV oficial o P2P Binance, vía Lambda tubolsillo-tasas).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatesScreen(
    onBack: () -> Unit,
    viewModel: RatesViewModel = koinViewModel(),
) {
    val vesPerDollar by viewModel.vesPerDollar.collectAsState()
    val status by viewModel.status.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val lastFetch by viewModel.lastFetch.collectAsState()
    var rateText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Tasa de cambio") },
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
            Text(
                text = "Bolívares por dólar",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Tasa actual: " + MoneyFormatter.formatMinor(
                    (vesPerDollar * 100).toLong(), "VES",
                ) + " por $ 1,00",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            // --- Fetch automático ---
            Text(
                text = "TRAER AUTOMÁTICA",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
            )
            if (!busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.fetchAndApply(RateSource.BCV) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Usar BCV oficial") }
                    OutlinedButton(
                        onClick = { viewModel.fetchAndApply(RateSource.PARALLEL) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Usar P2P Binance") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp), strokeWidth = 2.dp)
                    Text("Consultando tasas…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            lastFetch?.let { dto ->
                Text(
                    text = "BCV: " + fmt(dto.bcv_usd_bs) + " · P2P: " + fmt(dto.p2p_usdt_ves) +
                        (dto.fecha_valor_bcv?.let { " · " + it } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            status?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (it.startsWith("✗")) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // --- Manual ---
            Text(
                text = "O MANUAL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text("Nueva tasa (Bs. por $)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    enabled = rateText.isNotBlank(),
                    onClick = { viewModel.saveVesRate(rateText); rateText = "" },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Guardar") }
            }
            Text(
                text = "BCV y P2P vienen del estado del bot binancep2p en S3 (actualizado por cron); " +
                    "la tasa la usas para convertir tus cuentas en bolívares a USD.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private fun fmt(v: Double?): String =
    v?.let { MoneyFormatter.formatMinor((it * 100).toLong(), "VES") } ?: "—"
