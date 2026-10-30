package dev.tubolsillo.app.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import dev.tubolsillo.app.core.time.MonthBuckets
import dev.tubolsillo.app.domain.model.PlanKind
import dev.tubolsillo.app.domain.model.PlanLineView
import dev.tubolsillo.app.domain.model.PlanMonthData
import dev.tubolsillo.app.domain.model.PlanStatus
import dev.tubolsillo.app.ui.components.GroupFooter
import dev.tubolsillo.app.ui.components.GroupHeader
import dev.tubolsillo.app.ui.components.GroupRow
import dev.tubolsillo.app.ui.components.GroupSeparator
import dev.tubolsillo.app.ui.components.GroupedGroup
import dev.tubolsillo.app.ui.components.HeaderAction
import dev.tubolsillo.app.ui.components.IOSScreen
import dev.tubolsillo.app.ui.components.ThinBar
import dev.tubolsillo.app.ui.components.dayLabel
import dev.tubolsillo.app.ui.components.formatDate
import dev.tubolsillo.app.ui.components.monthLabel
import dev.tubolsillo.app.ui.theme.TNUM
import org.koin.compose.viewmodel.koinViewModel

/**
 * Plan del mes: lo que esperás ganar y pagar, enfrentado a lo que pasó.
 *
 * El orden de la lista es por urgencia: primero lo vencido, después los gastos
 * (lo accionable), y al final los ingresos.
 */
@Composable
fun PlanScreen(
    onBack: () -> Unit,
    viewModel: PlanViewModel = koinViewModel(),
) {
    val month by viewModel.month.collectAsState()
    val data by viewModel.monthData.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val editing by viewModel.editing.collectAsState()
    val message by viewModel.message.collectAsState()

    var adding by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<PlanLineView?>(null) }

    val categoryById = remember(categories) { categories.associateBy { it.id } }
    val overdue = remember(data) { data?.lines?.filter { it.isOverdue } ?: emptyList() }

    IOSScreen(
        title = monthLabel(month),
        onBack = onBack,
        actions = {
            HeaderAction("‹") { viewModel.previousMonth() }
            HeaderAction("›") { viewModel.nextMonth() }
            HeaderAction("Agregar") { adding = true }
        },
    ) {
        val plan = data ?: return@IOSScreen

        item(key = "hero") { PlanHero(plan) }

        item(key = "compare-h") { GroupHeader("Estimado vs. real") }
        item(key = "compare") {
            GroupedGroup {
                ComparisonRow(
                    title = "Ingresos",
                    estimatedUsd = plan.estimatedIncomeUsd,
                    actualUsd = plan.actualIncomeUsd,
                    // Más ingreso del previsto es buena noticia.
                    higherIsBetter = true,
                )
                GroupSeparator(insetStart = 16)
                ComparisonRow(
                    title = "Gastos",
                    estimatedUsd = plan.estimatedExpenseUsd,
                    actualUsd = plan.actualExpenseUsd,
                    higherIsBetter = false,
                )
            }
        }
        item(key = "compare-f") {
            GroupFooter(
                "El real son los movimientos del mes, sin transferencias entre tus cuentas. " +
                    "Si enlazás una línea con su movimiento, la comparación es exacta."
            )
        }

        if (overdue.isNotEmpty()) {
            item(key = "overdue-h") { GroupHeader("Vencidas") }
            item(key = "overdue") {
                GroupedGroup {
                    overdue.forEachIndexed { index, line ->
                        PlanLineRow(
                            line = line,
                            categoryName = line.categoryId?.let { categoryById[it]?.name },
                            onClick = { selected = line },
                            onAcceptSuggestion = { viewModel.linkTransaction(line.itemId, it) },
                            onDismissSuggestion = { viewModel.dismissSuggestion(line.itemId) },
                            showSeparator = index != overdue.lastIndex,
                        )
                    }
                }
            }
        }

        if (plan.expenses.isNotEmpty()) {
            item(key = "exp-h") { GroupHeader("Gastos estimados") }
            item(key = "exp") {
                GroupedGroup {
                    plan.expenses.forEachIndexed { index, line ->
                        PlanLineRow(
                            line = line,
                            categoryName = line.categoryId?.let { categoryById[it]?.name },
                            onClick = { selected = line },
                            onAcceptSuggestion = { viewModel.linkTransaction(line.itemId, it) },
                            onDismissSuggestion = { viewModel.dismissSuggestion(line.itemId) },
                            showSeparator = index != plan.expenses.lastIndex,
                        )
                    }
                }
            }
            item(key = "exp-f") {
                GroupFooter(
                    MoneyFormatter.formatUsd(plan.pendingExpenseUsd) + " por pagar de " +
                        MoneyFormatter.formatUsd(plan.estimatedExpenseUsd) + " estimado."
                )
            }
        }

        if (plan.income.isNotEmpty()) {
            item(key = "inc-h") { GroupHeader("Ingresos estimados") }
            item(key = "inc") {
                GroupedGroup {
                    plan.income.forEachIndexed { index, line ->
                        PlanLineRow(
                            line = line,
                            categoryName = line.categoryId?.let { categoryById[it]?.name },
                            onClick = { selected = line },
                            onAcceptSuggestion = { viewModel.linkTransaction(line.itemId, it) },
                            onDismissSuggestion = { viewModel.dismissSuggestion(line.itemId) },
                            showSeparator = index != plan.income.lastIndex,
                        )
                    }
                }
            }
            item(key = "inc-f") {
                GroupFooter(
                    MoneyFormatter.formatUsd(plan.pendingIncomeUsd) + " por cobrar de " +
                        MoneyFormatter.formatUsd(plan.estimatedIncomeUsd) + " estimado."
                )
            }
        }

        if (plan.isEmpty) {
            item(key = "empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Todavía no hay plan para este mes",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Cargá tu sueldo y tus pagos fijos —seguro, alquiler, matrícula— " +
                            "y los ves enfrentados a lo que de verdad entró y salió.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HeaderAction("Agregar la primera línea") { adding = true }
                }
            }
        }

        if (plan.ratesApproximate) {
            item(key = "rates-f") {
                GroupFooter(
                    "Falta la tasa de alguna moneda: esos montos se convirtieron 1 a 1. " +
                        "Cargala en Ajustes › Tasas para que los totales sean reales."
                )
            }
        }
    }

    if (adding) {
        PlanItemDialog(
            existing = null,
            categories = categories,
            onDismiss = { adding = false },
            onSave = { kind, label, amountText, currency, recurrence, dueAt, dayOfMonth, categoryId ->
                viewModel.addItem(
                    kind, label, amountText, currency, recurrence, dueAt, dayOfMonth, categoryId,
                )
                adding = false
            },
        )
    }

    editing?.let { item ->
        PlanItemDialog(
            existing = item,
            categories = categories,
            onDismiss = { viewModel.stopEdit() },
            onSave = { kind, label, amountText, currency, recurrence, dueAt, dayOfMonth, categoryId ->
                viewModel.updateItem(
                    item, label, amountText, currency, recurrence, dueAt, dayOfMonth, categoryId,
                )
            },
            onDelete = { viewModel.deleteItem(item.id) },
        )
    }

    selected?.let { line ->
        PlanLineDialog(
            line = line,
            categoryName = line.categoryId?.let { categoryById[it]?.name },
            onDismiss = { selected = null },
            onMarkPaid = {
                viewModel.setStatus(line.itemId, PlanStatus.PAID)
                selected = null
            },
            onMarkPending = {
                viewModel.setStatus(line.itemId, PlanStatus.PENDING)
                selected = null
            },
            onAcceptSuggestion = { txId ->
                viewModel.linkTransaction(line.itemId, txId)
                selected = null
            },
            onEdit = {
                viewModel.startEdit(line.itemId)
                selected = null
            },
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeMessage() },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeMessage() }) { Text("Entendido") }
            },
        )
    }
}

/** Cifra grande: lo que falta pagar, con el avance del mes en una barra. */
@Composable
private fun PlanHero(plan: PlanMonthData) {
    val progress = if (plan.estimatedExpenseUsd > 0) {
        (plan.actualExpenseUsd / plan.estimatedExpenseUsd).toFloat()
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, bottom = 4.dp),
    ) {
        Text(
            text = "TE QUEDA POR PAGAR",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = MoneyFormatter.formatUsd(plan.pendingExpenseUsd),
            style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = TNUM),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = plan.settledCount.toString() + " de " + plan.totalCount + " en orden" +
                if (plan.overdueCount > 0) " · " + plan.overdueCount + " vencida(s)" else "",
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TNUM),
            color = if (plan.overdueCount > 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (plan.estimatedExpenseUsd > 0) {
            Spacer(Modifier.height(10.dp))
            ThinBar(progress = progress, over = plan.expenseOver)
        }
    }
}

/** Estimado contra real, con la diferencia dicha en signo además de color. */
@Composable
private fun ComparisonRow(
    title: String,
    estimatedUsd: Double,
    actualUsd: Double,
    higherIsBetter: Boolean,
) {
    val delta = actualUsd - estimatedUsd
    val good = if (higherIsBetter) delta >= 0 else delta <= 0
    val deltaColor = if (kotlin.math.abs(delta) < 0.5) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else if (good) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    GroupRow(
        content = {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Estimado " + MoneyFormatter.formatUsd(estimatedUsd),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TNUM),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = MoneyFormatter.formatUsd(actualUsd),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFeatureSettings = TNUM,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Text(
                    text = signed(delta),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TNUM),
                    color = deltaColor,
                )
            }
        },
    )
}

/** "+$ 20,00" / "−$ 20,00": el signo es la señal, el color solo la refuerza. */
private fun signed(delta: Double): String =
    if (delta >= 0) "+" + MoneyFormatter.formatUsd(delta)
    else "−" + MoneyFormatter.formatUsd(kotlin.math.abs(delta))

/**
 * Fila de una línea del plan: glifo de estado, nombre con su fecha, y el monto
 * con la señal de pagado/pendiente en palabras.
 */
@Composable
private fun PlanLineRow(
    line: PlanLineView,
    categoryName: String?,
    onClick: () -> Unit,
    onAcceptSuggestion: (txId: Long) -> Unit,
    onDismissSuggestion: () -> Unit,
    showSeparator: Boolean,
) {
    GroupRow(
        onClick = onClick,
        leading = { StatusGlyph(line) },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (line.kind == PlanKind.INCOME) "+" else "−") +
                        MoneyFormatter.formatMinor(line.amountMinor, line.currencyCode),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFeatureSettings = TNUM,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (line.kind == PlanKind.INCOME) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stateLabel(line),
                    style = MaterialTheme.typography.bodySmall,
                    color = stateColor(line),
                )
            }
        },
        content = {
            Text(text = line.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = metaLabel(line, categoryName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            line.suggestion?.let { suggestion ->
                SuggestionHint(
                    occurredAt = suggestion.occurredAt,
                    amountUsd = suggestion.amountUsd,
                    onAccept = { onAcceptSuggestion(suggestion.txId) },
                    onDismiss = onDismissSuggestion,
                )
            }
        },
    )
    if (showSeparator) GroupSeparator(insetStart = 60)
}

/** Sugerencia inline: se ofrece, nunca se aplica sola. */
@Composable
private fun SuggestionHint(
    occurredAt: Long,
    amountUsd: Double,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = "Coincide con un movimiento del " + dayLabel(occurredAt) + " · " +
                MoneyFormatter.formatUsd(amountUsd),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TNUM),
            color = MaterialTheme.colorScheme.primary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onAccept, modifier = Modifier.height(32.dp)) {
                Text("Enlazar", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onDismiss, modifier = Modifier.height(32.dp)) {
                Text(
                    text = "No es",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusGlyph(line: PlanLineView) {
    val (icon, tint) = when {
        line.status == PlanStatus.PAID ->
            Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary

        line.isOverdue ->
            Icons.Filled.Warning to MaterialTheme.colorScheme.error

        else ->
            Icons.Filled.DateRange to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

private fun stateLabel(line: PlanLineView): String = when {
    line.status == PlanStatus.PAID && line.isSettledWithVariance ->
        "Real " + MoneyFormatter.formatMinor(
            line.settledMinor ?: 0L,
            line.settledCurrencyCode ?: line.currencyCode,
        )

    line.status == PlanStatus.PAID && line.kind == PlanKind.INCOME -> "Cobrado"
    line.status == PlanStatus.PAID -> "Pagado"
    line.status == PlanStatus.SKIPPED -> "Omitido este mes"
    line.isOverdue && line.kind == PlanKind.INCOME -> "Sin cobrar"
    line.isOverdue -> "Vencido"
    else -> "Pendiente"
}

@Composable
private fun stateColor(line: PlanLineView) = when {
    line.status == PlanStatus.PAID -> MaterialTheme.colorScheme.primary
    line.isOverdue -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** "10 ago · cada mes · Comida", o "día 10 · cada mes" si es recurrente. */
private fun metaLabel(line: PlanLineView, categoryName: String?): String {
    val date = if (line.isRecurring) {
        "día " + MonthBuckets.dayOfMonthOf(line.occursAt)
    } else {
        formatDate(line.occursAt)
    }
    val recurrence = if (line.isRecurring) "cada mes" else "una vez"
    return listOfNotNull(date, recurrence, categoryName).joinToString(" · ")
}
