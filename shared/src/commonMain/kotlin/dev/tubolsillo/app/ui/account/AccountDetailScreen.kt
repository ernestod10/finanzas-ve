package dev.tubolsillo.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.db.dao.TransactionWithTags
import dev.tubolsillo.app.core.db.entity.TagEntity
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.ui.components.ACCOUNT_PALETTE
import dev.tubolsillo.app.ui.components.IOSScreen
import dev.tubolsillo.app.ui.components.TagChip
import dev.tubolsillo.app.ui.components.parseAccountColor
import dev.tubolsillo.app.ui.components.TxRow
import dev.tubolsillo.app.ui.components.formatDate
import kotlin.math.roundToInt
import org.koin.core.parameter.parametersOf
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    accountId: Long,
    onBack: () -> Unit,
    viewModel: AccountViewModel = koinViewModel(parameters = { parametersOf(accountId) }),
) {
    val account by viewModel.account.collectAsState()
    val snapshots by viewModel.snapshots.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val allCategories by viewModel.categories.collectAsState()
    val catById = remember(allCategories) { allCategories.associateBy { it.id } }
    val recentSnapshots = remember(snapshots) { snapshots.take(30) }
    var showBalanceEdit by remember { mutableStateOf(false) }
    var showLimitEdit by remember { mutableStateOf(false) }
    var showNameEdit by remember { mutableStateOf(false) }
    var taggingTx by remember { mutableStateOf<TransactionWithTags?>(null) }

    IOSScreen(
        title = account?.name ?: "Cuenta",
        onBack = onBack,
        actions = {
            val acc = account
            if (acc != null) {
                dev.tubolsillo.app.ui.components.HeaderAction(
                    when {
                        isBnplCashea(acc) -> "Disponible"
                        AccountKind.fromKey(acc.kind).isDebt -> "Deuda"
                        else -> "Saldo"
                    }
                ) { showBalanceEdit = true }
            }
        },
    ) {
        val acc = account
        if (acc != null) {
            item(key = "hero", contentType = "hero") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 4.dp),
                ) {
                    val kind = AccountKind.fromKey(acc.kind)
                    val isDebt = kind.isDebt
                    val limitMinor = acc.creditLimitMinor
                    val storedDebt = acc.lastBalanceMinor?.let { kotlin.math.abs(it) }
                    // El portal de algunos bancos (BNC) publica solo el disponible;
                    // otros (Bancaribe) publican los dos. Con el límite conocido,
                    // el dato que falte se deduce: deuda = límite − disponible.
                    val availableMinor = acc.creditAvailableMinor
                        ?: if (storedDebt != null && limitMinor != null) {
                            (limitMinor - storedDebt).coerceAtLeast(0L)
                        } else null
                    val debtMinor = storedDebt
                        ?: if (limitMinor != null && availableMinor != null) {
                            (limitMinor - availableMinor).coerceAtLeast(0L)
                        } else null

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = buildString {
                                append(Institution.fromKey(acc.institution).displayName)
                                append(" · ")
                                append(kind.displayName)
                                acc.accountNumberMask?.takeIf { it.isNotBlank() }?.let {
                                    append(" · ")
                                    append(it)
                                }
                            }.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showNameEdit = true }) { Text("Renombrar") }
                    }
                    Text(
                        text = if (isDebt) "DISPONIBLE" else "SALDO ACTUAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = (when {
                                isDebt -> availableMinor ?: debtMinor
                                else -> acc.lastBalanceMinor
                            })?.let { MoneyFormatter.formatMinor(it, acc.currency) } ?: "—",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontFeatureSettings = dev.tubolsillo.app.ui.theme.TNUM,
                                fontWeight = FontWeight.Bold,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showBalanceEdit = true }) {
                            Text(
                                when {
                                    isBnplCashea(acc) -> "Editar disponible"
                                    isDebt -> "Editar deuda"
                                    else -> "Editar saldo"
                                }
                            )
                        }
                    }
                    if (isDebt) {
                        val usedPct = if (limitMinor != null && limitMinor > 0 && debtMinor != null) {
                            (debtMinor.toFloat() / limitMinor).coerceIn(0f, 1f)
                        } else 0f
                        if (limitMinor != null) {
                            dev.tubolsillo.app.ui.components.ThinBar(
                                progress = usedPct,
                                over = debtMinor != null && debtMinor > limitMinor,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = (usedPct * 100).roundToInt().toString() + " % del límite usado",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        // La ficha de la tarjeta: cada dato en su renglón, sin
                        // mezclar la deuda con el disponible.
                        CreditFact("Deuda", debtMinor?.let { MoneyFormatter.formatMinor(it, acc.currency) } ?: "—")
                        acc.minPaymentMinor?.let { min ->
                            CreditFact("Pago mínimo", MoneyFormatter.formatMinor(min, acc.currency))
                        }
                        acc.creditDueAt?.let { due ->
                            CreditFact("Paga antes del", formatDate(due))
                        }
                        CreditFact("Límite", limitMinor?.let { MoneyFormatter.formatMinor(it, acc.currency) } ?: "—")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { showLimitEdit = true }) { Text("Editar límite") }
                        }
                    }
                    acc.lastBalanceAt?.let { updated ->
                        Text(
                            text = "Actualizado " + dev.tubolsillo.app.ui.components.dayLabel(updated),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Color",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 10.dp),
                        )
                        ACCOUNT_PALETTE.forEach { hex ->
                            val dotColor = parseAccountColor(hex)
                                ?: MaterialTheme.colorScheme.primary
                            val selected = acc.colorHex == hex
                            Surface(
                                onClick = { viewModel.updateColor(if (selected) null else hex) },
                                shape = CircleShape,
                                color = dotColor,
                                border = if (selected) androidx.compose.foundation.BorderStroke(
                                    2.dp,
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                ) else null,
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(if (selected) 26.dp else 20.dp),
                            ) {}
                        }
                    }
                }
            }
        }

        if (recentSnapshots.size > 1) {
            item(key = "snap-chart", contentType = "group") {
                BalanceHistoryCard(
                    snapshots = recentSnapshots,
                    currency = account?.currency ?: "USD",
                )
            }
        }

        if (transactions.isNotEmpty()) {
            item(key = "tx-header", contentType = "header") {
                dev.tubolsillo.app.ui.components.GroupHeader("Movimientos (${transactions.size})")
            }
            items(
                count = transactions.size,
                key = { i -> "tx-${transactions[i].transaction.id}" },
                contentType = { "tx_row" },
            ) { index ->
                val tx = transactions[index]
                dev.tubolsillo.app.ui.components.GroupedItemSurface(
                    isFirst = index == 0,
                    isLast = index == transactions.lastIndex,
                ) {
                    TxRow(
                        tx = tx,
                        accountName = null,
                        onClick = { taggingTx = tx },
                        categoryName = tx.transaction.categoryId?.let { catById[it]?.name },
                        categoryColorHex = tx.transaction.categoryId?.let { catById[it]?.colorHex },
                        showSeparator = index != transactions.lastIndex,
                    )
                }
            }
        } else if (snapshots.isEmpty()) {
            item(key = "empty") {
                dev.tubolsillo.app.ui.components.GroupFooter(
                    "Sin movimientos registrados en esta cuenta. Puedes registrar uno desde Inicio."
                )
            }
        }
    }

    if (showBalanceEdit) {
        val acc = account
        val initialVal = acc?.lastBalanceMinor?.let {
            val v = it / 100.0
            if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
        } ?: ""
        TextEditDialog(
            title = when {
                acc != null && isBnplCashea(acc) -> "Saldo disponible (" + acc.currency + ")"
                acc != null && AccountKind.fromKey(acc.kind).isDebt ->
                    "Deuda actual (" + acc.currency + ")"
                else -> "Saldo actual (" + (account?.currency ?: "") + ")"
            },
            initialValue = initialVal,
            onDismiss = { showBalanceEdit = false },
            onSave = { viewModel.updateBalance(it) },
        )
    }
    if (showNameEdit) {
        TextEditDialog(
            title = "Nombre de la cuenta",
            initialValue = account?.name ?: "",
            onDismiss = { showNameEdit = false },
            onSave = { viewModel.updateName(it) },
        )
    }
    if (showLimitEdit) {
        val acc = account
        val initialVal = acc?.creditLimitMinor?.let {
            val v = it / 100.0
            if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
        } ?: ""
        TextEditDialog(
            title = "Límite de crédito (" + (account?.currency ?: "") + ")",
            initialValue = initialVal,
            onDismiss = { showLimitEdit = false },
            onSave = { viewModel.updateCreditLimit(it) },
        )
    }
    taggingTx?.let { tx ->
        TagTransactionDialog(
            tx = tx,
            allTags = viewModel.allTags.collectAsState().value,
            categories = allCategories,
            onDismiss = { taggingTx = null },
            onTag = { tagName, learn ->
                viewModel.tagTransaction(tx.transaction.id, tagName, learn)
                taggingTx = null
            },
            onCategory = { categoryId ->
                viewModel.setTransactionCategory(tx.transaction.id, categoryId)
                taggingTx = null
            },
            onUpdateDetails = { newDesc, newMinor ->
                viewModel.updateTransactionDetails(
                    tx.transaction.id,
                    newDesc,
                    newMinor,
                    tx.transaction.currency,
                )
                taggingTx = null
            },
            onDelete = {
                viewModel.deleteTransaction(tx.transaction.id)
                taggingTx = null
            },
        )
    }
}

/** Renglón etiqueta/valor de la ficha de la tarjeta. */
@Composable
private fun CreditFact(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = dev.tubolsillo.app.ui.theme.TNUM,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Diálogo simple de edición de texto (saldo / límite). */
/**
 * Evolución del saldo de la cuenta. Reemplaza la lista de snapshots: una línea
 * dice en un segundo lo que quince filas de fecha y monto no decían.
 */
@Composable
private fun BalanceHistoryCard(
    snapshots: List<dev.tubolsillo.app.core.db.entity.BalanceSnapshotEntity>,
    currency: String,
) {
    // Los snapshots llegan del más nuevo al más viejo. Se toma UNO por día
    // (el último) para que la línea muestre evolución diaria y no 30 puntos
    // del mismo día.
    val daily = remember(snapshots) {
        snapshots
            .groupBy { dev.tubolsillo.app.core.time.MonthBuckets.startOfDay(it.capturedAt) }
            .values
            .mapNotNull { day -> day.maxByOrNull { it.capturedAt } }
            .sortedBy { it.capturedAt }
    }
    val values = remember(daily, currency) {
        daily.map {
            it.amountMinor / dev.tubolsillo.app.core.money.Money.pow10(
                dev.tubolsillo.app.core.money.Currency.of(it.currency).decimals
            )
        }
    }
    val labels = remember(daily) {
        if (daily.size < 2) {
            emptyList()
        } else {
            listOf(
                0,
                daily.size / 4,
                daily.size / 2,
                daily.size * 3 / 4,
                daily.lastIndex,
            ).distinct().map { dev.tubolsillo.app.ui.components.dayMonthLabel(daily[it].capturedAt) }
        }
    }

    dev.tubolsillo.app.ui.components.TubolsilloCard(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        dev.tubolsillo.app.ui.components.CardHeader(
            title = "Evolución del saldo",
            icon = Icons.Filled.ShowChart,
        )
        Spacer(Modifier.height(10.dp))
        if (daily.size < 2) {
            Text(
                text = "Saldo actual: " + snapshots.first().let {
                    MoneyFormatter.formatMinor(it.amountMinor, it.currency)
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "La gráfica aparece cuando haya capturas de al menos dos días distintos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            dev.tubolsillo.app.ui.components.charts.TrendChart(
                values = values,
                xLabels = labels,
                lineColor = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Un punto por día, según tus capturas de saldo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TextEditDialog(
    title: String,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onSave(text); onDismiss() },
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

/**
 * Diálogo de edición y etiquetado en vivo: permite cambiar categoría de
 * presupuesto (aplicando a comercios iguales si "aprender" está activo),
 * editar descripción/monto, agregar etiquetas o eliminar el movimiento.
 */
@Composable
fun TagTransactionDialog(
    tx: TransactionWithTags,
    allTags: List<TagEntity>,
    categories: List<dev.tubolsillo.app.core.db.entity.CategoryEntity>,
    onDismiss: () -> Unit,
    onTag: (tagName: String, learn: Boolean) -> Unit,
    onCategory: (Long?) -> Unit,
    onUpdateDetails: ((description: String, amountMinor: Long) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var newTag by remember { mutableStateOf("") }
    var learn by remember { mutableStateOf(true) }
    var editingDetails by remember { mutableStateOf(false) }
    var descText by remember(tx.transaction.id) { mutableStateOf(tx.transaction.description) }
    val initialAbsMajor = remember(tx.transaction.id, tx.transaction.amountMinor) {
        val v = kotlin.math.abs(tx.transaction.amountMinor) / 100.0
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
    }
    var amountText by remember(tx.transaction.id) { mutableStateOf(initialAbsMajor) }
    var isExpense by remember(tx.transaction.id) { mutableStateOf(tx.transaction.amountMinor <= 0) }
    val alreadyTagged = tx.tags.map { it.name }.toSet()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tx.transaction.description,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (onUpdateDetails != null) {
                    TextButton(onClick = { editingDetails = !editingDetails }) {
                        Text(if (editingDetails) "Ocultar" else "Editar")
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editingDetails && onUpdateDetails != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isExpense,
                            onClick = { isExpense = true },
                            label = { Text("Gasto") },
                        )
                        FilterChip(
                            selected = !isExpense,
                            onClick = { isExpense = false },
                            label = { Text("Ingreso") },
                        )
                    }
                    OutlinedTextField(
                        value = descText,
                        onValueChange = { descText = it },
                        label = { Text("Descripción") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Monto (" + tx.transaction.currency + ")") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Text(
                    text = "Categoría (presupuesto)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = tx.transaction.categoryId == null,
                            onClick = { onCategory(null) },
                            label = { Text("Sin categoría") },
                        )
                    }
                    items(categories.size) { i ->
                        val cat = categories[i]
                        FilterChip(
                            selected = tx.transaction.categoryId == cat.id,
                            onClick = { onCategory(cat.id) },
                            label = { Text(cat.name, maxLines = 1) },
                            leadingIcon = {
                                if (cat.colorHex != null) {
                                    Box(
                                        modifier = Modifier.size(10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = parseAccountColor(cat.colorHex)
                                                ?: MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(10.dp),
                                        ) {}
                                    }
                                }
                            },
                        )
                    }
                }
                Text(
                    text = "Etiquetas (opcional)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (allTags.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(allTags) { tag ->
                            FilterChip(
                                selected = alreadyTagged.contains(tag.name),
                                onClick = { onTag(tag.name, false) },
                                label = { Text(tag.name, maxLines = 1) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    label = { Text("Nueva etiqueta (ej. Comida)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = learn, onCheckedChange = { learn = it })
                    Text(
                        text = "Aprender comercio para próximas veces",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.align(Alignment.Start),
                    ) {
                        Text("Eliminar movimiento", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            if (editingDetails && onUpdateDetails != null) {
                val parsedMinor = dev.tubolsillo.app.core.money.AmountNormalizer.parseMinor(
                    amountText,
                    tx.transaction.currency,
                )
                TextButton(
                    enabled = descText.isNotBlank() && parsedMinor != null,
                    onClick = {
                        val minor = parsedMinor ?: return@TextButton
                        val signed = if (isExpense) -kotlin.math.abs(minor) else kotlin.math.abs(minor)
                        onUpdateDetails(descText.trim(), signed)
                    },
                ) { Text("Guardar cambios") }
            } else {
                TextButton(
                    enabled = newTag.isNotBlank(),
                    onClick = { onTag(newTag.trim(), learn) },
                ) { Text("Etiquetar") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
    )
}

private fun isBnplCashea(acc: dev.tubolsillo.app.core.db.entity.AccountEntity): Boolean =
    dev.tubolsillo.app.domain.model.AccountKind.fromKey(acc.kind) == dev.tubolsillo.app.domain.model.AccountKind.BNPL &&
        dev.tubolsillo.app.domain.model.Institution.fromKey(acc.institution) == dev.tubolsillo.app.domain.model.Institution.CASHEA

