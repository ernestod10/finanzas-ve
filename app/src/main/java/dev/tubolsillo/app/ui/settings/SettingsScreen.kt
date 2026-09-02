package dev.tubolsillo.app.ui.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Ajustes: permisos de captura, cuentas, tasas, Binance, Capturas. */
@Composable
fun SettingsScreen(
    onRatesClick: () -> Unit,
    onBinanceClick: () -> Unit,
    onEventsClick: () -> Unit,
    onCsvImportClick: () -> Unit,
) {
    val context = LocalContext.current
    var smsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    var notificationsGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 33)
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            else true
        )
    }
    var listenerEnabled by remember {
        mutableStateOf(isNotificationListenerEnabled(context))
    }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        smsGranted = granted
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "Ajustes",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(16.dp),
            )
        }

        // Permisos de captura automática.
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
                subtitle = if (smsGranted) "Activado" else "Necesario para capturar compras por SMS",
                enabled = smsGranted,
                onClick = { smsLauncher.launch(Manifest.permission.READ_SMS) },
            )
        }
        item {
            PermissionRow(
                title = "Acceso a notificaciones",
                subtitle = if (listenerEnabled) "Activado"
                else "Lee los push de BDV, BNC, Cashea… (se activa en ajustes del sistema)",
                enabled = listenerEnabled,
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    listenerEnabled = isNotificationListenerEnabled(context)
                },
            )
        }
        item {
            PermissionRow(
                title = "Notificaciones de la app",
                subtitle = if (notificationsGranted) "Activado"
                else "Para avisarte cuando se captura una compra",
                enabled = notificationsGranted,
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
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
            Text(
                text = "TuBolsillo 0.2.0 — la captura automática se afina con tus mensajes reales.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

private fun isNotificationListenerEnabled(context: android.content.Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)

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
