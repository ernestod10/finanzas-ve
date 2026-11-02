package dev.tubolsillo.app.ui.credits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.domain.model.CreditItem
import dev.tubolsillo.app.domain.model.CreditsOverview
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.ui.components.GroupFooter
import dev.tubolsillo.app.ui.components.GroupHeader
import dev.tubolsillo.app.ui.components.GroupedGroup
import dev.tubolsillo.app.ui.components.IOSScreen
import dev.tubolsillo.app.ui.components.formatDate
import dev.tubolsillo.app.ui.components.initialsFor
import dev.tubolsillo.app.ui.theme.TNUM
import dev.tubolsillo.app.ui.theme.TubolsilloColors
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/**
 * Créditos: todo lo que se debe, con cuánto y cuándo.
 *
 * Cashea entra como "cuotas": su deuda y su próxima fecha salen de las cuotas
 * pendientes (cada 14 días), no del saldo de la cuenta.
 */
@Composable
fun CreditsScreen(
    onBack: () -> Unit,
    onOpenAccount: (Long) -> Unit,
    viewModel: CreditsViewModel = koinViewModel(),
) {
    val overview by viewModel.overview.collectAsState()
    var editingLimit by remember { mutableStateOf<CreditItem?>(null) }

    IOSScreen(title = "Créditos", onBack = onBack) {
        item(key = "resumen") { CreditsSummary(overview) }

        if (overview.isEmpty) {
            item(key = "vacio") {
                GroupFooter(
                    "Sin deudas registradas. Cuando agregues una tarjeta de crédito o " +
                        "una compra a cuotas, aparecen aquí con su fecha de pago."
                )
            }
            return@IOSScreen
        }

        if (overview.cards.isNotEmpty()) {
            item(key = "cards-h") { GroupHeader("Tarjetas de crédito") }
            item(key = "cards") {
                GroupedGroup {
                    overview.cards.forEach { item ->
                        CreditRow(item, onOpenAccount, onSetLimit = { editingLimit = it })
                    }
                }
            }
        }

        if (overview.installments.isNotEmpty()) {
            item(key = "cuotas-h") { GroupHeader("Cuotas") }
            item(key = "cuotas") {
                GroupedGroup {
                    overview.installments.forEach { item ->
                        CreditRow(item, onOpenAccount, onSetLimit = { editingLimit = it })
                    }
                }
            }
            item(key = "cuotas-foot") {
                GroupFooter(
                    "Cashea se paga cada 14 días: la deuda es la suma de las cuotas pendientes " +
                        "y la fecha es la de la próxima."
                )
            }
        }

        if (overview.otherDebts.isNotEmpty()) {
            item(key = "otras-h") { GroupHeader("Otras deudas") }
            item(key = "otras") {
                GroupedGroup {
                    overview.otherDebts.forEach { item ->
                        CreditRow(item, onOpenAccount, onSetLimit = { editingLimit = it })
                    }
                }
            }
        }
    }

    editingLimit?.let { item ->
        var text by remember(item.accountId) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editingLimit = null },
            title = { Text("Límite de " + item.name) },
            text = {
                Column {
                    Text(
                        text = "Con el límite, la deuda se calcula sola: límite − disponible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Límite (" + item.currency + ")") },
                        singleLine = true,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setLimit(item.accountId, item.currency, text)
                        editingLimit = null
                    },
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { editingLimit = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun CreditsSummary(overview: CreditsOverview) {
    val next = overview.nextPayment
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "DEUDA TOTAL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (overview.isEmpty) {
                Text(
                    text = "Sin deudas",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                overview.debtByCurrency.forEach { (currency, debt) ->
                    Text(
                        text = MoneyFormatter.formatMinor(debt, currency),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = TNUM,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            next?.dueAt?.let { dueAt ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Próximo pago: " + (next.dueAmountMinor?.let {
                        MoneyFormatter.formatMinor(it, next.currency)
                    } ?: "") + " · " + formatDate(dueAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = next.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CreditRow(item: CreditItem, onOpen: (Long) -> Unit, onSetLimit: (CreditItem) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .then(Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = accent.copy(alpha = 0.16f), modifier = Modifier.size(38.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = initialsFor(Institution.fromKey(item.institutionKey).displayName),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                text = subtitleOf(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.pendingInstallments > 0) {
                Spacer(Modifier.height(6.dp))
                InstallmentProgress(item)
            }
            if (item.isCard && item.limitMinor == null) {
                TextButton(
                    onClick = { onSetLimit(item) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
                ) { Text("Poner límite para calcular la deuda") }
            }
        }
        Spacer(Modifier.size(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = MoneyFormatter.formatMinor(item.debtMinor, item.currency),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = TNUM,
                ),
                color = TubolsilloColors.negative,
            )
            item.availableMinor?.let { available ->
                Text(
                    text = "Disp. " + MoneyFormatter.formatMinor(available, item.currency),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TNUM),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InstallmentProgress(item: CreditItem) {
    val total = item.pendingInstallments + item.paidInstallments
    if (total <= 0) return
    val done = item.paidInstallments.toFloat() / total
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(50)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(done)
                .height(4.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
        )
    }
    Text(
        text = item.paidInstallments.toString() + " de " + total + " cuotas pagadas",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 3.dp),
    )
}

private fun subtitleOf(item: CreditItem): String {
    val parts = mutableListOf<String>()
    parts.add(Institution.fromKey(item.institutionKey).displayName)
    item.dueAt?.let { parts.add("vence " + formatDate(it)) }
    if (item.isCard) {
        item.usedPct?.let { parts.add((it * 100).roundToInt().toString() + " % usado") }
    } else if (item.pendingInstallments > 0) {
        item.nextInstallmentMinor?.let {
            parts.add("cuota " + MoneyFormatter.formatMinor(it, item.currency))
        }
    }
    if (item.kind == AccountKind.BNPL) parts.add("Cashea")
    return parts.joinToString(" · ")
}
