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
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.platform.rememberJsonExportPicker
import dev.tubolsillo.app.core.platform.rememberPermissionActions
import dev.tubolsillo.app.domain.repo.SettingsRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Ajustes: permisos de captura, tasas, Binance, Capturas, CSV, respaldo. */
@Composable
fun SettingsScreen(
    onRatesClick: () -> Unit,
    onBinanceClick: () -> Unit,
    onEventsClick: () -> Unit,
    onCsvImportClick: () -> Unit,
    exportViewModel: ExportViewModel = koinViewModel(),
) {
    val permissions = rememberPermissionActions()
    val exportStatus by exportViewModel.status.collectAsState()
    val exportPicker = rememberJsonExportPicker("tubolsillo-backup.json")
    val settings: SettingsRepository = koinInject()
    val scope = rememberCoroutineScope()
    val darkMode by settings.darkMode.collectAsState(initial = "SYSTEM")
    var themeRow by remember { mutableStateOf(false) }
    val themeAction: (String) -> Unit = { mode ->
        scope.launch { settings.setDarkMode(mode) }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "Ajustes",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(16.dp),
            )
        }

        item {
            Text(
                text = "Apariencia",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                listOf("SYSTEM" to "Sistema", "LIGHT" to "Claro", "DARK" to "Oscuro").forEach { (mode, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = darkMode == mode,
                        onClick = { themeAction(mode) },
                        label = { Text(label) },
                    )
                }
            }
        }
        item { HorizontalDivider() }

        item {
            Text(
                text = "Captura automática",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        item {
            PermissionRow(
                title = "Leer SMS bancarios",
                subtitle = if (permissions.state.smsRead) "Activado"
                else "Necesario para capturar compras por SMS",
                enabled = permissions.state.smsRead,
                onClick = permissions.requestSms,
            )
        }
        item {
            PermissionRow(
                title = "Acceso a notificaciones",
                subtitle = if (permissions.state.notificationListener) "Activado"
                else "Lee los push de BDV, BNC, Cashea… (se activa en ajustes del sistema)",
                enabled = permissions.state.notificationListener,
                onClick = permissions.openNotificationAccessSettings,
            )
        }
        item {
            PermissionRow(
                title = "Notificaciones de la app",
                subtitle = if (permissions.state.postNotifications) "Activado"
                else "Para avisarte cuando se captura una compra",
                enabled = permissions.state.postNotifications,
                onClick = permissions.requestPostNotifications,
            )
        }
        item { HorizontalDivider() }

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
                subtitle = "Llaves de solo lectura, sync cada 15 min",
                onClick = onBinanceClick,
            )
        }
        item { HorizontalDivider() }
        item {
            SettingsRow(
                icon = { Icon(Icons.Filled.NotificationsActive, null) },
                title = "Capturas",
                subtitle = "Log crudo de SMS/push y Replay para afinar parsers",
                onClick = onEventsClick,
            )
        }
        item { HorizontalDivider() }
        item {
            SettingsRow(
                icon = { Icon(Icons.Filled.ReceiptLong, null) },
                title = "Importar CSV",
                subtitle = "Estados de cuenta de tu ebanking, con reconciliación",
                onClick = onCsvImportClick,
            )
        }
        item { HorizontalDivider() }
        item {
            SettingsRow(
                icon = { Icon(Icons.Filled.SaveAlt, null) },
                title = "Exportar respaldo",
                subtitle = "Toda tu data a un JSON (incluye capturas crudas)",
                onClick = { exportViewModel.exportWithPicker(exportPicker) },
            )
        }
        exportStatus?.let { status ->
            item {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.startsWith("✗")) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        item { HorizontalDivider() }
        item {
            Text(
                text = "TuBolsillo — tu data financiera nunca sale de tu teléfono.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (enabled) "✓" else "Configurar",
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary,
        )
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
