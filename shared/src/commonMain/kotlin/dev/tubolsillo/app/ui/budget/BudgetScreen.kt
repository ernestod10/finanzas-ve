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
import dev.tubolsillo.app.ui.components.ThinBar
import dev.tubolsillo.app.ui.components.monthLabel
import dev.tubolsillo.app.ui.dashboard.PlanSummaryCard
import dev.tubolsillo.app.ui.theme.TNUM
import org.koin.compose.viewmodel.koinViewModel

/**
 * Presupuesto del mes: total arriba, plan del mes e ingresos/gastos estimados,
 * y una fila por categoría con su barra de avance.
 */
@Composable
fun BudgetScreen(
    onManageCategories: () -> Unit = {},
    onOpenPlan: () -> Unit = {},
    viewModel: BudgetViewModel = koinViewModel(),
) {
    val month by viewModel.month.collectAsState()
    val data by viewModel.monthData.collectAsState()
    val planData by viewModel.planData.collectAsState()
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
                // Estado del mes: cuánto queda (o cuánto se pasó) y el ritmo
                // diario que queda disponible.
                val limit = monthData.totalLimitUsd
                val level = monthData.totalLevel
                val daysLeft = dev.tubolsillo.app.core.time.MonthBuckets.daysLeftIn(monthData.month)
                if (limit != null && limit > 0) {
                    val remaining = limit - monthData.totalSpentUsd
                    val text = when {
                        remaining < 0 -> "Excedido por " + MoneyFormatter.formatUsd(-remaining)
                        daysLeft != null && daysLeft > 0 ->
                            "Te quedan " + MoneyFormatter.formatUsd(remaining) +
                                " · ≈ " + MoneyFormatter.formatUsd(remaining / daysLeft) +
                                (if (daysLeft == 1) " para hoy" else " por día · $daysLeft días")
                        else -> "Te quedan " + MoneyFormatter.formatUsd(remaining)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TNUM),
                        color = when (level) {
                            dev.tubolsillo.app.domain.model.BudgetLevel.OVER -> MaterialTheme.colorScheme.error
                            dev.tubolsillo.app.domain.model.BudgetLevel.WARN -> MaterialTheme.colorScheme.tertiary
                            dev.tubolsillo.app.domain.model.BudgetLevel.OK -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                monthData.totalProgress?.let { progress ->
                    Spacer(Modifier.height(10.dp))
                    ThinBar(
                        progress = progress,
                        over = monthData.totalSpentUsd > (monthData.totalLimitUsd ?: Double.MAX_VALUE),
                        warning = monthData.totalLevel == dev.tubolsillo.app.domain.model.BudgetLevel.WARN,
                        marker = dev.tubolsillo.app.core.time.MonthBuckets.daysLeftIn(monthData.month)?.let { left ->
                            val total = dev.tubolsillo.app.core.time.MonthBuckets.lastDayOf(monthData.month)
                            ((total - left + 1f).coerceIn(0f, total.toFloat())) / total.toFloat()
                        },
                    )
                }
            }
        }

        item(key = "plan") {
            PlanSummaryCard(
                plan = planData,
                onOpen = onOpenPlan,
                onTogglePaid = { viewModel.togglePlanLinePaid(it) },
            )
        }

        item(key = "cats-h") { GroupHeader("Categorías") }
        item(key = "cats") {
            GroupedGroup {
                monthData.rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(enabled = row.tagName != "Sin categoría") {
                                editingTag = row.tagName
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.tagName,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Spacer(Modifier.height(5.dp))
                            ThinBar(
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
                            val limit = row.limitUsd
                            if (limit != null) {
                                Text(
                                    text = "de " + MoneyFormatter.formatUsd(limit),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TNUM),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else if (row.tagName != "Sin categoría") {
                                Text(
                                    text = "Definir límite",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
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
        val currentLimitUsd = data?.rows?.firstOrNull { it.tagName == tagName }?.limitUsd
        BudgetDialog(
            tagName = tagName,
            initialAmountUsd = currentLimitUsd,
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
    initialAmountUsd: Double?,
    onDismiss: () -> Unit,
    onSave: (amountText: String, currencyCode: String) -> Unit,
) {
    var amountText by remember(tagName, initialAmountUsd) {
        mutableStateOf(
            initialAmountUsd?.let {
                val rounded = kotlin.math.round(it * 100.0) / 100.0
                if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
                else rounded.toString()
            } ?: ""
        )
    }
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
