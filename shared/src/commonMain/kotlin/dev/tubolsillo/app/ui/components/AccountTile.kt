package dev.tubolsillo.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.tubolsillo.app.core.money.Currency
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.model.AccountBalanceView
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.ui.theme.TNUM
import dev.tubolsillo.app.ui.theme.TubolsilloColors
import kotlin.math.abs
import org.jetbrains.compose.resources.painterResource

private val TileShape = RoundedCornerShape(12.dp)
private val TileHeight = 96.dp

/**
 * Tesela de cuenta: color de marca sólido, nombre y saldo. En las líneas de
 * crédito el número grande es el DISPONIBLE (lo que el usuario ve en su banco)
 * y el tipo lo dice un rótulo, no un color de alarma.
 */
@Composable
fun AccountTile(
    view: AccountBalanceView,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = accountAccent(view)
    val ink = onAccent(accent)
    val kind = AccountKind.fromKey(view.kindKey)

    Surface(
        onClick = onClick,
        modifier = modifier.height(TileHeight),
        shape = TileShape,
        color = accent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InstitutionMark(view = view, kind = kind)
                Spacer(Modifier.weight(1f))
                if (kind.isDebt) {
                    Text(
                        text = if (kind == AccountKind.BNPL) "BNPL" else "TDC",
                        style = MaterialTheme.typography.labelSmall,
                        color = ink.copy(alpha = 0.75f),
                    )
                }
            }
            Column {
                Text(
                    text = view.name,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                // Código + monto, como la referencia: "VES 160.000,00" entra
                // donde "Bs. 160.000,00" no.
                Text(
                    text = tileValue(view),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = TNUM,
                    ),
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Celda de alta: rellena, con borde de acento, para cerrar la grilla. */
@Composable
fun AddAccountTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(TileHeight),
        shape = TileShape,
        color = TubolsilloColors.tileFill,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Agregar cuenta",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Grilla de tres columnas, sin scroll propio: se compone dentro de la lista de
 * la pantalla. Las celdas vacías de la última fila se rellenan para que las
 * teselas no se estiren.
 */
@Composable
fun AccountTileGrid(
    views: List<AccountBalanceView>,
    onAccountClick: (Long) -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cells = views.size + 1 // + la celda de alta
    val rows = (cells + 2) / 3

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(rows) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                repeat(3) { col ->
                    val index = row * 3 + col
                    when {
                        index < views.size -> AccountTile(
                            view = views[index],
                            onClick = { onAccountClick(views[index].accountId) },
                            modifier = Modifier.weight(1f),
                        )

                        index == views.size -> AddAccountTile(
                            onClick = onAddAccount,
                            modifier = Modifier.weight(1f),
                        )

                        else -> Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Logo de la entidad sobre un chip blanco; si no hay, el glifo del tipo. */
@Composable
private fun InstitutionMark(view: AccountBalanceView, kind: AccountKind) {
    val logo = institutionLogo(view.institutionKey)
    if (logo != null) {
        Surface(
            shape = RoundedCornerShape(7.dp),
            color = Color.White,
            modifier = Modifier.size(26.dp),
        ) {
            Image(
                painter = painterResource(logo),
                contentDescription = null,
                modifier = Modifier
                    .padding(3.dp)
                    .fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    } else {
        Icon(
            imageVector = iconFor(kind),
            contentDescription = null,
            tint = onAccent(accountAccent(view)).copy(alpha = 0.85f),
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun iconFor(kind: AccountKind): ImageVector = when (kind) {
    AccountKind.CREDIT_CARD, AccountKind.BNPL -> Icons.Filled.CreditCard
    AccountKind.CRYPTO, AccountKind.CASH -> Icons.Filled.AccountBalanceWallet
    else -> Icons.Filled.AccountBalance
}

private fun tileValue(view: AccountBalanceView): String {
    val kind = AccountKind.fromKey(view.kindKey)
    val balance = view.balanceMinor
    val amountMinor = if (kind.isDebt) {
        val limit = view.creditLimitMinor
        when {
            view.creditAvailableMinor != null -> view.creditAvailableMinor
            limit != null -> (limit - abs(balance ?: 0L)).coerceAtLeast(0L)
            balance != null -> abs(balance)
            else -> return "—"
        }
    } else {
        balance ?: return "—"
    }
    val code = Currency.of(view.currencyCode).code
    return code + " " + MoneyFormatter.formatMinor(amountMinor, view.currencyCode, withSymbol = false)
}

/** Color de tinta para texto secundario sobre una tesela. */
internal fun tileSecondaryInk(accent: Color): Color = onAccent(accent).copy(alpha = 0.75f)
