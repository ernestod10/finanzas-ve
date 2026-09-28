package dev.tubolsillo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.db.dao.TransactionWithTags
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.model.SourceType
import dev.tubolsillo.app.ui.theme.TNUM

/**
 * Fila de movimiento al estilo de una lista agrupada de iOS: icono en
 * contenedor tintado (como los ajustes de iOS), descripción arriba, meta en
 * tinta secundaria, y el monto a la derecha en números tabulares.
 *
 * El signo va acompañado de color y de posición — nunca solo de color
 * (`color.md › Inclusive color`: "Avoid relying solely on color to
 * differentiate"). El `+`/`−` explícito es la segunda señal.
 */
@Composable
fun TxRow(
    tx: TransactionWithTags,
    accountName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    categoryName: String? = null,
    categoryColorHex: String? = null,
    showSeparator: Boolean = true,
    separatorInset: Int = 60,
) {
    val negative = tx.transaction.amountMinor < 0
    val source = SourceType.fromKey(tx.transaction.source)
    val frozenUsd = tx.transaction.usdEquivalentMinor

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .androidxClickable(onClick)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceGlyph(source)
            androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.transaction.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = buildList {
                    accountName?.let { add(it) }
                    categoryName?.let { add(it) }
                    tx.tags.firstOrNull()?.let { add(it.name) }
                }.joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (negative) "−" else "+") + MoneyFormatter.formatMinor(
                        minor = kotlin.math.abs(tx.transaction.amountMinor),
                        code = tx.transaction.currency,
                        withSymbol = true,
                    ),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFeatureSettings = TNUM,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (negative) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.primary,
                )
                if (tx.transaction.currency != "USD" && frozenUsd != null && frozenUsd != 0L) {
                    Text(
                        text = "≈ " + MoneyFormatter.formatMinor(kotlin.math.abs(frozenUsd), "USD"),
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TNUM),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (showSeparator) {
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = separatorInset.dp, end = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
        }
    }
}

/** Icono de origen en contenedor suave: 28dp, como los de Ajustes en iOS. */
@Composable
private fun SourceGlyph(source: SourceType) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
                shape = RoundedCornerShape(7.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = sourceIcon(source),
            contentDescription = source.displayName,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
    }
}

private fun Modifier.androidxClickable(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

private fun sourceIcon(source: SourceType): ImageVector = when (source) {
    SourceType.SMS -> Icons.Filled.Sms
    SourceType.NOTIFICATION -> Icons.Filled.Notifications
    SourceType.BINANCE -> Icons.Filled.CurrencyBitcoin
    SourceType.CSV -> Icons.Filled.FileDownload
    SourceType.MANUAL -> Icons.Filled.Edit
    SourceType.EMAIL -> Icons.Filled.MailOutline
    SourceType.PORTAL -> Icons.Filled.AccountBalance
}
