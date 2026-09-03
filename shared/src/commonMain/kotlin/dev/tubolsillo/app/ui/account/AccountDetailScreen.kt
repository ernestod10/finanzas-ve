package dev.tubolsillo.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.db.dao.TransactionWithTags
import dev.tubolsillo.app.core.db.entity.TagEntity
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.ui.components.TagChip
import dev.tubolsillo.app.ui.components.TxRow
import dev.tubolsillo.app.ui.components.formatDate
import org.koin.core.parameter.parametersOf
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    accountId: Long,
    onBack: () -> Unit,
    viewModel: AccountViewModel = koinViewModel(parameters = { parametersOf(accountId) }),
) {
    val account by viewModel.account.collectAsState()
    val snapshots by viewModel.snapshots.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    var showBalanceEdit by remember { mutableStateOf(false) }
    var showLimitEdit by remember { mutableStateOf(false) }
    var taggingTx by remember { mutableStateOf<TransactionWithTags?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(account?.name ?: "Cuenta") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            },
        )
        val acc = account
        if (acc != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = Institution.fromKey(acc.institution).displayName + " · " +
                        AccountKind.fromKey(acc.kind).displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = acc.lastBalanceMinor?.let { MoneyFormatter.formatMinor(it, acc.currency) } ?: "—",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showBalanceEdit = true }) { Text("Editar saldo") }
                }
                if (AccountKind.fromKey(acc.kind).isDebt) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Límite: " + (acc.creditLimitMinor?.let {
                                MoneyFormatter.formatMinor(it, acc.currency)
                            } ?: "—"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showLimitEdit = true }) { Text("Editar") }
                    }
                }
                Text(
                    text = "Saldo manual — los conectores automáticos (SMS, push, Binance) llegan en próximas fases.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            if (snapshots.size > 1) {
                item {
                    Text(
                        text = "Historial de saldos",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                snapshots.take(15).forEach { snapshot ->
                    item(key = "snap-${snapshot.id}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = formatDate(snapshot.capturedAt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = MoneyFormatter.formatMinor(snapshot.amountMinor, snapshot.currency),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            transactions.forEach { tx ->
                item(key = "tx-${tx.transaction.id}") {
                    TxRow(
                        tx = tx,
                        accountName = null,
                        onClick = { taggingTx = tx },
                    )
                }
            }
            if (transactions.isEmpty() && snapshots.isEmpty()) {
                item {
                    Text(
                        text = "Sin movimientos. Toca + en Inicio para agregar uno.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }

    if (showBalanceEdit) {
        TextEditDialog(
            title = "Saldo actual (" + (account?.currency ?: "") + ")",
            onDismiss = { showBalanceEdit = false },
            onSave = { viewModel.updateBalance(it) },
        )
    }
    if (showLimitEdit) {
        TextEditDialog(
            title = "Límite de crédito (" + (account?.currency ?: "") + ")",
            onDismiss = { showLimitEdit = false },
            onSave = { viewModel.updateCreditLimit(it) },
        )
    }
    taggingTx?.let { tx ->
        TagTransactionDialog(
            tx = tx,
            allTags = viewModel.allTags.collectAsState().value,
            onDismiss = { taggingTx = null },
            onTag = { tagName, learn ->
                viewModel.tagTransaction(tx.transaction.id, tagName, learn)
                taggingTx = null
            },
        )
    }
}

/** Diálogo simple de edición de texto (saldo / límite). */
@Composable
private fun TextEditDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onSave(text); onDismiss() },
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

/**
 * Diálogo de etiquetado en vivo: etiquetas existentes como chips, campo para
 * crear una nueva, y "aprender" para que ese comercio se etiquete solo la
 * próxima vez.
 */
@Composable
fun TagTransactionDialog(
    tx: TransactionWithTags,
    allTags: List<TagEntity>,
    onDismiss: () -> Unit,
    onTag: (tagName: String, learn: Boolean) -> Unit,
) {
    var newTag by remember { mutableStateOf("") }
    var learn by remember { mutableStateOf(true) }
    val alreadyTagged = tx.tags.map { it.name }.toSet()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tx.transaction.description) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    allTags.forEach { tag ->
                        FilterChip(
                            selected = alreadyTagged.contains(tag.name),
                            onClick = { onTag(tag.name, false) },
                            label = { Text(tag.name, maxLines = 1) },
                        )
                    }
                }
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    label = { Text("Nueva etiqueta (ej. Comida)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = learn, onCheckedChange = { learn = it })
                    Text(
                        text = "Aprender: etiquetar solo este comercio la próxima vez",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = newTag.isNotBlank(),
                onClick = { onTag(newTag.trim(), learn) },
            ) { Text("Etiquetar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}
