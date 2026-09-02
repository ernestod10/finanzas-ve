package dev.tubolsillo.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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

@Composable
fun AddAccountDialog(
    onDismiss: () -> Unit,
    onSave: (institution: Institution, name: String, kind: AccountKind, currencyCode: String, initialBalanceMinor: Long?, creditLimitMinor: Long?) -> Unit,
) {
    var institution by remember { mutableStateOf(Institution.BANESCO) }
    var kind by remember { mutableStateOf(AccountKind.CHECKING) }
    var currencyCode by remember { mutableStateOf("VES") }
    var name by remember { mutableStateOf("") }
    var initialBalance by remember { mutableStateOf("") }
    var creditLimit by remember { mutableStateOf("") }

    val isDebt = kind.isDebt
    val initialMinor = AmountNormalizer.parseMinor(initialBalance, currencyCode)
    val limitMinor = AmountNormalizer.parseMinor(creditLimit, currencyCode)
    val canSave = name.isNotBlank() && (initialBalance.isBlank() || initialMinor != null) &&
        (!isDebt || creditLimit.isBlank() || limitMinor != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva cuenta") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre (ej. Banesco TDC)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Institution.entries.forEach { inst ->
                        FilterChip(
                            selected = institution == inst,
                            onClick = { institution = inst },
                            label = { Text(inst.displayName, maxLines = 1) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AccountKind.entries.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = {
                                kind = k
                                if (k.isDebt && currencyCode != "USD") currencyCode = "USD"
                            },
                            label = { Text(k.displayName, maxLines = 1) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Currency.USD, Currency.VES, Currency.USDT, Currency.BTC).forEach { c ->
                        FilterChip(
                            selected = currencyCode == c.code,
                            onClick = { currencyCode = c.code },
                            label = { Text(c.code) },
                        )
                    }
                }
                OutlinedTextField(
                    value = initialBalance,
                    onValueChange = { initialBalance = it },
                    label = { Text(if (isDebt) "Deuda actual (" + currencyCode + ")" else "Saldo inicial (" + currencyCode + ")") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isDebt) {
                    OutlinedTextField(
                        value = creditLimit,
                        onValueChange = { creditLimit = it },
                        label = { Text("Límite de crédito (" + currencyCode + ")") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val initial = if (isDebt && initialMinor != null) -kotlin.math.abs(initialMinor) else initialMinor
                    onSave(institution, name.trim(), kind, currencyCode, initial, limitMinor)
                },
            ) { Text("Crear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
