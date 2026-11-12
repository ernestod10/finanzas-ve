package dev.tubolsillo.app.ui.savings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import dev.tubolsillo.app.domain.model.SavingsGoalView
import dev.tubolsillo.app.ui.components.ConfirmDeleteDialog
import dev.tubolsillo.app.ui.components.GroupFooter
import dev.tubolsillo.app.ui.components.GroupHeader
import dev.tubolsillo.app.ui.components.GroupSeparator
import dev.tubolsillo.app.ui.components.GroupedGroup
import dev.tubolsillo.app.ui.components.IOSScreen
import dev.tubolsillo.app.ui.components.ThinBar
import org.koin.compose.viewmodel.koinViewModel

/**
 * Objetivos de ahorro: cuánto llevas, cuánto falta y cuánto hay que apartar por
 * mes para llegar a la fecha límite.
 */
@Composable
fun SavingsScreen(
    onBack: () -> Unit,
    viewModel: SavingsViewModel = koinViewModel(),
) {
    val goals by viewModel.goals.collectAsState()
    val message by viewModel.message.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var contributing by remember { mutableStateOf<SavingsGoalView?>(null) }
    var deleting by remember { mutableStateOf<SavingsGoalView?>(null) }

    IOSScreen(
        title = "Objetivos de ahorro",
        onBack = onBack,
        actions = { TextButton(onClick = { adding = true }) { Text("Nuevo") } },
    ) {
        if (goals.isEmpty()) {
            item(key = "empty") {
                GroupFooter(
                    "Aún no tienes objetivos. Crea uno con un monto y, si quieres, una fecha: " +
                        "la app te dirá cuánto apartar cada mes."
                )
            }
        } else {
            item(key = "list-h") { GroupHeader("Tus metas") }
            item(key = "list") {
                GroupedGroup {
                    goals.forEachIndexed { index, goal ->
                        if (index > 0) GroupSeparator()
                        GoalRow(
                            goal = goal,
                            nowMs = viewModel.now(),
                            onContribute = { contributing = goal },
                            onDelete = { deleting = goal },
                        )
                    }
                }
            }
        }
        message?.let { text ->
            item(key = "msg") { GroupFooter(text) }
        }
    }

    if (adding) {
        AddGoalDialog(
            onDismiss = { adding = false },
            onSave = { name, target, deadline ->
                viewModel.addGoal(name, target, deadline)
                adding = false
            },
        )
    }
    contributing?.let { goal ->
        ContributeDialog(
            goal = goal,
            onDismiss = { contributing = null },
            onSave = { amount ->
                viewModel.contribute(goal, amount)
                contributing = null
            },
        )
    }
    deleting?.let { goal ->
        ConfirmDeleteDialog(
            title = "¿Eliminar «${goal.name}»?",
            message = "Se borra el objetivo y su historial de aportes.",
            onConfirm = { viewModel.archive(goal) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun GoalRow(
    goal: SavingsGoalView,
    nowMs: Long,
    onContribute: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(goal.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(
                    text = MoneyFormatter.formatMinor(goal.savedMinor, goal.currency) + " de " +
                        MoneyFormatter.formatMinor(goal.targetMinor, goal.currency),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = (goal.progress * 100).toInt().toString() + " %",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (goal.isComplete) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(8.dp))
        ThinBar(progress = goal.progress, over = false)
        Spacer(Modifier.height(6.dp))
        Text(
            text = buildString {
                if (goal.isComplete) {
                    append("¡Completado!")
                } else {
                    append("Faltan ").append(MoneyFormatter.formatMinor(goal.remainingMinor, goal.currency))
                    goal.perMonthNeeded(nowMs)?.let { perMonth ->
                        append(" · ").append(MoneyFormatter.formatMinor(perMonth, goal.currency))
                        append(" por mes")
                    }
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDelete) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
            TextButton(onClick = onContribute) { Text("Apartar") }
        }
    }
}

@Composable
private fun AddGoalDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, target: String, deadline: Long?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var months by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo objetivo") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("¿Para qué? (ej. Viaje)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Monto objetivo (USD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = months,
                    onValueChange = { months = it.filter { c -> c.isDigit() } },
                    label = { Text("¿En cuántos meses? (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && target.isNotBlank(),
                onClick = {
                    val deadline = months.toIntOrNull()?.takeIf { it > 0 }
                        ?.let { dev.tubolsillo.app.core.time.nowMillis() + it * 30L * 86_400_000L }
                    onSave(name, target, deadline)
                },
            ) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ContributeDialog(
    goal: SavingsGoalView,
    onDismiss: () -> Unit,
    onSave: (amount: String) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apartar para «${goal.name}»") },
        text = {
            Column {
                Text(
                    text = "Faltan " + MoneyFormatter.formatMinor(goal.remainingMinor, goal.currency),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Monto (" + goal.currency + ")") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = amount.isNotBlank(), onClick = { onSave(amount) }) { Text("Apartar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
