package dev.tubolsillo.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.platform.rememberJsonExportPicker
import dev.tubolsillo.app.core.platform.rememberJsonImportPicker
import dev.tubolsillo.app.core.platform.rememberPermissionActions
import dev.tubolsillo.app.domain.repo.SettingsRepository
import dev.tubolsillo.app.domain.repo.UpdateState
import dev.tubolsillo.app.ui.components.DisclosureChevron
import dev.tubolsillo.app.ui.components.GroupFooter
import dev.tubolsillo.app.ui.components.GroupHeader
import dev.tubolsillo.app.ui.components.GroupedGroup
import dev.tubolsillo.app.ui.components.IOSScreen
import dev.tubolsillo.app.ui.theme.AppAccent
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
    onPortalClick: () -> Unit = {},
    onReviewClick: () -> Unit = {},
    onAiModelClick: () -> Unit = {},
    onCreditsClick: () -> Unit = {},
    onClassifierClick: () -> Unit = {},
    onSavingsClick: () -> Unit = {},
    exportViewModel: ExportViewModel = koinViewModel(),
    updateViewModel: UpdateViewModel = koinViewModel(),
) {
    val permissions = rememberPermissionActions()
    val exportStatus by exportViewModel.status.collectAsState()
    // Dos pickers porque el nombre del archivo cambia si va cifrado.
    val exportPlain = rememberJsonExportPicker("tubolsillo-backup.json")
    val exportEncrypted = rememberJsonExportPicker("tubolsillo-backup.tub")
    var showExportPassword by remember { mutableStateOf(false) }
    // Contenido cifrado pendiente de contraseña para restaurar.
    var lockedImport by remember { mutableStateOf<Pair<String, String?>?>(null) }
    val importPicker = rememberJsonImportPicker { content, name ->
        if (content != null && dev.tubolsillo.app.core.crypto.isEncryptedBackup(content)) {
            lockedImport = content to name
        } else {
            exportViewModel.importBackup(content, name)
        }
    }
    val settings: SettingsRepository = koinInject()
    val scope = rememberCoroutineScope()
    val darkMode by settings.darkMode.collectAsState(initial = "SYSTEM")
    val accentKey by settings.accentColor.collectAsState(initial = "EMERALD")
    val autoSyncPortals by settings.autoSyncPortals.collectAsState()
    val appLock by settings.appLockEnabled.collectAsState()
    val currentAccent = AppAccent.fromKey(accentKey)
    val themeAction: (String) -> Unit = { mode -> scope.launch { settings.setDarkMode(mode) } }
    val accentAction: (AppAccent) -> Unit = { accent -> scope.launch { settings.setAccentColor(accent.key) } }

    IOSScreen(title = "Menú") {
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
                Separator()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Color de la app",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = currentAccent.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppAccent.entries.forEach { accent ->
                        val isSelected = accent == currentAccent
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { accentAction(accent) }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(
                                                width = 2.5.dp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                shape = CircleShape,
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(if (isSelected) 3.dp else 0.dp)
                                    .background(accent.primaryLight, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = accent.label,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Text(
                                text = accent.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }

        item(key = "captura-h") { GroupHeader("Captura automática") }
        item(key = "captura") {
            GroupedGroup {
                LinkRow(
                    Icons.Filled.RateReview,
                    Color(0xFF7A3FA0),
                    "Revisión de capturas",
                    "Lo que el parser no pudo leer",
                    onReviewClick,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { scope.launch { settings.setAutoSyncPortals(!autoSyncPortals) } }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Consultar bancos al abrir", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Saldos y movimientos de los portales con clave guardada",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = autoSyncPortals,
                        onCheckedChange = { checked -> scope.launch { settings.setAutoSyncPortals(checked) } },
                    )
                }
                Separator()
                LinkRow(Icons.Filled.NotificationsActive, Color(0xFF7A3FA0), "Capturas",
                    "Registro crudo de capturas y push, con replay", onEventsClick)
            }
        }

        item(key = "respaldo-h") { GroupHeader("Respaldo") }
        item(key = "respaldo") {
            GroupedGroup {
                LinkRow(Icons.Filled.SaveAlt, Color(0xFF3E7C74), "Exportar respaldo",
                    "Toda tu data a un JSON, incluye capturas crudas") {
                    showExportPassword = true
                }
                Separator()
                LinkRow(Icons.Filled.Restore, Color(0xFF2E5E43), "Restaurar respaldo",
                    "Importar un JSON o un respaldo cifrado") {
                    importPicker()
                }
            }
        }

        item(key = "creditos-h") { GroupHeader("Créditos") }
        item(key = "creditos") {
            GroupedGroup {
                LinkRow(Icons.Filled.Savings, Color(0xFF1F7A3D), "Objetivos de ahorro",
                    "Metas con monto y cuánto apartar por mes", onSavingsClick)
                Separator()
                LinkRow(Icons.Filled.AutoAwesome, Color(0xFF2E6E9E), "Clasificar historial",
                    "Sugiere categoría para los movimientos sin ella", onClassifierClick)
                Separator()
                LinkRow(Icons.Filled.CreditCard, Color(0xFFB3413B), "Créditos y deudas",
                    "Tarjetas, Cashea y cuotas con su fecha de pago", onCreditsClick)
            }
        }

        item(key = "binance-h") { GroupHeader("Exchange") }
        item(key = "binance") {
            GroupedGroup {
                LinkRow(null, null, "Binance",
                    "Saldos, depósitos, retiros, P2P y Binance Pay", onBinanceClick)
            }
        }

        exportStatus?.let { status ->
            item(key = "export-status") {
                GroupFooter(status)
            }
        }

        item(key = "privacidad-h") { GroupHeader("Privacidad") }
        item(key = "privacidad") {
            GroupedGroup {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { scope.launch { settings.setAppLock(!appLock) } }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bloqueo con huella o PIN", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Al volver a la app pide la huella o el PIN del teléfono",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = appLock,
                        onCheckedChange = { checked -> scope.launch { settings.setAppLock(checked) } },
                    )
                }
            }
        }

        item(key = "app-h") { GroupHeader("Aplicación") }
        item(key = "app") {
            GroupedGroup {
                LinkRow(Icons.Filled.AutoAwesome, Color(0xFF7B3DEC), "Modelo de lenguaje",
                    "Quién lee tus capturas: Gemini Nano o un modelo local", onAiModelClick)
                Separator()
                UpdateRow(updateViewModel)
            }
        }

        item(key = "footer") {
            GroupFooter("TuBolsillo — tu información financiera nunca sale del teléfono.")
        }
    }

    if (showExportPassword) {
        BackupPasswordDialog(
            title = "Exportar respaldo",
            message = "Con contraseña el archivo queda cifrado (AES-256). Sin ella es un JSON legible.",
            confirmLabel = "Cifrar y exportar",
            allowPlain = true,
            onDismiss = { showExportPassword = false },
            onConfirm = { password ->
                showExportPassword = false
                if (password == null) {
                    exportViewModel.exportWithPicker(exportPlain)
                } else {
                    exportViewModel.exportWithPicker(exportEncrypted, password)
                }
            },
        )
    }

    lockedImport?.let { pending ->
        BackupPasswordDialog(
            title = "Respaldo cifrado",
            message = "Escribe la contraseña con la que se generó este respaldo.",
            confirmLabel = "Restaurar",
            allowPlain = false,
            onDismiss = { lockedImport = null },
            onConfirm = { password ->
                lockedImport = null
                exportViewModel.importBackup(pending.first, pending.second, password)
            },
        )
    }
}

/**
 * Pide la contraseña del respaldo. Con [allowPlain] se puede elegir exportar
 * sin cifrar (el JSON de siempre).
 */
@Composable
private fun BackupPasswordDialog(
    title: String,
    message: String,
    confirmLabel: String,
    allowPlain: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.isNotBlank(),
                onClick = { onConfirm(password) },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            Row {
                if (allowPlain) {
                    TextButton(onClick = { onConfirm(null) }) { Text("Sin contraseña") }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

/**
 * Fila de versión: al tocarla busca actualizaciones en GitHub y, si hay una
 * nueva, descarga el APK y abre el instalador del sistema.
 */
@Composable
private fun UpdateRow(viewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsState()
    val (subtitle, action, busy) = when (val s = state) {
        is UpdateState.Checking -> Triple("Buscando actualizaciones…", null, true)
        is UpdateState.Installing -> Triple("Abriendo la descarga…", null, true)
        is UpdateState.UpToDate -> Triple("Estás en la última versión", { viewModel.check() }, false)
        is UpdateState.Available -> Triple(
            "Versión " + s.info.versionName + " disponible — toca para descargarla en el navegador",
            { viewModel.download(s.info) },
            false,
        )
        is UpdateState.Failed -> Triple(s.message, { viewModel.check() }, false)
        UpdateState.Idle -> Triple("Toca para buscar actualizaciones", { viewModel.check() }, false)
    }
    val highlight = state is UpdateState.Available
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = action != null) { action?.invoke() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Versión " + viewModel.currentVersion,
                style = MaterialTheme.typography.bodyLarge,
                color = if (highlight) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (highlight) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            DisclosureChevron()
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
        // Título arriba y detalle debajo: antes iban lado a lado con la mitad
        // del ancho cada uno y los títulos largos se cortaban con "…".
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
