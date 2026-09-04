package dev.tubolsillo.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.model.AccountBalanceView
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.ui.components.initialsFor
import dev.tubolsillo.app.ui.components.parseAccountColor

/** Tarjeta de cuenta (activo): chip de icono + saldo con números tabulares. */
@Composable
fun AccountCard(view: AccountBalanceView, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val accColor = parseAccountColor(view.colorHex)
                ?: MaterialTheme.colorScheme.primary
            MonogramChip(
                short = initialsFor(Institution.fromKey(view.institutionKey).displayName),
                tint = accColor,
                container = accColor.copy(alpha = 0.12f),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = view.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = InstitutionLabel(view),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.4f,
                    )
                    view.lastUpdateAt?.let { at ->
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "· " + formatTime(at),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = view.balanceMinor?.let { MoneyFormatter.formatMinor(it, view.currencyCode) }
                        ?: "—",
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                )
                view.usdEquivalent?.let { usd ->
                    if (view.currencyCode != "USD") {
                        Text(
                            text = "≈ " + MoneyFormatter.formatUsd(usd),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Tarjeta de deuda (TDC/BNPL): chip terracota + barra de uso fina. */
@Composable
fun DebtCard(view: AccountBalanceView, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val debtMinor = view.balanceMinor?.let { if (it < 0) -it else it } ?: 0L
    val limitMinor = view.creditLimitMinor
    val isBnpl = AccountKind.fromKey(view.kindKey) == AccountKind.BNPL
    // BNPL (Cashea): el dato que ve el usuario es el DISPONIBLE = límite - deuda.
    val availableMinor = if (isBnpl && limitMinor != null) (limitMinor - debtMinor).coerceAtLeast(0L) else null
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val accColor = parseAccountColor(view.colorHex)
                    ?: MaterialTheme.colorScheme.error
                MonogramChip(
                    short = initialsFor(Institution.fromKey(view.institutionKey).displayName),
                    tint = accColor,
                    container = accColor.copy(alpha = 0.12f),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = view.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                    )
                    Text(
                        text = AccountKind.fromKey(view.kindKey).displayName.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.4f,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (availableMinor != null) {
                        Text(
                            text = "Disponible",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = MoneyFormatter.formatMinor(
                            availableMinor ?: debtMinor, view.currencyCode
                        ),
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = if (availableMinor != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                    )
                    view.usdEquivalent?.let { usd ->
                        if (view.currencyCode != "USD") {
                            Text(
                                text = "≈ " + MoneyFormatter.formatUsd(usd),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (limitMinor != null && limitMinor > 0) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (debtMinor.toFloat() / limitMinor).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp),
                    color = MaterialTheme.colorScheme.error,
                    trackColor = MaterialTheme.colorScheme.errorContainer,
                )
                Spacer(Modifier.height(4.dp))
                val kind = AccountKind.fromKey(view.kindKey)
                if (kind == AccountKind.BNPL) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Deuda " + MoneyFormatter.formatMinor(debtMinor, view.currencyCode),
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.error,
                        )
                        limitMinor?.let { lim ->
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "de límite " + MoneyFormatter.formatMinor(lim, view.currencyCode),
                                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Usado " + MoneyFormatter.formatMinor(debtMinor, view.currencyCode) +
                            " de " + MoneyFormatter.formatMinor(limitMinor, view.currencyCode),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun InstitutionLabel(view: AccountBalanceView): String =
    dev.tubolsillo.app.domain.model.Institution.fromKey(view.institutionKey).displayName

@Composable
private fun MonogramChip(
    short: String,
    tint: androidx.compose.ui.graphics.Color,
    container: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = CircleShape, color = container) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = short,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                )
            }
        }
    }
}
