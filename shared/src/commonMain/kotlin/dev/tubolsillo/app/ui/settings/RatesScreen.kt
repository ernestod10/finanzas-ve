package dev.tubolsillo.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import org.koin.compose.viewmodel.koinViewModel

/** Etiquetas de la fuente de tasa. */
/** Símbolo de la divisa que define la fuente. */
private fun sourceSymbol(source: String): String = when (source) {
    "EUR_BCV" -> "€"
    "P2P" -> "1 USDT"
    else -> "$ 1"
}

private fun sourceLabel(source: String): String = when (source) {
    "EUR_BCV" -> "Euro (BCV)"
    "P2P" -> "Tasa USDT (prom. venta 100)"
    "MANUAL" -> "Manual"
    else -> "BCV oficial"
}

/**
 * Editor de la tasa VES: dropdown para elegir la fuente y botón
 * "Actualizar tasa" que consulta el endpoint y aplica la seleccionada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatesScreen(
    onBack: () -> Unit,
    viewModel: RatesViewModel = koinViewModel(),
) {
    val vesPerDollar by viewModel.vesPerDollar.collectAsState()
    val rateSource by viewModel.rateSource.collectAsState()
    val status by viewModel.status.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val lastFetch by viewModel.lastFetch.collectAsState()
    var rateText by remember { mutableStateOf("") }
    var dropdownOpen by remember { mutableStateOf(false) }

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
                text = "Tasa en uso: " + MoneyFormatter.formatMinor(
                    (vesPerDollar * 100).toLong(), "VES",
                ) + " por " + sourceSymbol(rateSource) + "  ·  " + sourceLabel(rateSource),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            // --- Dropdown de fuente + botón actualizar ---
            Text(
                text = "FUENTE DE LA TASA",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
            )
            ExposedDropdownMenuBox(
                expanded = dropdownOpen,
                onExpandedChange = { dropdownOpen = it },
            ) {
                OutlinedTextField(
                    value = sourceLabel(rateSource),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownOpen) },
                    singleLine = true,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = dropdownOpen,
                    onDismissRequest = { dropdownOpen = false },
                ) {
                    listOf("BCV", "EUR_BCV", "P2P", "MANUAL").forEach { src ->
                        DropdownMenuItem(
                            text = { Text(sourceLabel(src)) },
                            onClick = {
                                viewModel.selectSource(src)
                                dropdownOpen = false
                            },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { viewModel.refresh() },
                    enabled = !busy && rateSource != "MANUAL",
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Actualizar tasa")
                }
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(start = 12.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
            if (rateSource == "MANUAL") {
                Text(
                    text = "Con fuente Manual el botón queda desactivado: define la tasa abajo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            lastFetch?.let { dto ->
                Text(
                    text = "Última consulta — Bs/$: " + fmt(dto.bcv_usd_bs) +
                        " · Bs/€: " + fmt(dto.bcv_eur_bs) +
                        " · USDT: " + fmt(dto.p2p_usdt_ves) +
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
                OutlinedButton(
                    enabled = rateText.isNotBlank(),
                    onClick = { viewModel.saveVesRate(rateText); rateText = "" },
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Guardar") }
            }
            Text(
                text = "BCV (Bs/$ y Bs/€) y Tasa USDT (promedio de venta para 100 USDT) vienen del estado del bot binancep2p en S3.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private fun fmt(v: Double?): String =
    v?.let { MoneyFormatter.formatMinor((it * 100).toLong(), "VES") } ?: "—"
