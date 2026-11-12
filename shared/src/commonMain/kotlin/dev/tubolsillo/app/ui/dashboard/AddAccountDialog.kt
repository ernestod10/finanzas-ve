package dev.tubolsillo.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.core.money.Currency
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.ui.components.ACCOUNT_PALETTE
import dev.tubolsillo.app.ui.components.InstitutionLogoBadge
import dev.tubolsillo.app.ui.components.parseAccountColor
import dev.tubolsillo.app.ui.theme.TubolsilloColors

/**
 * Alta de cuenta, tarjeta o línea de crédito como hoja inferior a pantalla
 * completa: la institución se elige con su logo, el tipo con iconos y el resto
 * queda agrupado en tarjetas, en vez del formulario apretado que había antes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    initialKind: AccountKind = AccountKind.CHECKING,
    onSave: (institution: Institution, name: String, kind: AccountKind, currencyCode: String, initialBalanceMinor: Long?, creditLimitMinor: Long?, colorHex: String?) -> Unit,
) {
    var institution by remember { mutableStateOf(Institution.BANESCO) }
    var kind by remember { mutableStateOf(initialKind) }
    var casheaLane by remember { mutableStateOf(0) } // 0 = Principal, 1 = Cotidiana
    var currencyCode by remember { mutableStateOf("VES") }
    var name by remember { mutableStateOf("") }
    var initialBalance by remember { mutableStateOf("") }
    var creditLimit by remember { mutableStateOf("") }
    var colorHex by remember { mutableStateOf<String?>(null) }

    val isDebt = kind.isDebt
    val initialMinor = AmountNormalizer.parseMinor(initialBalance, currencyCode)
    val limitMinor = AmountNormalizer.parseMinor(creditLimit, currencyCode)
    val isCashea = kind == AccountKind.BNPL && institution == Institution.CASHEA
    val canSave = name.isNotBlank() && (initialBalance.isBlank() || initialMinor != null) &&
        (!isDebt || creditLimit.isBlank() || limitMinor != null) &&
        (!isCashea || (limitMinor != null && initialMinor != null))

    val title = when {
        kind == AccountKind.CREDIT_CARD -> "Nueva tarjeta"
        kind == AccountKind.BNPL && institution == Institution.CASHEA -> "Nueva línea Cashea"
        else -> "Nueva cuenta"
    }
    val subtitle = when {
        kind == AccountKind.CREDIT_CARD -> "Límite, deuda actual y color de la tarjeta."
        isCashea -> "El límite de tu línea y lo disponible que ves en Cashea."
        else -> "Banco, moneda y saldo con el que arranca."
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Tipo: icono + etiqueta, en un renglón que se desliza.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                KindOption("Cuenta", Icons.Filled.AccountBalanceWallet, kind == AccountKind.CHECKING) {
                    kind = AccountKind.CHECKING
                }
                KindOption("Tarjeta", Icons.Filled.CreditCard, kind == AccountKind.CREDIT_CARD) {
                    kind = AccountKind.CREDIT_CARD
                    if (name.isBlank()) name = institution.displayName + " TDC"
                }
                KindOption("Cashea", Icons.Filled.ShoppingBag, kind == AccountKind.BNPL) {
                    kind = AccountKind.BNPL
                    institution = Institution.CASHEA
                    currencyCode = "USD"
                    if (name.isBlank()) name = "Cashea · Principal"
                }
                KindOption("Efectivo", Icons.Filled.Payments, kind == AccountKind.CASH) {
                    kind = AccountKind.CASH
                }
                KindOption("Crypto", Icons.Filled.CurrencyBitcoin, kind == AccountKind.CRYPTO) {
                    kind = AccountKind.CRYPTO
                    currencyCode = "USDT"
                }
                KindOption("Ahorro", Icons.Filled.Savings, kind == AccountKind.SAVINGS) {
                    kind = AccountKind.SAVINGS
                }
            }

            // Institución con su logo.
            SectionLabel("Institución")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                Institution.entries.forEach { inst ->
                    val selected = inst == institution
                    Surface(
                        onClick = {
                            institution = inst
                            currencyCode = inst.defaultCurrency
                            if (inst == Institution.CASHEA) {
                                kind = AccountKind.BNPL
                                currencyCode = "USD"
                                if (name.isBlank()) name = "Cashea · Principal"
                            } else if (inst == Institution.OTHER) {
                                if (name.isBlank()) name = ""
                            } else {
                                if (kind == AccountKind.BNPL) kind = AccountKind.CHECKING
                                if (kind == AccountKind.CREDIT_CARD && name.isBlank()) {
                                    name = inst.displayName + " TDC"
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else TubolsilloColors.tileFill,
                        border = if (selected) {
                            androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                MaterialTheme.colorScheme.primary,
                            )
                        } else null,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            InstitutionLogoBadge(
                                institutionKey = inst.key,
                                displayName = inst.displayName,
                                size = 30,
                            )
                            Text(
                                text = inst.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.width(92.dp),
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = {
                    Text(
                        if (institution == Institution.OTHER) "Nombre del banco o institución"
                        else if (isDebt) "Nombre (ej. Bancaribe TDC)" else "Nombre (ej. Banesco principal)"
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (!isCashea) {
                SectionLabel("Moneda")
                SegmentedCodes(
                    codes = listOf(Currency.USD, Currency.VES, Currency.USDT, Currency.BTC).map { it.code },
                    selected = currencyCode,
                    onSelect = { currencyCode = it },
                )
            } else {
                Text(
                    text = "La línea Cashea se expresa en USD: equivale a tu límite en Bs. al cambio BCV.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionLabel(if (isDebt) "Tarjeta" else "Saldo")
            if (isDebt) {
                OutlinedTextField(
                    value = creditLimit,
                    onValueChange = { creditLimit = it },
                    label = {
                        Text(if (kind == AccountKind.BNPL) "Límite de la línea (USD)"
                        else "Límite de crédito (" + currencyCode + ")")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = initialBalance,
                    onValueChange = { initialBalance = it },
                    label = {
                        Text(
                            if (isCashea) "Disponible en la app de Cashea (USD)"
                            else if (kind == AccountKind.BNPL) "Deuda actual de la línea (" + currencyCode + ")"
                            else "Deuda actual (" + currencyCode + ")"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isCashea) {
                    SegmentedCodes(
                        codes = listOf("Principal", "Cotidiana"),
                        selected = if (casheaLane == 0) "Principal" else "Cotidiana",
                        onSelect = { lane ->
                            casheaLane = if (lane == "Principal") 0 else 1
                            name = "Cashea · " + lane
                        },
                    )
                }
            } else {
                OutlinedTextField(
                    value = initialBalance,
                    onValueChange = { initialBalance = it },
                    label = { Text("Saldo inicial (" + currencyCode + ")") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Color: la cuenta se pinta en Inicio y en las gráficas.
            SectionLabel("Color")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ACCOUNT_PALETTE.forEach { hex ->
                    val selected = colorHex == hex
                    Surface(
                        onClick = { colorHex = hex },
                        shape = CircleShape,
                        color = parseAccountColor(hex) ?: MaterialTheme.colorScheme.primary,
                        border = if (selected) {
                            androidx.compose.foundation.BorderStroke(
                                2.5.dp,
                                MaterialTheme.colorScheme.onSurface,
                            )
                        } else null,
                        modifier = Modifier.size(if (selected) 30.dp else 26.dp),
                    ) {}
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(top = 4.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val initial = if (isCashea && limitMinor != null && initialMinor != null) {
                            val deuda = (limitMinor - kotlin.math.abs(initialMinor)).coerceAtLeast(0L)
                            -deuda
                        } else if (isDebt && initialMinor != null) {
                            -kotlin.math.abs(initialMinor)
                        } else {
                            initialMinor
                        }
                        onSave(institution, name.trim(), kind, currencyCode, initial, limitMinor, colorHex)
                    },
                ) {
                    Text(if (isDebt) "Crear tarjeta" else "Crear cuenta")
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun KindOption(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else TubolsilloColors.tileFill,
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else null,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Fila de opciones exclusivas (moneda, línea de Cashea). */
@Composable
private fun SegmentedCodes(
    codes: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        codes.forEach { code ->
            val isSelected = code == selected
            Surface(
                onClick = { onSelect(code) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else TubolsilloColors.tileFill,
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}
