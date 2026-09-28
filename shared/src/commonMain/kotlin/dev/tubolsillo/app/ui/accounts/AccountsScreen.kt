package dev.tubolsillo.app.ui.accounts

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import dev.tubolsillo.app.ui.components.AccountRow
import dev.tubolsillo.app.ui.components.GroupFooter
import dev.tubolsillo.app.ui.components.GroupHeader
import dev.tubolsillo.app.ui.components.GroupedGroup
import dev.tubolsillo.app.ui.components.HeaderAction
import dev.tubolsillo.app.ui.components.IOSScreen
import dev.tubolsillo.app.ui.dashboard.AddAccountDialog
import dev.tubolsillo.app.ui.dashboard.DashboardViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Todas las cuentas: activos arriba, tarjetas y líneas abajo. */
@Composable
fun AccountsScreen(
    onAccountClick: (Long) -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val dashboard by viewModel.dashboard.collectAsState()
    var showAddAccount by remember { mutableStateOf(false) }

    IOSScreen(
        title = "Cuentas",
        actions = { HeaderAction("Nueva") { showAddAccount = true } },
    ) {
        val data = dashboard
        if (data == null || data.accounts.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "Sin cuentas todavía. Toca Nueva para agregar la primera.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp),
                )
            }
        } else {
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
                    GroupFooter("En las líneas de crédito el número grande es lo disponible, no la deuda.")
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
