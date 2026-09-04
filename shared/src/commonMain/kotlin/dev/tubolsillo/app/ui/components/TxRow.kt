package dev.tubolsillo.app.ui.components

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
import androidx.compose.material.icons.filled.MailOutline
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
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.model.SourceType

/** Fila de transacción: descripción, tiempo y monto en números tabulares. */
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = sourceIcon(SourceType.fromKey(tx.transaction.source)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(10.dp))
            androidx.compose.foundation.layout.Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tx.transaction.description,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                    )
                    if (tx.transaction.transferPairId != null) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = "Transferencia interna",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val meta = listOfNotNull(
                        accountName,
                        formatTime(tx.transaction.occurredAt),
                    ).joinToString(" · ")
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.End) {
            Text(
                text = MoneyFormatter.formatMinor(
                    minor = tx.transaction.amountMinor,
                    code = tx.transaction.currency,
                    withSymbol = true,
                ),
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = if (negative) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )
            if (tx.tags.isNotEmpty()) {
                Spacer(Modifier.size(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    tx.tags.take(1).forEach { tag ->
                        TagChip(name = tag.name)
                    }
                }
            }
        }
    }
}

private fun sourceIcon(source: SourceType): ImageVector = when (source) {
    SourceType.SMS -> Icons.Filled.Sms
    SourceType.NOTIFICATION -> Icons.Filled.Notifications
    SourceType.BINANCE -> Icons.Filled.CurrencyBitcoin
    SourceType.CSV -> Icons.Filled.FileDownload
    SourceType.MANUAL -> Icons.Filled.Edit
    SourceType.EMAIL -> Icons.Filled.MailOutline
}
