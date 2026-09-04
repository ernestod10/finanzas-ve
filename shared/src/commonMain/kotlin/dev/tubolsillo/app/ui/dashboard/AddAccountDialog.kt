package dev.tubolsillo.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.core.money.Currency
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.ui.components.ACCOUNT_PALETTE
import dev.tubolsillo.app.ui.components.parseAccountColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onSave: (institution: Institution, name: String, kind: AccountKind, currencyCode: String, initialBalanceMinor: Long?, creditLimitMinor: Long?, colorHex: String?) -> Unit,
) {
    var institution by remember { mutableStateOf(Institution.BANESCO) }
    var kind by remember { mutableStateOf(AccountKind.CHECKING) }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
        Text(
            when {
                kind == AccountKind.CREDIT_CARD -> "Nueva tarjeta de crédito"
                kind == AccountKind.BNPL && institution == Institution.CASHEA -> "Nueva línea Cashea"
                else -> "Nueva cuenta"
            }
        )
    },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (institution == Institution.OTHER) "Nombre del banco o institución" else "Nombre (ej. Banesco TDC)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Banco / institución como dropdown con todas las opciones.
                InstitutionDropdown(
                    selected = institution,
                    onSelect = {
                        institution = it
                        currencyCode = it.defaultCurrency
                        if (it == Institution.CASHEA) {
                            kind = AccountKind.BNPL
                            currencyCode = "USD"
                            if (name.isBlank()) name = "Cashea · Principal"
                        } else if (kind == AccountKind.CREDIT_CARD && name.isBlank()) {
                            name = it.displayName + " TDC"
                        }
                    },
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    listOf(
                        AccountKind.CHECKING to "Cuenta",
                        AccountKind.CREDIT_CARD to "Tarjeta de crédito",
                        AccountKind.BNPL to "Cashea",
                        AccountKind.CASH to "Efectivo",
                        AccountKind.CRYPTO to "Crypto",
                        AccountKind.SAVINGS to "Ahorro",
                    ).forEach { (k, label) ->
                        FilterChip(
                            selected = kind == k,
                            onClick = {
                                kind = k
                                if (k.isDebt && currencyCode != "USD") currencyCode = "USD"
                                if (k == AccountKind.CREDIT_CARD && name.isBlank()) {
                                    name = institution.displayName + " TDC"
                                }
                                if (k == AccountKind.BNPL) {
                                    institution = Institution.CASHEA
                                    currencyCode = "USD"
                                    if (name.isBlank()) {
                                        name = if (casheaLane == 0) "Cashea · Principal"
                                        else "Cashea · Cotidiana"
                                    }
                                }
                            },
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }
                if (kind == AccountKind.BNPL && institution == Institution.CASHEA) {
                    Text(
                        text = "La línea Cashea se expresa en USD — equivale a tu límite en Bs. al cambio BCV.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Currency.USD, Currency.VES, Currency.USDT, Currency.BTC).forEach { c ->
                            FilterChip(
                                selected = currencyCode == c.code,
                                onClick = { currencyCode = c.code },
                                label = { Text(c.code) },
                            )
                        }
                    }
                }
                if (isDebt) {
                    val isCashea = kind == AccountKind.BNPL && institution == Institution.CASHEA
                    OutlinedTextField(
                        value = creditLimit,
                        onValueChange = { creditLimit = it },
                        label = { Text(if (kind == AccountKind.BNPL) "Límite de la línea (USD)" else "Límite de crédito (" + currencyCode + ")") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = initialBalance,
                        onValueChange = { initialBalance = it },
                        label = { Text(
                            if (isCashea) "Saldo disponible (USD) — el que ves en la app de Cashea"
                            else if (kind == AccountKind.BNPL) "Deuda actual de la línea (" + currencyCode + ")"
                            else "Deuda actual (" + currencyCode + ")"
                        ) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = initialBalance,
                        onValueChange = { initialBalance = it },
                        label = { Text("Saldo inicial (" + currencyCode + ")") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (kind == AccountKind.BNPL && institution == Institution.CASHEA) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Principal", "Cotidiana").forEachIndexed { i, lane ->
                            FilterChip(
                                selected = casheaLane == i,
                                onClick = {
                                    casheaLane = i
                                    name = "Cashea · " + lane
                                },
                                label = { Text("Línea " + lane, maxLines = 1) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
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
            ) { Text("Crear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstitutionDropdown(
    selected: Institution,
    onSelect: (Institution) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Banco / institución") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Institution.entries.forEach { inst ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = inst.displayName,
                            style = if (inst == selected) {
                                MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary)
                            } else MaterialTheme.typography.bodyLarge,
                        )
                    },
                    onClick = {
                        onSelect(inst)
                        expanded = false
                    },
                )
            }
        }
    }
}
