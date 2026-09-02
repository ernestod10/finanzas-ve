package dev.tubolsillo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.db.dao.TransactionWithTags
import dev.tubolsillo.app.core.money.Money
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.model.SourceType
import dev.tubolsillo.app.domain.model.TransactionCategory

private val ExpenseColor = Color(0xFFB3261E)
private val IncomeColor = Color(0xFF1B7A3D)

@Composable
fun TxRow(
    tx: TransactionWithTags,
    accountName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val negative = tx.transaction.amountMinor < 0
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tx.transaction.description,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                )
                if (tx.transaction.transferPairId != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = "Transferencia interna",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = accountName ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (accountName != null) {
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = formatTime(tx.transaction.occurredAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (tx.tags.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    tx.tags.take(2).forEach { tag ->
                        TagChip(name = tag.name, modifier = Modifier.padding(end = 3.dp))
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = sourceIcon(SourceType.fromKey(tx.transaction.source)),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = MoneyFormatter.formatMinor(
                minor = tx.transaction.amountMinor,
                code = tx.transaction.currency,
                withSymbol = true,
            ),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (negative) ExpenseColor else IncomeColor,
        )
    }
}

private fun sourceIcon(source: SourceType): ImageVector = when (source) {
    SourceType.SMS -> Icons.Filled.Sms
    SourceType.NOTIFICATION -> Icons.Filled.Notifications
    SourceType.BINANCE -> Icons.Filled.CurrencyBitcoin
    SourceType.CSV -> Icons.Filled.FileDownload
    SourceType.MANUAL -> Icons.Filled.Edit
}

/** Color del monto: gasto rojo, ingreso verde. */
fun txAmountColor(negative: Boolean): Color = if (negative) ExpenseColor else IncomeColor

/** Muestra el monto con signo explícito para el detalle. */
fun signedAmount(amountMinor: Long, code: String): String =
    if (amountMinor > 0) "+" + MoneyFormatter.formatMinor(amountMinor, code)
    else MoneyFormatter.formatMinor(amountMinor, code)
