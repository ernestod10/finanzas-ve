package dev.tubolsillo.app.ui.subscriptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import dev.tubolsillo.app.core.db.entity.SubscriptionEntity
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.core.time.nowMillis
import dev.tubolsillo.app.domain.model.SubscriptionDates
import dev.tubolsillo.app.ui.components.ConfirmDeleteDialog
import dev.tubolsillo.app.ui.components.GroupFooter
import dev.tubolsillo.app.ui.components.GroupSeparator
import dev.tubolsillo.app.ui.components.GroupedGroup
import dev.tubolsillo.app.ui.components.HeaderAction
import dev.tubolsillo.app.ui.components.IOSScreen
import dev.tubolsillo.app.ui.components.formatDate
import dev.tubolsillo.app.ui.theme.TubolsilloColors
import org.koin.compose.viewmodel.koinViewModel

/**
 * Suscripciones puestas a mano: nombre, precio y el día en que se cobra.
 * Muestra el próximo cobro y cuántos días faltan, que es lo que uno mira
 * cuando se le viene el mes.
 */
@Composable
fun SubscriptionsScreen(
    onBack: () -> Unit,
    viewModel: SubscriptionsViewModel = koinViewModel(),
) {
    val items by viewModel.items.collectAsState()
    val message by viewModel.message.collectAsState()
    var editing by remember { mutableStateOf<SubscriptionEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SubscriptionEntity?>(null) }
    val now = remember { nowMillis() }

    IOSScreen(
        title = "Suscripciones",
        onBack = onBack,
        actions = { HeaderAction("Nueva") { creating = true } },
    ) {
        item(key = "intro") {
            GroupFooter(
                "Las que pagás todos los meses: Netflix, el gym, el hosting. " +
                    "La app te recuerda cuánto y qué día se cobran."
            )
        }
        if (items.isEmpty()) {
            item(key = "empty") {
                GroupFooter("Aún no anotaste ninguna. Toca «Nueva» arriba.")
            }
        } else {
            item(key = "list") {
                GroupedGroup {
                    items.forEachIndexed { index, item ->
                        if (index > 0) GroupSeparator()
                        val days = SubscriptionDates.daysUntilNext(item.dayOfMonth, now)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editing = item }
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                Text(
                                    text = "Día " + item.dayOfMonth + " · " +
                                        formatDate(
                                            SubscriptionDates.nextCharge(item.dayOfMonth, now)
                                        ) + " · en " + days + (if (days == 1) " día" else " días"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = MoneyFormatter.formatMinor(item.amountMinor, item.currency),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                    }
                }
            }
        }
        message?.let { text ->
            item(key = "msg") { GroupFooter(text) }
        }
    }

    if (creating || editing != null) {
        SubscriptionDialog(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { id, name, amount, currency, day ->
                viewModel.save(id, name, amount, currency, day)
                creating = false
                editing = null
            },
            onDelete = editing?.let { item ->
                { deleting = item }
            },
        )
    }

    deleting?.let { item ->
        ConfirmDeleteDialog(
            title = "¿Quitar «" + item.name + "»?",
            message = "Deja de aparecer en la lista. No toca movimientos pasados.",
            onConfirm = {
                viewModel.delete(item.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun SubscriptionDialog(
    initial: SubscriptionEntity?,
    onDismiss: () -> Unit,
    onSave: (Long?, String, String, String, String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var amount by remember {
        mutableStateOf(
            initial?.let {
                val whole = it.amountMinor / 100
                val cents = (it.amountMinor % 100).toString().padStart(2, '0')
                "$whole,$cents"
            } ?: ""
        )
    }
    var day by remember { mutableStateOf(initial?.dayOfMonth?.toString() ?: "") }
    var currency by remember { mutableStateOf(initial?.currency ?: "VES") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nueva suscripción" else "Editar suscripción") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre (ej. Netflix)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Precio (" + currency + ")") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("VES", "USD").forEach { code ->
                        val selected = currency == code
                        Surface(
                            onClick = { currency = code },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else TubolsilloColors.tileFill,
                        ) {
                            Text(
                                text = code,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = day,
                    onValueChange = { value -> day = value.filter { it.isDigit() }.take(2) },
                    label = { Text("Día del cobro (1-31)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Quitar suscripción", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(initial?.id, name, amount, currency, day) }) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
