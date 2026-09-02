package dev.tubolsillo.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.hilt.navigation.compose.hiltViewModel
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.ui.components.AccountCard
import dev.tubolsillo.app.ui.components.DebtCard
import dev.tubolsillo.app.ui.components.TxRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAccountClick: (Long) -> Unit,
    onSeeAllTransactions: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val dashboard by viewModel.dashboard.collectAsState()
    val vesPerDollar by viewModel.vesPerDollar.collectAsState()
    var showManualEntry by remember { mutableStateOf(false) }
    var showAddAccount by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("TuBolsillo") },
                actions = {
                    IconButton(onClick = { showAddAccount = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Agregar cuenta")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showManualEntry = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Agregar movimiento")
            }
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
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Agrega tu primera cuenta", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Toca + arriba para agregar un banco, TDC o wallet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = MoneyFormatter.formatUsd(data.totals.netWorthUsd),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "≈ " + MoneyFormatter.formatMinor(
                                (data.totals.netWorthUsd * vesPerDollar * 100).toLong(),
                                "VES",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                val assets = data.accountViews.filter { !AccountKind.fromKey(it.kindKey).isDebt }
                val debts = data.accountViews.filter { AccountKind.fromKey(it.kindKey).isDebt }
                val accountName = { id: Long -> data.accounts.firstOrNull { it.id == id }?.name }

                assets.forEach { view ->
                    item(key = "asset-${view.accountId}") {
                        AccountCard(view = view, onClick = { onAccountClick(view.accountId) })
                    }
                }

                if (debts.isNotEmpty()) {
                    item(key = "debt-header") {
                        Text(
                            text = "Deudas",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(top = 8.dp),
                        )
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
                                text = "Recientes",
                                style = MaterialTheme.typography.titleLarge,
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
            onSave = { institution, name, kind, currency, initialMinor, limitMinor ->
                viewModel.addAccount(institution, name, kind, currency, initialMinor, limitMinor)
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
