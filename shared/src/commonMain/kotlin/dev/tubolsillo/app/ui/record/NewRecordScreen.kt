package dev.tubolsillo.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.core.money.Currency
import dev.tubolsillo.app.core.time.MonthBuckets
import dev.tubolsillo.app.core.time.nowMillis
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.TransactionCategory
import dev.tubolsillo.app.ui.components.AmountDisplay
import dev.tubolsillo.app.ui.components.AmountKeypad
import dev.tubolsillo.app.ui.components.dayLabel
import dev.tubolsillo.app.ui.dashboard.AddAccountDialog
import dev.tubolsillo.app.ui.dashboard.DashboardViewModel
import dev.tubolsillo.app.ui.review.EntryDraftHolder
import dev.tubolsillo.app.ui.theme.TubolsilloColors
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs

/**
 * Nuevo registro a pantalla completa, como en la referencia: tipo, monto con
 * teclado propio, cuenta, categoría, fecha y descripción.
 *
 * Guarda con `DashboardViewModel.addManualTransaction`, que ya conoce el
 * agendado de cuotas de Cashea; la captura automática no interviene.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRecordScreen(
    initialIncome: Boolean,
    onDone: () -> Unit,
    initialScan: Boolean = false,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val dashboard by viewModel.dashboard.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts = dashboard?.accounts ?: emptyList()

    // Si venimos de la cola de revisión, el formulario arranca con lo que el
    // modelo leyó; el usuario lo corrige y confirma.
    val draftHolder: EntryDraftHolder = koinInject()
    val draft = remember { draftHolder.take() }

    var isExpense by rememberSaveable {
        mutableStateOf(draft?.let { !it.income } ?: !initialIncome)
    }
    var amountText by rememberSaveable { mutableStateOf(draft?.amount ?: "") }
    var accountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryId by rememberSaveable { mutableStateOf(draft?.categoryId) }
    var description by rememberSaveable { mutableStateOf(draft?.description ?: "") }
    var occurredAt by rememberSaveable { mutableStateOf(nowMillis()) }
    var casheaEnabled by rememberSaveable { mutableStateOf(true) }
    var showAccounts by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var describeText by rememberSaveable { mutableStateOf("") }

    var scanError by remember { mutableStateOf<String?>(null) }

    // Instantánea inicial para saber si hay cambios sin guardar.
    val initialAmount = remember { amountText }
    val initialDescription = remember { description }
    val dirty = amountText != initialAmount || description != initialDescription
    var confirmDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val interpreted by viewModel.interpreted.collectAsState()
    val interpreting by viewModel.interpreting.collectAsState()
    val analyzerAvailability by viewModel.analyzerAvailability.collectAsState()

    // "gasté 20 en taxi ayer" → precarga monto, descripción, tipo, fecha y categoría.
    androidx.compose.runtime.LaunchedEffect(interpreted) {
        interpreted?.let { reading ->
            reading.amount?.let { value ->
                amountText = if (value == value.toLong().toDouble()) value.toLong().toString()
                else value.toString()
            }
            reading.merchant?.takeIf { it.isNotBlank() }?.let { description = it }
            reading.kind?.let { kind ->
                if (kind.contains("income", true) || kind.contains("credit", true)) {
                    isExpense = false
                } else if (kind.contains("expense", true) || kind.contains("purchase", true)) {
                    isExpense = true
                }
            }
            reading.dayOffset?.let { offset -> occurredAt = nowMillis() + offset * DAY_MS }
            reading.category?.let { name ->
                categoryId = categories.firstOrNull { it.name.equals(name, true) }?.id
            }
            viewModel.clearInterpreted()
        }
    }

    val account = accounts.firstOrNull { it.id == (accountId ?: accounts.firstOrNull()?.id) }
    val currency = account?.currency ?: "USD"
    val amountMinor = AmountNormalizer.parseMinor(amountText, currency)
    // Escaneo de factura: OCR en el teléfono. Primero se precarga con las
    // reglas (funciona sin IA) y después el modelo refina si está disponible.
    val scanReceipt = dev.tubolsillo.app.core.platform.rememberReceiptScanner { text, error ->
        scanError = error
        if (!text.isNullOrBlank()) {
            val parsed = dev.tubolsillo.app.domain.receipts.ReceiptParser.parse(text, currency)
            parsed.totalMinor?.let { minor ->
                val value = minor / 100.0
                amountText = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
            }
            parsed.merchant?.let { description = it }
            parsed.occurredAt?.let { occurredAt = it }
            viewModel.analyzeReceipt(text)
        }
    }

    // Desde el menú del "+", la opción Factura entra directo al escáner.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (initialScan) scanReceipt()
    }

    val canSave = account != null && amountMinor != null && description.isNotBlank()
    val isCashea = account?.let { Institution.fromKey(it.institution) == Institution.CASHEA } == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { if (dirty) confirmDiscard = true else onDone() }) { Text("Cancelar") }
            Text(
                text = "Nuevo registro",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            TextButton(
                enabled = canSave && !saving,
                onClick = {
                    val acc = account ?: return@TextButton
                    val minor = amountMinor ?: return@TextButton
                    val signed = if (isExpense) -abs(minor) else abs(minor)
                    saving = true
                    saveError = null
                    scope.launch {
                        runCatching {
                            viewModel.saveManualTransaction(
                                accountId = acc.id,
                                amountMinor = signed,
                                currencyCode = acc.currency,
                                description = description.trim(),
                                category = TransactionCategory.MERCHANT,
                                userCategoryId = categoryId,
                                casheaInstallmentMinor = if (isExpense && isCashea && casheaEnabled) abs(minor) else null,
                                occurredAt = occurredAt,
                            )
                        }.onSuccess { onDone() }
                            .onFailure { saveError = "No se pudo guardar. Inténtalo otra vez." }
                        saving = false
                    }
                },
            ) { Text(if (saving) "Guardando…" else "Guardar", fontWeight = FontWeight.SemiBold) }
        }

        Spacer(Modifier.height(8.dp))
        // Alta en lenguaje natural: el modelo interpreta y el formulario se
        // precarga; el usuario siempre revisa antes de guardar.
        OutlinedTextField(
            value = describeText,
            onValueChange = { describeText = it },
            label = { Text("Describir (ej. gasté 20 en taxi ayer)") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    analyzerAvailability == dev.tubolsillo.app.domain.ai.AnalyzerAvailability.UNAVAILABLE ->
                        "Requiere Gemini Nano"

                    interpreting -> "Interpretando…"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { viewModel.interpretEntry(describeText) },
                enabled = describeText.isNotBlank() && !interpreting,
            ) { Text("Interpretar") }
            TextButton(onClick = scanReceipt) { Text("Escanear factura") }
        }
        scanError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        saveError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(8.dp))
        TypeSegment(isExpense = isExpense, onChange = { isExpense = it })

        Spacer(Modifier.height(20.dp))
        AmountDisplay(text = amountText, currencyCode = currency)
        Spacer(Modifier.height(20.dp))

        SelectorRow(
            icon = { Icon(Icons.Filled.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary) },
            label = account?.name ?: "Elegir cuenta",
            value = account?.currency ?: "—",
            onClick = { showAccounts = true },
        )
        Spacer(Modifier.height(8.dp))
        SelectorRow(
            icon = { Icon(Icons.Filled.Category, null, tint = MaterialTheme.colorScheme.primary) },
            label = categories.firstOrNull { it.id == categoryId }?.name ?: "Sin categoría",
            value = "Opcional",
            onClick = { showCategories = true },
        )

        Spacer(Modifier.height(14.dp))
        DateChips(
            occurredAt = occurredAt,
            onToday = { occurredAt = nowMillis() },
            onYesterday = { occurredAt = nowMillis() - DAY_MS },
            onPick = { showDate = true },
        )

        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Descripción (ej. FARMACIA X)") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        if (isExpense && isCashea) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = casheaEnabled, onCheckedChange = { casheaEnabled = it })
                Text(
                    text = "Agendar 3 cuotas cada 15 días (se ven en Créditos)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        AmountKeypad(
            value = amountText,
            onValueChange = { amountText = it },
            decimals = Currency.of(currency).decimals,
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("¿Descartar cambios?") },
            text = { Text("Lo que escribiste no se ha guardado.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onDone()
                    },
                ) { Text("Descartar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Seguir editando") }
            },
        )
    }

    if (showAccounts) {
        AlertDialog(
            onDismissRequest = { showAccounts = false },
            title = { Text("Cuenta") },
            text = {
                Column {
                    accounts.forEach { acc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    accountId = acc.id
                                    showAccounts = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(acc.name, modifier = Modifier.weight(1f))
                            Text(
                                text = acc.currency,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAccounts = false }) { Text("Cerrar") }
            },
        )
    }

    if (showCategories) {
        AlertDialog(
            onDismissRequest = { showCategories = false },
            title = { Text("Categoría") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    CategoryOption("Sin categoría", selected = categoryId == null) {
                        categoryId = null
                        showCategories = false
                    }
                    categories.forEach { category ->
                        CategoryOption(category.name, selected = categoryId == category.id) {
                            categoryId = category.id
                            showCategories = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCategories = false }) { Text("Cerrar") }
            },
        )
    }

    if (showDate) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = MonthBuckets.toPickerMillis(occurredAt),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        picker.selectedDateMillis?.let { picked ->
                            val timeOfDay = nowMillis() - MonthBuckets.startOfDay(nowMillis())
                            occurredAt = MonthBuckets.fromPickerMillis(picked) + timeOfDay
                        }
                        showDate = false
                    },
                ) { Text("Listo") }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = picker)
        }
    }
}

@Composable
private fun TypeSegment(isExpense: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = TubolsilloColors.tileFill,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            SegmentOption("Gasto", selected = isExpense, modifier = Modifier.weight(1f)) {
                onChange(true)
            }
            SegmentOption("Ingreso", selected = !isExpense, modifier = Modifier.weight(1f)) {
                onChange(false)
            }
        }
    }
}

@Composable
private fun SegmentOption(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
        modifier = modifier
            .heightIn(min = 38.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SelectorRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = TubolsilloColors.tileFill,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DateChips(
    occurredAt: Long,
    onToday: () -> Unit,
    onYesterday: () -> Unit,
    onPick: () -> Unit,
) {
    val today = MonthBuckets.startOfDay(nowMillis())
    val yesterday = today - DAY_MS
    val day = MonthBuckets.startOfDay(occurredAt)
    val todaySelected = day == today
    val yesterdaySelected = day == yesterday

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateChip("Hoy", todaySelected, onToday, Modifier.weight(1f))
        DateChip("Ayer", yesterdaySelected, onYesterday, Modifier.weight(1f))
        DateChip(
            label = if (!todaySelected && !yesterdaySelected) dayLabel(occurredAt) else "Otra fecha",
            selected = !todaySelected && !yesterdaySelected,
            onClick = onPick,
            modifier = Modifier.weight(1.2f),
        )
    }
}

@Composable
private fun DateChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else TubolsilloColors.tileFill,
        modifier = modifier
            .heightIn(min = 38.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CategoryOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Text(
                    text = "Elegida",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        )
    }
}

private const val DAY_MS = 86_400_000L
