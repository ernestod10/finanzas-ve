package dev.tubolsillo.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.ui.components.AccountRow
import dev.tubolsillo.app.ui.components.GroupFooter
import dev.tubolsillo.app.ui.components.GroupHeader
import dev.tubolsillo.app.ui.components.GroupedGroup
import dev.tubolsillo.app.ui.components.HeaderAction
import dev.tubolsillo.app.ui.components.IOSScreen
import dev.tubolsillo.app.ui.components.TxRow
import dev.tubolsillo.app.ui.theme.TNUM
import org.koin.compose.viewmodel.koinViewModel

/**
 * Inicio: el total, las cuentas y lo reciente. Estructura de lista agrupada de
 * iOS — un grupo por bloque, separadores sangrados — con el verde reservado
 * para cifras y acciones.
 */
@Composable
fun DashboardScreen(
    onAccountClick: (Long) -> Unit,
    onSeeAllTransactions: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val dashboard by viewModel.dashboard.collectAsState()
    val vesPerDollar by viewModel.vesPerDollar.collectAsState()
    val usdPerEur by viewModel.usdPerEur.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var showManualEntry by remember { mutableStateOf(false) }
    var showAddAccount by remember { mutableStateOf(false) }

    IOSScreen(
        title = "Inicio",
        actions = { HeaderAction("Agregar") { showAddAccount = true } },
    ) {
        val data = dashboard
        if (data == null || data.accounts.isEmpty()) {
            item(key = "empty") { EmptyState(onAdd = { showAddAccount = true }) }
        } else {
            item(key = "hero") {
                HeroBlock(
                    assetsUsd = data.totals.assetsUsd,
                    debtUsd = data.totals.debtUsd,
                    netWorthUsd = data.totals.netWorthUsd,
                    vesPerDollar = vesPerDollar,
                    usdPerEur = usdPerEur,
                    onRegister = { showManualEntry = true },
                )
            }

            val assets = data.accountViews.filter { !AccountKind.fromKey(it.kindKey).isDebt }
            val debts = data.accountViews.filter { AccountKind.fromKey(it.kindKey).isDebt }

            if (assets.isNotEmpty()) {
                item(key = "assets-h") { GroupHeader("Cuentas") }
                item(key = "assets") {
                    GroupedGroup {
                        assets.forEachIndexed { index, view ->
                            AccountRow(
                                view = view,
                                onClick = { onAccountClick(view.accountId) },
                                showSeparator = index != assets.lastIndex,
                            )
                        }
                    }
                }
            }

            if (debts.isNotEmpty()) {
                item(key = "debts-h") { GroupHeader("Tarjetas y líneas") }
                item(key = "debts") {
                    GroupedGroup {
                        debts.forEachIndexed { index, view ->
                            AccountRow(
                                view = view,
                                onClick = { onAccountClick(view.accountId) },
                                showSeparator = index != debts.lastIndex,
                            )
                        }
                    }
                }
                item(key = "debts-f") {
                    GroupFooter("En las líneas de crédito, el número grande es lo disponible.")
                }
            }

            if (data.recentTransactions.isNotEmpty()) {
                item(key = "recent-h") { GroupHeader("Recientes") }
                item(key = "recent-action") {
                    Row(modifier = Modifier.padding(start = 32.dp, end = 16.dp, bottom = 4.dp)) {
                        HeaderAction("Ver todos", onClick = onSeeAllTransactions)
                    }
                }
                val catById = categories.associateBy { it.id }
                val accountName = { id: Long -> data.accounts.firstOrNull { it.id == id }?.name }
                val recent = data.recentTransactions.take(8)
                item(key = "recent") {
                    GroupedGroup {
                        recent.forEachIndexed { index, tx ->
                            TxRow(
                                tx = tx,
                                accountName = accountName(tx.transaction.accountId),
                                onClick = onSeeAllTransactions,
                                categoryName = tx.transaction.categoryId?.let { catById[it]?.name },
                                showSeparator = index != recent.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showManualEntry) {
        ManualEntryDialog(
            accounts = dashboard?.accounts ?: emptyList(),
            onDismiss = { showManualEntry = false },
            onSave = { accountId, amountMinor, currency, description, category ->
                viewModel.addManualTransaction(accountId, amountMinor, currency, description, category)
                showManualEntry = false
            },
        )
    }
    if (showAddAccount) {
        AddAccountDialog(
            onDismiss = { showAddAccount = false },
            onSave = { institution, name, kind, currency, initialMinor, limitMinor, colorHex ->
                viewModel.addAccount(institution, name, kind, currency, initialMinor, limitMinor, colorHex)
                showAddAccount = false
            },
        )
    }
}

/**
 * El bloque del patrimonio. Sin tarjeta: en iOS una cifra grande vive sobre el
 * fondo agrupado y el peso lo lleva el número, no un contenedor.
 */
@Composable
private fun HeroBlock(
    assetsUsd: Double,
    debtUsd: Double,
    netWorthUsd: Double,
    vesPerDollar: Double,
    usdPerEur: Double,
    onRegister: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, top = 2.dp, bottom = 4.dp),
    ) {
        Text(
            text = "TOTAL EN CUENTAS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = MoneyFormatter.formatUsd(assetsUsd),
            style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = TNUM),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "≈ " + MoneyFormatter.formatMinor((assetsUsd * vesPerDollar * 100).toLong(), "VES"),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TNUM),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (usdPerEur > 0) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "≈ € " + MoneyFormatter.formatMinor((assetsUsd / usdPerEur * 100).toLong(), "EUR"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TNUM),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Deudas " + MoneyFormatter.formatUsd(debtUsd),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TNUM),
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "· Neto " + MoneyFormatter.formatUsd(netWorthUsd),
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TNUM),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        HeaderAction("Registrar movimiento", onClick = onRegister)
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Tu dinero en un solo lugar",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Agrega tu primer banco, tarjeta o wallet para ver tu total, tus deudas y tu presupuesto.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        HeaderAction("Agregar cuenta", onClick = onAdd)
    }
}
