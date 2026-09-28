package dev.tubolsillo.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.ui.components.GroupHeader
import dev.tubolsillo.app.ui.components.GroupedGroup
import dev.tubolsillo.app.ui.components.IOSScreen
import dev.tubolsillo.app.ui.components.TxRow
import dev.tubolsillo.app.ui.components.dayLabel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Movimientos: filtro por cuenta y lista agrupada por día — el encabezado de
 * cada grupo es el día, como agrupa Mail o Mensajes en iOS.
 */
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = koinViewModel(),
) {
    val filtered by viewModel.filtered.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedAccountId by viewModel.selectedAccountId.collectAsState()

    IOSScreen(title = "Movimientos") {
        item(key = "filters") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    AccountFilterChip(
                        label = "Todas",
                        selected = selectedAccountId == null,
                        onClick = { viewModel.selectAccount(null) },
                    )
                }
                accounts.forEach { account ->
                    item(key = account.id) {
                        AccountFilterChip(
                            label = account.name,
                            selected = selectedAccountId == account.id,
                            onClick = { viewModel.selectAccount(account.id) },
                        )
                    }
                }
            }
        }

        val accountName = { id: Long -> accounts.firstOrNull { it.id == id }?.name }
        val catById = categories.associateBy { it.id }

        // Un grupo por día: encabezado + filas con separadores sangrados.
        val byDay = filtered.groupBy { dayLabel(it.transaction.occurredAt) }
        byDay.forEach { (day, txs) ->
            item(key = "h-$day") { GroupHeader(day) }
            item(key = "g-$day") {
                GroupedGroup {
                    txs.forEachIndexed { index, tx ->
                        TxRow(
                            tx = tx,
                            accountName = accountName(tx.transaction.accountId),
                            onClick = {},
                            categoryName = tx.transaction.categoryId?.let { catById[it]?.name },
                            showSeparator = index != txs.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
