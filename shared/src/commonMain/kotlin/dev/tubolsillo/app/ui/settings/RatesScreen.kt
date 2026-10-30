package dev.tubolsillo.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.ui.components.GroupedGroup
import dev.tubolsillo.app.ui.components.HeaderAction
import dev.tubolsillo.app.ui.components.IOSScreen
import dev.tubolsillo.app.ui.theme.TNUM
import org.koin.compose.viewmodel.koinViewModel

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
 * Editor de la tasa VES + monitor de brecha cambiaria BCV vs USDT +
 * calculadora rápida de conversión para Pago Móvil.
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

    // Calculadora rápida
    var calcAmountText by remember { mutableStateOf("20") }
    var calcUnit by remember { mutableStateOf("USD_BCV") } // USD_BCV | EUR_BCV | USDT | VES
    var copiedMsg by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    val bcvUsd = lastFetch?.bcv_usd_bs ?: vesPerDollar
    val bcvEur = lastFetch?.bcv_eur_bs ?: (bcvUsd * 1.08)
    val p2pUsdt = lastFetch?.p2p_usdt_ves ?: vesPerDollar
    val spreadPct = if (bcvUsd > 0 && p2pUsdt > 0) ((p2pUsdt - bcvUsd) / bcvUsd) * 100.0 else 0.0

    IOSScreen(
        title = "Tasas y Calculadora",
        onBack = onBack,
        actions = {
            if (!busy && rateSource != "MANUAL") {
                HeaderAction("Actualizar") { viewModel.refresh() }
            }
        },
    ) {
        item(key = "rates-content") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = "Tasa activa: " + MoneyFormatter.formatMinor(
                        (vesPerDollar * 100).toLong(), "VES",
                    ) + " por " + sourceSymbol(rateSource) + "  ·  " + sourceLabel(rateSource),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

            // Tarjeta de referencia y brecha cambiaria
            lastFetch?.let { dto ->
                Spacer(Modifier.height(14.dp))
                GroupedGroup {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("BCV Dólar ($)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                fmt(dto.bcv_usd_bs),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFeatureSettings = TNUM,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("BCV Euro (€)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                fmt(dto.bcv_eur_bs),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFeatureSettings = TNUM,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Binance P2P (1 USDT)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                fmt(dto.p2p_usdt_ves),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFeatureSettings = TNUM,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                        if (dto.bcv_usd_bs != null && dto.p2p_usdt_ves != null && dto.bcv_usd_bs > 0) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            )
                            val pctRounded = kotlin.math.round(spreadPct * 10.0) / 10.0
                            val usdtFor100Bcv = kotlin.math.round((100.0 * dto.bcv_usd_bs / dto.p2p_usdt_ves) * 100.0) / 100.0
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "Brecha BCV vs USDT",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "${if (pctRounded >= 0) "+" else ""}$pctRounded%  ($100 BCV = $usdtFor100Bcv USDT)",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFeatureSettings = TNUM,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            // --- Calculadora rápida de Pago Móvil / Divisas ---
            Text(
                text = "CALCULADORA RÁPIDA (PAGO MÓVIL / BRECHA)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
            )
            GroupedGroup {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "USD_BCV" to "$ BCV",
                            "EUR_BCV" to "€ BCV",
                            "USDT" to "USDT",
                            "VES" to "Bs",
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = calcUnit == key,
                                onClick = { calcUnit = key },
                                label = { Text(label) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = calcAmountText,
                        onValueChange = { calcAmountText = it },
                        label = { Text("Monto a convertir") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val inputMinor = AmountNormalizer.parseMinor(calcAmountText, "USD") ?: 0L
                    val inputMajor = inputMinor / 100.0
                    val totalVes = when (calcUnit) {
                        "USD_BCV" -> inputMajor * bcvUsd
                        "EUR_BCV" -> inputMajor * bcvEur
                        "USDT" -> inputMajor * p2pUsdt
                        else -> inputMajor
                    }
                    val equivUsdBcv = if (bcvUsd > 0) totalVes / bcvUsd else 0.0
                    val equivUsdt = if (p2pUsdt > 0) totalVes / p2pUsdt else 0.0
                    val vesMinor = kotlin.math.round(totalVes * 100.0).toLong()
                    val plainVesForCopy = run {
                        val whole = vesMinor / 100
                        val cents = kotlin.math.abs(vesMinor % 100)
                        "$whole,${cents.toString().padStart(2, '0')}"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Monto en Bolívares",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = MoneyFormatter.formatMinor(vesMinor, "VES"),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFeatureSettings = TNUM,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        TextButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(plainVesForCopy))
                                copiedMsg = "✓ Copiado: $plainVesForCopy Bs"
                            },
                        ) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                            Text("Copiar Bs")
                        }
                    }
                    Text(
                        text = "Equivale a " + MoneyFormatter.formatUsd(equivUsdBcv) + " (BCV)  ·  " +
                            MoneyFormatter.formatUsd(equivUsdt).replace("$", "") + " USDT (P2P)",
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TNUM),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (equivUsdBcv > equivUsdt && equivUsdt > 0) {
                        val savedUsd = equivUsdBcv - equivUsdt
                        Text(
                            text = "Pagando en Bs con USDT te ahorras " + MoneyFormatter.formatUsd(savedUsd),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFeatureSettings = TNUM,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    copiedMsg?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // --- Dropdown de fuente + botón actualizar ---
            Text(
                text = "FUENTE DE LA TASA PARA PATRIMONIO",
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
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private fun fmt(v: Double?): String =
    v?.let { MoneyFormatter.formatMinor((it * 100).toLong(), "VES") } ?: "—"
