package dev.tubolsillo.app.ui.dashboard

import org.koin.compose.viewmodel.koinViewModel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.ui.components.AccountCard
import dev.tubolsillo.app.ui.components.AppHeader
import dev.tubolsillo.app.ui.components.DebtCard
import dev.tubolsillo.app.ui.components.TxRow

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            AppHeader(
                title = "TuBolsillo",
                actionIcon = Icons.Filled.Add,
                actionLabel = "Nueva cuenta",
                actionContentDescription = "Agregar cuenta",
                onAction = { showAddAccount = true },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val data = dashboard
            if (data == null || data.accounts.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        ) {
                            Box(
                                modifier = Modifier.size(72.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Tu dinero en un solo lugar",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Agrega tu primer banco, tarjeta o wallet\npara ver tu total, deudas y presupuesto.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = { showAddAccount = true },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                        ) {
                            Text("Agregar cuenta", modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }
                }
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = "TOTAL EN CUENTAS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.6.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = MoneyFormatter.formatUsd(data.totals.assetsUsd),
                            style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "≈ " + MoneyFormatter.formatMinor(
                                    (data.totals.assetsUsd * vesPerDollar * 100).toLong(),
                                    "VES",
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (usdPerEur > 0) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "≈ € " + dev.tubolsillo.app.core.money.MoneyFormatter.formatMinor(
                                        (data.totals.assetsUsd / usdPerEur * 100).toLong(),
                                        "EUR",
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Deudas " + MoneyFormatter.formatUsd(data.totals.debtUsd),
                                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "· Neto " + MoneyFormatter.formatUsd(data.totals.netWorthUsd),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { showManualEntry = true },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Registrar movimiento")
                        }
                    }
                }

                val assets = data.accountViews.filter { !AccountKind.fromKey(it.kindKey).isDebt }
                val debts = data.accountViews.filter { AccountKind.fromKey(it.kindKey).isDebt }
                val accountName = { id: Long -> data.accounts.firstOrNull { it.id == id }?.name }
                val catById = categories.associateBy { it.id }

                assets.forEach { view ->
                    item(key = "asset-${view.accountId}") {
                        AccountCard(view = view, onClick = { onAccountClick(view.accountId) })
                    }
                }

                if (debts.isNotEmpty()) {
                    item(key = "debt-header") {
                        SectionHeader(title = "Deudas")
                    }
                    debts.forEach { view ->
                        item(key = "debt-${view.accountId}") {
                            DebtCard(view = view, onClick = { onAccountClick(view.accountId) })
                        }
                    }
                }

                if (data.recentTransactions.isNotEmpty()) {
                    item(key = "recent-header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "RECIENTES",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "Ver todo",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clickableNoRipple { onSeeAllTransactions() },
                            )
                        }
                    }
                    data.recentTransactions.take(10).forEach { tx ->
                        item(key = "tx-${tx.transaction.id}") {
                            TxRow(
                                tx = tx,
                                accountName = accountName(tx.transaction.accountId),
                                onClick = { onSeeAllTransactions() },
                                categoryName = tx.transaction.categoryId?.let { catById[it]?.name },
                                categoryColorHex = tx.transaction.categoryId?.let { catById[it]?.colorHex },
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

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}

/** Encabezado de sección: versalitas espaciadas + hairline. */
@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.2.sp,
        )
        Spacer(Modifier.width(10.dp))
        androidx.compose.material3.HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        )
    }
}
