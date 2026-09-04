package dev.tubolsillo.app.ui.accounts

import org.koin.compose.viewmodel.koinViewModel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.domain.usecase.RateProvider
import dev.tubolsillo.app.domain.usecase.TotalsCalculator
import dev.tubolsillo.app.ui.components.AccountCard
import dev.tubolsillo.app.ui.components.DebtCard
import dev.tubolsillo.app.ui.dashboard.AddAccountDialog
import dev.tubolsillo.app.ui.dashboard.DashboardViewModel

/** Lista de todas las cuentas (activos arriba, deudas abajo) con alta rápida. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onAccountClick: (Long) -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val dashboard by viewModel.dashboard.collectAsState()
    var showAddAccount by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Cuentas") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddAccount = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Agregar cuenta")
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
                    Text(
                        text = "Sin cuentas todavía — toca + para agregar la primera.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val assets = data.accountViews.filter { !AccountKind.fromKey(it.kindKey).isDebt }
                val debts = data.accountViews.filter { AccountKind.fromKey(it.kindKey).isDebt }

                assets.forEach { view ->
                    item(key = "asset-${view.accountId}") {
                        AccountCard(view = view, onClick = { onAccountClick(view.accountId) })
                    }
                }
                if (debts.isNotEmpty()) {
                    item {
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
            }
        }
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
