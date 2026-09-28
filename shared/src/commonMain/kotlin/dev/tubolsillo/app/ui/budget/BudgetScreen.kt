package dev.tubolsillo.app.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import dev.tubolsillo.app.ui.components.GroupFooter
import dev.tubolsillo.app.ui.components.GroupHeader
import dev.tubolsillo.app.ui.components.GroupedGroup
import dev.tubolsillo.app.ui.components.HeaderAction
import dev.tubolsillo.app.ui.components.IOSScreen
import dev.tubolsillo.app.ui.components.monthLabel
import dev.tubolsillo.app.ui.theme.TNUM
import org.koin.compose.viewmodel.koinViewModel

/**
 * Presupuesto del mes: total arriba y una fila por categoría con su barra de
 * avance. El mes se cambia desde la barra de navegación.
 */
@Composable
fun BudgetScreen(
    onManageCategories: () -> Unit = {},
    viewModel: BudgetViewModel = koinViewModel(),
) {
    val month by viewModel.month.collectAsState()
    val data by viewModel.monthData.collectAsState()
    var editingTag by remember { mutableStateOf<String?>(null) }

    IOSScreen(
        title = monthLabel(month),
        actions = {
            HeaderAction("‹") { viewModel.previousMonth() }
            HeaderAction("›") { viewModel.nextMonth() }
        },
    ) {
        val monthData = data
        if (monthData == null) return@IOSScreen

        item(key = "total") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, end = 32.dp, bottom = 4.dp),
            ) {
                Text(
                    text = "GASTADO",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = MoneyFormatter.formatUsd(monthData.totalSpentUsd),
                    style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = TNUM),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                monthData.totalLimitUsd?.let { limit ->
                    Text(
                        text = "de " + MoneyFormatter.formatUsd(limit) + " presupuestado",
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TNUM),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                monthData.totalProgress?.let { progress ->
                    Spacer(Modifier.height(10.dp))
                    BudgetBar(progress = progress, over = monthData.totalSpentUsd > (monthData.totalLimitUsd ?: Double.MAX_VALUE))
                }
            }
        }

        item(key = "cats-h") { GroupHeader("Categorías") }
        item(key = "cats") {
            GroupedGroup {
                monthData.rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable { editingTag = row.tagName }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.tagName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Spacer(Modifier.height(5.dp))
                            BudgetBar(
                                progress = row.progress ?: 0f,
                                over = row.isOverBudget,
                                modifier = Modifier.fillMaxWidth(0.85f),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = MoneyFormatter.formatUsd(row.spentUsd),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFeatureSettings = TNUM,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = if (row.isOverBudget) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            row.limitUsd?.let { limit ->
                                Text(
                                    text = "de " + MoneyFormatter.formatUsd(limit),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TNUM),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (index != monthData.rows.lastIndex) {
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }

        item(key = "cats-f") {
            GroupFooter("Toca una categoría para ponerle su límite del mes.")
        }

        item(key = "manage") {
            Box(modifier = Modifier.padding(start = 32.dp, top = 4.dp)) {
                HeaderAction("Administrar categorías", onClick = onManageCategories)
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

/** Barra de avance fina; el exceso cambia el color y engrosa el trazo. */
@Composable
private fun BudgetBar(
    progress: Float,
    over: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
                shape = RoundedCornerShape(50),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(5.dp)
                .background(
                    color = if (over) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50),
                ),
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
