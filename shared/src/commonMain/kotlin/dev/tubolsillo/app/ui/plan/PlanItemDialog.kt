package dev.tubolsillo.app.ui.plan

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.db.entity.CategoryEntity
import dev.tubolsillo.app.core.db.entity.PlanItemEntity
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.core.money.Currency
import dev.tubolsillo.app.core.money.Money
import dev.tubolsillo.app.core.time.MonthBuckets
import dev.tubolsillo.app.core.time.nowMillis
import dev.tubolsillo.app.domain.model.PlanKind
import dev.tubolsillo.app.domain.model.PlanRecurrence
import dev.tubolsillo.app.ui.components.formatDate

/** Monedas que ofrece la app para una línea del plan. */
private val PLAN_CURRENCIES = listOf("USD", "VES", "USDT")

/**
 * Alta y edición de una línea del plan.
 *
 * "Cada mes" guarda solo el día: la línea se repite en todos los meses
 * siguientes. "Una vez" guarda una fecha completa. El monto se escribe siempre
 * en positivo; la dirección la da el selector Ingreso/Gasto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanItemDialog(
    existing: PlanItemEntity?,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (
        kind: PlanKind,
        label: String,
        amountText: String,
        currencyCode: String,
        recurrence: PlanRecurrence,
        dueAt: Long?,
        dayOfMonth: Int?,
        categoryId: Long?,
    ) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val today = remember { MonthBuckets.startOfDay(nowMillis()) }

    var kind by remember { mutableStateOf(PlanKind.fromKey(existing?.kind)) }
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var amountText by remember {
        mutableStateOf(existing?.let { editableAmount(it.amountMinor, it.currency) } ?: "")
    }
    var currency by remember { mutableStateOf(existing?.currency ?: "USD") }
    var recurrence by remember {
        mutableStateOf(PlanRecurrence.fromKey(existing?.recurrence))
    }
    var dueAt by remember { mutableStateOf(existing?.dueAt ?: today) }
    var dayOfMonth by remember {
        mutableStateOf(
            existing?.dayOfMonth
                ?: MonthBuckets.dayOfMonthOf(existing?.dueAt ?: today)
        )
    }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var showPicker by remember { mutableStateOf(false) }

    val amountMinor = AmountNormalizer.parseMinor(amountText, currency)
    val canSave = label.isNotBlank() && amountMinor != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Nueva línea" else "Editar línea") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = { Text(option.displayName) },
                        )
                    }
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nombre (ej. Seguro, Sueldo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Monto estimado (" + currency + ")") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PLAN_CURRENCIES.forEach { code ->
                        FilterChip(
                            selected = currency == code,
                            onClick = { currency = code },
                            label = { Text(code) },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanRecurrence.entries.forEach { option ->
                        FilterChip(
                            selected = recurrence == option,
                            onClick = { recurrence = option },
                            label = { Text(option.displayName) },
                        )
                    }
                }

                if (recurrence == PlanRecurrence.ONCE) {
                    // Fila que abre el calendario: la fecha completa solo hace
                    // falta cuando la línea es puntual.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatDate(dueAt),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showPicker = true }) { Text("Cambiar") }
                    }
                } else {
                    DayOfMonthField(
                        day = dayOfMonth,
                        onChange = { dayOfMonth = it },
                    )
                }

                if (categories.isNotEmpty()) {
                    Text(
                        text = "Categoría (opcional)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        categories.forEach { category ->
                            FilterChip(
                                selected = categoryId == category.id,
                                onClick = {
                                    categoryId = if (categoryId == category.id) null else category.id
                                },
                                label = { Text(category.name) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        kind,
                        label,
                        amountText,
                        currency,
                        recurrence,
                        dueAt,
                        dayOfMonth,
                        categoryId,
                    )
                },
            ) { Text(if (existing == null) "Agregar" else "Guardar") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = MonthBuckets.toPickerMillis(dueAt),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            dueAt = MonthBuckets.fromPickerMillis(it)
                        }
                        showPicker = false
                    },
                ) { Text("Listo") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Día del mes con dos botones: el 31 se recorta solo en los meses cortos. */
@Composable
private fun DayOfMonthField(day: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.DateRange,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Día " + day + " de cada mes",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        )
        IconButton(onClick = { onChange((day - 1).coerceIn(1, 31)) }) {
            Icon(Icons.Filled.Remove, contentDescription = "Un día menos")
        }
        IconButton(onClick = { onChange((day + 1).coerceIn(1, 31)) }) {
            Icon(Icons.Filled.Add, contentDescription = "Un día más")
        }
    }
}

/** Monto en unidades menores → texto editable, sin símbolo ni separador de miles. */
private fun editableAmount(minor: Long, currencyCode: String): String {
    val decimals = Currency.of(currencyCode).decimals
    val major = minor.toDouble() / Money.pow10(decimals)
    if (decimals > 2) return major.toString()
    // "60" en vez de "60.0"; con centavos, un decimal limpio.
    return if (major == major.toLong().toDouble()) major.toLong().toString()
    else (kotlin.math.round(major * 100) / 100.0).toString()
}
