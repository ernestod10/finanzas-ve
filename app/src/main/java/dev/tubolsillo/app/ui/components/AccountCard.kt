package dev.tubolsillo.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.model.AccountBalanceView
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.domain.model.Institution

/** Tarjeta de cuenta (activo): saldo nativo + equivalente USD. */
@Composable
fun AccountCard(view: AccountBalanceView, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = view.name,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = Institution.fromKey(view.institutionKey).displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (view.lastUpdateAt != null) {
                    Text(
                        text = "Actualizado " + formatTime(view.lastUpdateAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = view.balanceMinor?.let {
                        MoneyFormatter.formatMinor(it, view.currencyCode)
                    } ?: "—",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (view.usdEquivalent != null && view.currencyCode != "USD") {
                    Text(
                        text = "≈ " + MoneyFormatter.formatUsd(view.usdEquivalent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Tarjeta de deuda (TDC/BNPL): saldo vs límite con barra de progreso. */
@Composable
fun DebtCard(view: AccountBalanceView, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val debtMinor = view.balanceMinor?.let { if (it < 0) -it else it } ?: 0L
    val limitMinor = view.creditLimitMinor
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = view.name,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = AccountKind.fromKey(view.kindKey).displayName +
                            " · " + Institution.fromKey(view.institutionKey).displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = MoneyFormatter.formatMinor(debtMinor, view.currencyCode),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (view.usdEquivalent != null && view.currencyCode != "USD") {
                        Text(
                            text = "≈ " + MoneyFormatter.formatUsd(view.usdEquivalent),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (limitMinor != null && limitMinor > 0) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (debtMinor.toFloat() / limitMinor).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Usado " + MoneyFormatter.formatMinor(debtMinor, view.currencyCode) +
                        " de " + MoneyFormatter.formatMinor(limitMinor, view.currencyCode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
