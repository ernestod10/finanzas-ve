package dev.tubolsillo.app.ui.budget

import org.koin.compose.viewmodel.koinViewModel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.ui.components.TagChip
import dev.tubolsillo.app.ui.components.monthLabel

@Composable
fun BudgetScreen(
    onManageCategories: () -> Unit = {},
    viewModel: BudgetViewModel = koinViewModel(),
) {
    val month by viewModel.month.collectAsState()
    val data by viewModel.monthData.collectAsState()
    var editingTag by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Selector de mes.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.previousMonth() }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Mes anterior")
            }
            Text(
                text = monthLabel(month),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { viewModel.nextMonth() }) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Mes siguiente")
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onManageCategories) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Categorías")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val monthData = data
            if (monthData == null) return@LazyColumn

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Gastado " + MoneyFormatter.formatUsd(monthData.totalSpentUsd),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    monthData.totalLimitUsd?.let { limit ->
                        Text(
                            text = "de " + MoneyFormatter.formatUsd(limit) + " presupuestado",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    monthData.totalProgress?.let { progress ->
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                        )
                    }
                }
            }

            monthData.rows.forEach { row ->
                item(key = row.tagName) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (row.isOverBudget)
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surface,
                        ),
                        onClick = { editingTag = row.tagName },
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TagChip(name = row.tagName, modifier = Modifier.weight(1f, fill = false))
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = MoneyFormatter.formatUsd(row.spentUsd) +
                                        (row.limitUsd?.let { " / " + MoneyFormatter.formatUsd(it) } ?: ""),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (row.isOverBudget) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            row.progress?.let { progressValue ->
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { progressValue },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                )
                            }
                            row.remainingUsd?.let { remaining ->
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (remaining >= 0) "Quedan " + MoneyFormatter.formatUsd(remaining)
                                    else "Excedido en " + MoneyFormatter.formatUsd(-remaining),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (remaining >= 0) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Toca una etiqueta para definir su presupuesto del mes.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    editingTag?.let { tagName ->
        BudgetDialog(
            tagName = tagName,
            onDismiss = { editingTag = null },
            onSave = { amountText, currency ->
                viewModel.setBudget(tagName, amountText, currency)
                editingTag = null
            },
        )
    }
}

@Composable
private fun BudgetDialog(
    tagName: String,
    onDismiss: () -> Unit,
    onSave: (amountText: String, currencyCode: String) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("USD") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Presupuesto: " + tagName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("USD", "VES").forEach { code ->
                        androidx.compose.material3.FilterChip(
                            selected = currency == code,
                            onClick = { currency = code },
                            label = { Text(code) },
                        )
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Límite mensual (" + currency + ")") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amountText.isNotBlank(),
                onClick = { onSave(amountText, currency) },
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
