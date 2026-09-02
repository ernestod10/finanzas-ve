package dev.tubolsillo.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Ajustes: cuentas, tasas y (fases siguientes) Binance, parsers, export. */
@Composable
fun SettingsScreen(
    onRatesClick: () -> Unit,
    onBinanceClick: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "Ajustes",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(16.dp),
            )
        }
        item {
            SettingsRow(
                icon = { Icon(Icons.Filled.CurrencyExchange, null) },
                title = "Tasa de cambio",
                subtitle = "Bolívares por dólar para el total en USD",
                onClick = onRatesClick,
            )
        }
        item { HorizontalDivider() }
        item {
            SettingsRow(
                icon = { Text("₿", style = MaterialTheme.typography.titleLarge) },
                title = "Binance",
                subtitle = "Conecta tu cuenta con llaves de solo lectura (próximamente)",
                onClick = onBinanceClick,
            )
        }
        item { HorizontalDivider() }
        item {
            Text(
                text = "TuBolsillo 0.1.0 — captura automática por SMS/push, CSV y más fases vienen.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun SettingsRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
}
