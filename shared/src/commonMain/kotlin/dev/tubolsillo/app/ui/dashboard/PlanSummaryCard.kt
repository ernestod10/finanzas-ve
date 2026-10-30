package dev.tubolsillo.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import dev.tubolsillo.app.ui.components.ThinBar
import dev.tubolsillo.app.ui.components.dayLabel
import dev.tubolsillo.app.ui.components.formatDate
import dev.tubolsillo.app.ui.theme.TNUM

/**
 * El plan del mes en una sola mirada: cuánto falta pagar y qué tan avanzado va.
 *
 * Antes el resumen competía con el resto de Inicio —dos filas de estimado y
 * real, una fila de liquidez, dos pies y dos acciones—. En Inicio alcanza con UN
 * número y una barra: el detalle vive en su pantalla. Las líneas se despliegan a
 * demanda, que es la única información que se gana al tocar.
 */
@Composable
fun PlanSummaryCard(
    plan: PlanMonthData?,
    onOpen: () -> Unit,
    liquidAssetsUsd: Double? = null,
    onTogglePaid: ((PlanLineView) -> Unit)? = null,
) {
    if (plan == null) return

    var expanded by rememberSaveable { mutableStateOf(false) }

    GroupHeader("Plan del mes")

    if (plan.isEmpty) {
        GroupedGroup {
            GroupRow(
                onClick = onOpen,
                content = {
                    Text(
                        text = "Sin plan cargado",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "Carga tus ingresos y tus pagos con fecha.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailing = { HeaderAction("Armar", onClick = onOpen) },
            )
        }
        return
    }

    // El lado que importa: si hay gastos, lo que falta pagar; si solo hay
    // ingresos, lo que falta cobrar. Nunca una fila de $0,00 sin sentido.
    val hasExpenses = plan.expenses.isNotEmpty()
    val pendingUsd = if (hasExpenses) plan.pendingExpenseUsd else plan.pendingIncomeUsd
    val estimatedUsd = if (hasExpenses) plan.estimatedExpenseUsd else plan.estimatedIncomeUsd
    val lines = if (hasExpenses) plan.expenses else plan.income
    val doneCount = lines.count { it.isPaid }
    val overdueCount = lines.count { it.isOverdue }
    val progress = if (estimatedUsd > 0) {
        ((estimatedUsd - pendingUsd) / estimatedUsd).toFloat()
    } else 0f

    GroupedGroup {
        GroupRow(
            onClick = onOpen,
            content = {
                Text(
                    text = if (hasExpenses) "Por pagar" else "Por cobrar",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = doneCount.toString() + " de " + lines.size + " listos",
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TNUM),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                ThinBar(progress = progress, over = hasExpenses && plan.expenseOver)
            },
            trailing = {
                Text(
                    text = MoneyFormatter.formatUsd(pendingUsd),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFeatureSettings = TNUM,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (overdueCount > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
            },
        )

        if (expanded) {
            val allLines = (plan.expenses + plan.income).sortedBy { it.occursAt }
            allLines.forEach { line ->
                GroupSeparator(insetStart = 16)
                CompactPlanLineRow(
                    line = line,
                    onClick = onOpen,
                    onTogglePaid = onTogglePaid?.let { toggle -> { toggle(line) } },
                )
            }
        }
    }

    // Un solo pie: lo más urgente, sin repetir el número de arriba.
    urgencyLine(plan, pendingUsd, liquidAssetsUsd)?.let { GroupFooter(it) }

    // Una sola acción. "Editar plan" sobraba: la fila entera ya abre el plan.
    Row(modifier = Modifier.padding(start = 32.dp, top = 4.dp)) {
        HeaderAction(if (expanded) "Ocultar líneas" else "Ver líneas") {
            expanded = !expanded
        }
    }
}

/** La frase más útil del momento: lo vencido, si alcanza, o lo que viene. */
private fun urgencyLine(
    plan: PlanMonthData,
    pendingUsd: Double,
    liquidAssetsUsd: Double?,
): String? {
    val overdue = (plan.expenses + plan.income)
        .filter { it.isOverdue }
        .minByOrNull { it.occursAt }
    if (overdue != null) {
        val count = (plan.expenses + plan.income).count { it.isOverdue }
        val prefix = if (count == 1) "Vencido: " else count.toString() + " vencidos · primero: "
        return prefix + overdue.label + " · " + dayLabel(overdue.occursAt)
    }

    if (liquidAssetsUsd != null && pendingUsd > 0.0) {
        val free = liquidAssetsUsd - pendingUsd
        return if (free >= 0) {
            "Te alcanza lo que tenés en cuentas."
        } else {
            "Faltan " + MoneyFormatter.formatUsd(-free) + " para cubrir los pagos."
        }
    }

    return plan.nextDue?.let { next ->
        "Próximo: " + next.label + " · " + dayLabel(next.occursAt)
    }
}

@Composable
private fun CompactPlanLineRow(
    line: PlanLineView,
    onClick: () -> Unit,
    onTogglePaid: (() -> Unit)? = null,
) {
    val dateLabel = if (line.isRecurring) {
        "Día " + MonthBuckets.dayOfMonthOf(line.occursAt)
    } else {
        formatDate(line.occursAt)
    }
    val statusText = when {
        line.status == PlanStatus.PAID && line.kind == PlanKind.INCOME -> "Cobrado"
        line.status == PlanStatus.PAID -> "Pagado"
        line.isOverdue -> "Vencido"
        else -> "Pendiente"
    }
    GroupRow(
        onClick = onTogglePaid ?: onClick,
        leading = if (onTogglePaid != null) {
            {
                IconButton(
                    onClick = onTogglePaid,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = if (line.isPaid) Icons.Filled.CheckCircle
                        else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = statusText,
                        tint = if (line.isPaid) MaterialTheme.colorScheme.primary
                        else if (line.isOverdue) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        } else null,
        content = {
            Text(
                text = line.label,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (line.isPaid) TextDecoration.LineThrough else null,
                color = if (line.isPaid) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = dateLabel + " · " + statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (line.isOverdue) MaterialTheme.colorScheme.error
                else if (line.isPaid) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailing = {
            Text(
                text = (if (line.kind == PlanKind.INCOME) "+" else "−") +
                    MoneyFormatter.formatMinor(line.amountMinor, line.currencyCode),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFeatureSettings = TNUM,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (line.isPaid) MaterialTheme.colorScheme.onSurfaceVariant
                else if (line.kind == PlanKind.INCOME) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}
