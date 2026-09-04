package dev.tubolsillo.app.ui.transactions

import org.koin.compose.viewmodel.koinViewModel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.ui.components.TxRow
import dev.tubolsillo.app.ui.components.dayLabel
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = koinViewModel(),
) {
    val filtered by viewModel.filtered.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedAccountId by viewModel.selectedAccountId.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = selectedAccountId == null,
                    onClick = { viewModel.selectAccount(null) },
                    label = { Text("Todas") },
                )
            }
            accounts.forEach { account ->
                item(key = account.id) {
                    FilterChip(
                        selected = selectedAccountId == account.id,
                        onClick = { viewModel.selectAccount(account.id) },
                        label = { Text(account.name, maxLines = 1) },
                    )
                }
            }
        }

        val accountName = { id: Long -> accounts.firstOrNull { it.id == id }?.name }
        val catById = categories.associateBy { it.id }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            var lastDay: String? = null
            filtered.forEach { tx ->
                val day = dayLabel(tx.transaction.occurredAt)
                if (day != lastDay) {
                    lastDay = day
                    item(key = "day-$day-${tx.transaction.id}") {
                        Text(
                            text = day,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                item(key = "tx-${tx.transaction.id}") {
                    TxRow(
                        tx = tx,
                        accountName = accountName(tx.transaction.accountId),
                        onClick = {},
                        categoryName = tx.transaction.categoryId?.let { catById[it]?.name },
                        categoryColorHex = tx.transaction.categoryId?.let { catById[it]?.colorHex },
                    )
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        text = "Sin movimientos todavía",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                    )
                }
            }
        }
    }
}
