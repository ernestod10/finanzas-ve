package dev.tubolsillo.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.db.entity.AccountEntity
import dev.tubolsillo.app.core.db.entity.CategoryEntity
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.TransactionCategory

/**
 * Diálogo de entrada manual. Soporta asignar categoría de presupuesto y
 * programar automáticamente las 3 cuotas cada 14 días de Cashea en el Plan.
 */
@Composable
fun ManualEntryDialog(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (
        accountId: Long,
        amountMinor: Long,
        currencyCode: String,
        description: String,
        category: TransactionCategory,
        userCategoryId: Long?,
        casheaInstallmentMinor: Long?,
    ) -> Unit,
) {
    var account by remember { mutableStateOf(accounts.firstOrNull()) }
    var isExpense by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(TransactionCategory.MERCHANT) }
    var userCategoryId by remember { mutableStateOf<Long?>(null) }
    var isCasheaPurchase by remember {
        mutableStateOf(
            accounts.firstOrNull()?.let { Institution.fromKey(it.institution) == Institution.CASHEA } == true
        )
    }
    var installmentAmountText by remember { mutableStateOf("") }

    val currency = account?.currency ?: "USD"
    val amountMinor = AmountNormalizer.parseMinor(amountText, currency)
    val installmentMinor = if (isExpense && isCasheaPurchase) {
        if (installmentAmountText.isNotBlank()) {
            AmountNormalizer.parseMinor(installmentAmountText, currency)
        } else {
            amountMinor
        }
    } else null
    val canSave = account != null && amountMinor != null && description.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo movimiento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = isExpense, onClick = { isExpense = true }, label = { Text("Gasto") })
                    FilterChip(selected = !isExpense, onClick = { isExpense = false }, label = { Text("Ingreso") })
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(accounts, key = { it.id }) { acc ->
                        FilterChip(
                            selected = account?.id == acc.id,
                            onClick = {
                                account = acc
                                if (Institution.fromKey(acc.institution) == Institution.CASHEA) {
                                    isCasheaPurchase = true
                                }
                            },
                            label = { Text(acc.name, maxLines = 1) },
                        )
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = {
                        Text(
                            if (isExpense && isCasheaPurchase) "Pago inicial hoy ($currency)"
                            else "Monto ($currency)"
                        )
                    },
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
                if (categories.isNotEmpty() && isExpense) {
                    Text(
                        text = "Categoría de presupuesto",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item(key = "__none") {
                            FilterChip(
                                selected = userCategoryId == null,
                                onClick = { userCategoryId = null },
                                label = { Text("Auto / Sin categoría") },
                            )
                        }
                        items(categories, key = { it.id }) { cat ->
                            FilterChip(
                                selected = userCategoryId == cat.id,
                                onClick = { userCategoryId = cat.id },
                                label = { Text(cat.name, maxLines = 1) },
                            )
                        }
                    }
                }
                if (isExpense) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isCasheaPurchase,
                            onCheckedChange = { isCasheaPurchase = it },
                        )
                        Text(
                            text = "Compra Cashea: agendar 3 cuotas c/14 días en el Plan",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (isCasheaPurchase) {
                        OutlinedTextField(
                            value = installmentAmountText,
                            onValueChange = { installmentAmountText = it },
                            label = { Text("Monto de cada una de las 3 cuotas ($currency)") },
                            placeholder = { Text("Igual a la inicial si lo dejas vacío") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val acc = account ?: return@TextButton
                    val minor = amountMinor ?: return@TextButton
                    val signed = if (isExpense) -kotlin.math.abs(minor) else kotlin.math.abs(minor)
                    onSave(
                        acc.id,
                        signed,
                        acc.currency,
                        description.trim(),
                        category,
                        userCategoryId,
                        installmentMinor?.let { kotlin.math.abs(it) },
                    )
                },
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
