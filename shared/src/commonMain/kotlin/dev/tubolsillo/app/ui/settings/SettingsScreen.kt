package dev.tubolsillo.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.platform.rememberJsonExportPicker
import dev.tubolsillo.app.core.platform.rememberPermissionActions
import dev.tubolsillo.app.domain.repo.SettingsRepository
import dev.tubolsillo.app.ui.components.DisclosureChevron
import dev.tubolsillo.app.ui.components.GroupFooter
import dev.tubolsillo.app.ui.components.GroupHeader
import dev.tubolsillo.app.ui.components.GroupedGroup
import dev.tubolsillo.app.ui.components.IOSScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Ajustes con la estructura de iOS Settings: grupos con encabezado en
 * versalitas, iconos en cuadro de color, chevrons y el estado a la derecha.
 */
@Composable
fun SettingsScreen(
    onRatesClick: () -> Unit,
    onBinanceClick: () -> Unit,
    onEventsClick: () -> Unit,
    onCsvImportClick: () -> Unit,
    onPortalClick: () -> Unit = {},
    exportViewModel: ExportViewModel = koinViewModel(),
) {
    val permissions = rememberPermissionActions()
    val exportStatus by exportViewModel.status.collectAsState()
    val exportPicker = rememberJsonExportPicker("tubolsillo-backup.json")
    val settings: SettingsRepository = koinInject()
    val scope = rememberCoroutineScope()
    val darkMode by settings.darkMode.collectAsState(initial = "SYSTEM")
    val themeAction: (String) -> Unit = { mode -> scope.launch { settings.setDarkMode(mode) } }

    IOSScreen(title = "Ajustes") {
        item(key = "apariencia-h") { GroupHeader("Apariencia") }
        item(key = "apariencia") {
            GroupedGroup {
                Text(
                    text = "Tema",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("SYSTEM" to "Sistema", "LIGHT" to "Claro", "DARK" to "Oscuro").forEach { (mode, label) ->
                        SegmentedOption(
                            label = label,
                            selected = darkMode == mode,
                            onClick = { themeAction(mode) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item(key = "captura-h") { GroupHeader("Captura automática") }
        item(key = "captura") {
            GroupedGroup {
                PermissionRow(
                    title = "Leer SMS bancarios",
                    subtitle = if (permissions.state.smsRead) "Activado" else "Para capturar compras por SMS",
                    done = permissions.state.smsRead,
                    onClick = permissions.requestSms,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                )
                PermissionRow(
                    title = "Acceso a notificaciones",
                    subtitle = if (permissions.state.notificationListener) "Activado"
                    else "Lee los push de BDV, BNC, Cashea",
                    done = permissions.state.notificationListener,
                    onClick = permissions.openNotificationAccessSettings,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                )
                PermissionRow(
                    title = "Notificaciones de la app",
                    subtitle = if (permissions.state.postNotifications) "Activado"
                    else "Para avisarte cuando se captura una compra",
                    done = permissions.state.postNotifications,
                    onClick = permissions.requestPostNotifications,
                )
            }
        }

        item(key = "datos-h") { GroupHeader("Datos y conexiones") }
        item(key = "datos") {
            GroupedGroup {
                LinkRow(Icons.Filled.CurrencyExchange, Color(0xFF2A7C13), "Tasa de cambio",
                    "Bolívares por dólar para el total en USD", onRatesClick)
                Separator()
                LinkRow(Icons.Filled.Link, Color(0xFF1E6FA3), "Portales de banco",
                    "Leer movimientos con la sesión vinculada", onPortalClick)
                Separator()
                LinkRow(Icons.Filled.NotificationsActive, Color(0xFF7A3FA0), "Capturas",
                    "Registro crudo de SMS y push, con replay", onEventsClick)
                Separator()
                LinkRow(Icons.Filled.ReceiptLong, Color(0xFF8A5A2B), "Importar CSV",
                    "Estados de cuenta de tu ebanking", onCsvImportClick)
            }
        }

        item(key = "respaldo-h") { GroupHeader("Respaldo") }
        item(key = "respaldo") {
            GroupedGroup {
                LinkRow(Icons.Filled.SaveAlt, Color(0xFF3E7C74), "Exportar respaldo",
                    "Toda tu data a un JSON, incluye capturas crudas") {
                    exportViewModel.exportWithPicker(exportPicker)
                }
            }
        }

        item(key = "binance-h") { GroupHeader("Exchange") }
        item(key = "binance") {
            GroupedGroup {
                LinkRow(null, null, "Binance",
                    "Llaves de solo lectura, sincronización cada 15 minutos", onBinanceClick)
            }
        }

        exportStatus?.let { status ->
            item(key = "export-status") {
                GroupFooter(status)
            }
        }

        item(key = "footer") {
            GroupFooter("TuBolsillo — tu información financiera nunca sale del teléfono.")
        }
    }
}

@Composable
private fun Separator() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    )
}

/** Fila con icono en cuadro de color, como Ajustes de iOS. */
@Composable
private fun LinkRow(
    icon: ImageVector?,
    iconColor: Color?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null && iconColor != null) {
            Box(
                modifier = Modifier
                    .size(29.dp)
                    .background(iconColor, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.1f),
        )
        Spacer(Modifier.width(6.dp))
        DisclosureChevron()
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    done: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (done) {
            Text(
                text = "Activado",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        } else {
            DisclosureChevron()
        }
    }
}

/** Control segmentado: la forma en que iOS ofrece 2-4 opciones excluyentes. */
@Composable
private fun SegmentedOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = 32.dp)
            .background(
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}
