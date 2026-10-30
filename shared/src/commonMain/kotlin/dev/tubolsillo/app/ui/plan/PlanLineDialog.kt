package dev.tubolsillo.app.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.core.time.MonthBuckets
import dev.tubolsillo.app.domain.model.PlanKind
import dev.tubolsillo.app.domain.model.PlanLineView
import dev.tubolsillo.app.domain.model.PlanStatus
import dev.tubolsillo.app.ui.components.dayLabel
import dev.tubolsillo.app.ui.components.formatDate
import dev.tubolsillo.app.ui.theme.TNUM

/**
 * Detalle y acciones de una línea: marcarla pagada o pendiente, enlazarla con
 * la transacción que la app sugiere, o editarla.
 *
 * La conciliación nunca es automática: la sugerencia se muestra y el usuario
 * decide. Es la regla de la casa — nada se mueve solo.
 */
@Composable
fun PlanLineDialog(
    line: PlanLineView,
    categoryName: String?,
    onDismiss: () -> Unit,
    onMarkPaid: () -> Unit,
    onMarkPending: () -> Unit,
    onAcceptSuggestion: (txId: Long) -> Unit,
    onEdit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(line.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailRow(
                    label = if (line.kind == PlanKind.INCOME) "A cobrar" else "A pagar",
                    value = MoneyFormatter.formatMinor(
                        line.amountMinor,
                        line.currencyCode,
                        withSymbol = true,
                    ) + if (line.isRecurring) " cada mes" else "",
                )
                DetailRow(
                    label = if (line.isRecurring) "Se repite" else "Fecha",
                    value = if (line.isRecurring) {
                        "Día " + MonthBuckets.dayOfMonthOf(line.occursAt) + " de cada mes"
                    } else {
                        formatDate(line.occursAt)
                    },
                )
                DetailRow(label = "Estado", value = statusLabel(line))
                categoryName?.let { DetailRow(label = "Categoría", value = it) }

                line.suggestion?.let { suggestion ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "¿Es este movimiento?",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = dayLabel(suggestion.occurredAt) + " · " +
                                    MoneyFormatter.formatUsd(suggestion.amountUsd),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFeatureSettings = TNUM,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            TextButton(onClick = { onAcceptSuggestion(suggestion.txId) }) {
                                Text("Enlazar y marcar pagado")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (line.status == PlanStatus.PAID) {
                TextButton(onClick = onMarkPending) { Text("Marcar pendiente") }
            } else {
                TextButton(onClick = onMarkPaid) {
                    Text(if (line.kind == PlanKind.INCOME) "Marcar cobrado" else "Marcar pagado")
                }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onEdit) { Text("Editar") }
                Spacer(Modifier.padding(horizontal = 4.dp))
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        },
    )
}

/** "Vencido" se dice con palabra, no solo con color. */
private fun statusLabel(line: PlanLineView): String = when {
    line.status == PlanStatus.PAID && line.kind == PlanKind.INCOME -> "Cobrado"
    line.status == PlanStatus.PAID -> "Pagado"
    line.status == PlanStatus.SKIPPED -> "Omitido"
    line.isOverdue && line.kind == PlanKind.INCOME -> "Sin cobrar · vencido"
    line.isOverdue -> "Pendiente · vencido"
    else -> "Pendiente"
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TNUM),
        )
    }
}
