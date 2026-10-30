package dev.tubolsillo.app.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.db.dao.TransactionWithTags
import dev.tubolsillo.app.ui.account.TagTransactionDialog
import dev.tubolsillo.app.ui.components.GroupFooter
import dev.tubolsillo.app.ui.components.GroupHeader
import dev.tubolsillo.app.ui.components.GroupedItemSurface
import dev.tubolsillo.app.ui.components.IOSScreen
import dev.tubolsillo.app.ui.components.TxRow
import org.koin.compose.viewmodel.koinViewModel

/**
 * Movimientos: buscador instantáneo, filtro por cuenta y categoría, lista
 * agrupada por día y edición/categorización en vivo al tocar cualquier fila.
 */
@Composable
fun TransactionsScreen(
    viewModel: TransactionsViewModel = koinViewModel(),
) {
    val groupedByDay by viewModel.groupedByDay.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    val accountNames by viewModel.accountNames.collectAsState()
    val categoryNames by viewModel.categoryNames.collectAsState()
    val selectedAccountId by viewModel.selectedAccountId.collectAsState()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var editingTx by remember { mutableStateOf<TransactionWithTags?>(null) }

    IOSScreen(title = "Registros") {
        item(key = "search", contentType = "search") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Buscar comercio, categoría o etiqueta...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }

        item(key = "filters", contentType = "filters") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "__all_acc") {
                        AccountFilterChip(
                            label = "Todas las cuentas",
                            selected = selectedAccountId == null,
                            onClick = { viewModel.selectAccount(null) },
                        )
                    }
                    items(accounts, key = { it.id }) { account ->
                        AccountFilterChip(
                            label = account.name,
                            selected = selectedAccountId == account.id,
                            onClick = { viewModel.selectAccount(account.id) },
                        )
                    }
                }
                if (categories.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "__all_cat") {
                            AccountFilterChip(
                                label = "Todas las categorías",
                                selected = selectedCategoryId == null,
                                onClick = { viewModel.selectCategory(null) },
                            )
                        }
                        item(key = "__uncat") {
                            AccountFilterChip(
                                label = "Sin categoría",
                                selected = selectedCategoryId == -1L,
                                onClick = { viewModel.selectCategory(-1L) },
                            )
                        }
                        items(categories, key = { it.id }) { cat ->
                            AccountFilterChip(
                                label = cat.name,
                                selected = selectedCategoryId == cat.id,
                                onClick = { viewModel.selectCategory(cat.id) },
                            )
                        }
                    }
                }
            }
        }

        if (groupedByDay.isEmpty()) {
            item(key = "empty") {
                GroupFooter("No hay movimientos que coincidan con el filtro actual.")
            }
        }

        val catColorById = categories.associate { it.id to it.colorHex }
        // Un grupo por día virtualizado fila por fila para scroll y cambio de página a 120Hz.
        groupedByDay.forEach { group ->
            val day = group.dayLabel
            val txs = group.transactions
            item(key = "h-$day", contentType = "header") { GroupHeader(day) }
            itemsIndexed(
                items = txs,
                key = { _, tx -> tx.transaction.id },
                contentType = { _, _ -> "tx_row" },
            ) { index, tx ->
                GroupedItemSurface(
                    isFirst = index == 0,
                    isLast = index == txs.lastIndex,
                ) {
                    TxRow(
                        tx = tx,
                        accountName = accountNames[tx.transaction.accountId],
                        onClick = { editingTx = tx },
                        categoryName = tx.transaction.categoryId?.let { categoryNames[it] },
                        categoryColorHex = tx.transaction.categoryId?.let { catColorById[it] },
                        showSeparator = index != txs.lastIndex,
                    )
                }
            }
        }
    }

    editingTx?.let { tx ->
        TagTransactionDialog(
            tx = tx,
            allTags = allTags,
            categories = categories,
            onDismiss = { editingTx = null },
            onTag = { tagName, learn ->
                viewModel.tagTransaction(tx.transaction.id, tagName, learn)
                editingTx = null
            },
            onCategory = { categoryId ->
                viewModel.setTransactionCategory(tx.transaction.id, categoryId)
                editingTx = null
            },
            onUpdateDetails = { newDesc, newMinor ->
                viewModel.updateTransactionDetails(
                    tx.transaction.id,
                    newDesc,
                    newMinor,
                    tx.transaction.currency,
                )
                editingTx = null
            },
            onDelete = {
                viewModel.deleteTransaction(tx.transaction.id)
                editingTx = null
            },
        )
    }
}

@Composable
private fun AccountFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
