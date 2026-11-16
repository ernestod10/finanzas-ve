package dev.tubolsillo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import dev.tubolsillo.app.core.db.entity.CategoryEntity
import dev.tubolsillo.app.core.db.entity.TransactionSplitEntity
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.core.money.MoneyFormatter
import kotlin.math.abs

/** Una fila editable del reparto: categoría + monto en texto. */
private data class SplitRow(
    val categoryId: Long?,
    val amountText: String,
)

/**
 * Reparte un gasto entre varias categorías. La suma tiene que dar exactamente
 * el monto del movimiento; si no, no se puede guardar (así el presupuesto no
 * recibe plata de más ni de menos).
 */
@Composable
fun SplitEditorDialog(
    totalMinor: Long,
    currency: String,
    categories: List<CategoryEntity>,
    existing: List<TransactionSplitEntity>,
    onDismiss: () -> Unit,
    onSave: (List<Pair<Long?, Long>>) -> Unit,
) {
    val total = abs(totalMinor)
    fun textOf(minor: Long): String {
        val whole = minor / 100
        val cents = (minor % 100).toString().padStart(2, '0')
        return "$whole,$cents"
    }

    var rows by remember {
        mutableStateOf(
            if (existing.isNotEmpty()) {
                existing.map { SplitRow(it.categoryId, textOf(it.amountMinor)) }
            } else {
                listOf(SplitRow(null, textOf(total)), SplitRow(null, ""))
            }
        )
    }

    val parsed = rows.map { row -> AmountNormalizer.parseMinor(row.amountText, currency) ?: 0L }
    val sum = parsed.sum()
    val remainder = total - sum
    val valid = rows.size >= 2 && remainder == 0L && parsed.all { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dividir el gasto") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Total: " + MoneyFormatter.formatMinor(total, currency) +
                        if (remainder != 0L) {
                            " · falta repartir " + MoneyFormatter.formatMinor(remainder, currency)
                        } else {
                            " · repartido completo"
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (remainder == 0L) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                rows.forEachIndexed { index, row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CategoryPicker(
                            categories = categories,
                            selectedId = row.categoryId,
                            modifier = Modifier.weight(1.2f),
                            onSelect = { id ->
                                rows = rows.toMutableList().also {
                                    it[index] = it[index].copy(categoryId = id)
                                }
                            },
                        )
                        OutlinedTextField(
                            value = row.amountText,
                            onValueChange = { value ->
                                rows = rows.toMutableList().also {
                                    it[index] = it[index].copy(amountText = value)
                                }
                            },
                            label = { Text(currency) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                rows = rows.toMutableList().also { it.removeAt(index) }
                            },
                            enabled = rows.size > 2,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Quitar parte",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val left = remainder.coerceAtLeast(0L)
                            rows = rows + SplitRow(null, if (left > 0) textOf(left) else "")
                        },
                    ) { Text("Agregar parte") }
                    TextButton(
                        onClick = {
                            // Reparte lo que falta en partes iguales entre las filas.
                            val each = total / rows.size
                            rows = rows.mapIndexed { i, r ->
                                val amount = if (i == rows.lastIndex) total - each * (rows.size - 1) else each
                                r.copy(amountText = textOf(amount))
                            }
                        },
                    ) { Text("Partes iguales") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(rows.mapIndexed { i, row -> row.categoryId to parsed[i] }) },
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun CategoryPicker(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    modifier: Modifier = Modifier,
    onSelect: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = categories.firstOrNull { it.id == selectedId }?.name ?: "Sin categoría"
    androidx.compose.material3.Surface(
        onClick = { expanded = true },
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = dev.tubolsillo.app.ui.theme.TubolsilloColors.tileFill,
    ) {
        androidx.compose.foundation.layout.Box {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Sin categoría") },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onSelect(category.id)
                        expanded = false
                    },
                )
            }
            }
        }
    }
}
