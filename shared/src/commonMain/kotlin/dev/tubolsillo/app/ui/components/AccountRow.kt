package dev.tubolsillo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.domain.model.AccountBalanceView
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.ui.theme.TNUM

/**
 * Fila de cuenta para la lista agrupada: monograma, nombre, institución y el
 * saldo a la derecha con chevron de navegación.
 *
 * En una línea de crédito el dato que manda es el DISPONIBLE (lo que el usuario
 * ve en la app del banco), y la deuda baja a la meta en tinta secundaria: la
 * salud la comunica el número grande, no un color de alarma.
 */
@Composable
fun AccountRow(
    view: AccountBalanceView,
    onClick: () -> Unit,
    showSeparator: Boolean = true,
) {
    val kind = AccountKind.fromKey(view.kindKey)
    val isDebt = kind.isDebt
    val limitMinor = view.creditLimitMinor
    val debtMinor = view.balanceMinor?.let { kotlin.math.abs(it) } ?: 0L
    val availableMinor = view.creditAvailableMinor
        ?: limitMinor?.let { (it - debtMinor).coerceAtLeast(0L) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonogramBadge(view)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = view.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = Institution.fromKey(view.institutionKey).displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                if (isDebt && availableMinor != null) {
                    Text(
                        text = MoneyFormatter.formatMinor(availableMinor, view.currencyCode),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFeatureSettings = TNUM,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (debtMinor > 0) {
                            "Debe " + MoneyFormatter.formatMinor(debtMinor, view.currencyCode)
                        } else {
                            "Sin deuda"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TNUM),
                        color = if (debtMinor > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = MoneyFormatter.formatMinor(view.balanceMinor ?: 0L, view.currencyCode),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFeatureSettings = TNUM,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = if (isDebt) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            DisclosureChevron()
        }
        if (showSeparator) {
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 68.dp, end = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            )
        }
    }
}

/** Monograma de la institución: la marca sin depender de logos con licencia. */
@Composable
fun MonogramBadge(view: AccountBalanceView, size: Int = 40) {
    val accent = parseAccountColor(view.colorHex) ?: MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(color = accent.copy(alpha = 0.14f), shape = RoundedCornerShape(size.dp / 3)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsFor(Institution.fromKey(view.institutionKey).displayName),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
    }
}
