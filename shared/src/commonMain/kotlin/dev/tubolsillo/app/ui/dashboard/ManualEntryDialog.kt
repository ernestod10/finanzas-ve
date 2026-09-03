package dev.tubolsillo.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.db.entity.AccountEntity
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.domain.model.TransactionCategory

/**
 * Diálogo de entrada manual. El monto se parsea con AmountNormalizer, así el
 * usuario puede escribir "25,00", "1.234,56" o "25.5" sin preocuparse.
 */
@Composable
fun ManualEntryDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSave: (accountId: Long, amountMinor: Long, currencyCode: String, description: String, category: TransactionCategory) -> Unit,
) {
    var account by remember { mutableStateOf(accounts.firstOrNull()) }
    var isExpense by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(TransactionCategory.MERCHANT) }

    val currency = account?.currency ?: "USD"
    val amountMinor = AmountNormalizer.parseMinor(amountText, currency)
    val canSave = account != null && amountMinor != null && description.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo movimiento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = isExpense, onClick = { isExpense = true }, label = { Text("Gasto") })
                    FilterChip(selected = !isExpense, onClick = { isExpense = false }, label = { Text("Ingreso") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    accounts.forEach { acc ->
                        FilterChip(
                            selected = account?.id == acc.id,
                            onClick = { account = acc },
                            label = { Text(acc.name, maxLines = 1) },
                        )
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Monto (" + currency + ")") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción (ej. FARMACIA X)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionCategory.entries
                        .filter { it != TransactionCategory.TRANSFER }
                        .forEach { c ->
                            FilterChip(
                                selected = category == c,
                                onClick = { category = c },
                                label = { Text(c.displayName, maxLines = 1) },
                            )
                        }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val account = account ?: return@TextButton
                    val minor = amountMinor ?: return@TextButton
                    val signed = if (isExpense) -kotlin.math.abs(minor) else kotlin.math.abs(minor)
                    onSave(account.id, signed, account.currency, description.trim(), category)
                },
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
