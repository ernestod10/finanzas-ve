package dev.tubolsillo.app.ui.importcsv

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import dev.tubolsillo.app.data.source.csv.CsvField
import dev.tubolsillo.app.data.source.csv.CsvPresets
import dev.tubolsillo.app.ui.components.formatDate

/**
 * Importación de estados de cuenta CSV: picker SAF → template/preset → mapeo
 * de columnas → preview → importar con dedup/reconciliación.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvImportScreen(
    onBack: () -> Unit,
    viewModel: CsvImportViewModel = hiltViewModel(),
) {
    val accounts by viewModel.accounts.collectAsState()
    val template by viewModel.template.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val header by viewModel.header.collectAsState()
    val error by viewModel.error.collectAsState()
    val result by viewModel.result.collectAsState()
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.loadFile(it) } }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Importar CSV") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = { filePicker.launch(arrayOf("text/*", "text/csv")) }) {
                Text("Elegir archivo CSV")
            }

            Text(
                text = "Template",
                style = MaterialTheme.typography.titleLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CsvPresets.ALL.forEach { preset ->
                    FilterChip(
                        selected = template.id == preset.id,
                        onClick = { viewModel.selectTemplate(preset) },
                        label = { Text(preset.displayName, maxLines = 1) },
                    )
                }
            }

            if (header.isNotEmpty()) {
                Text(
                    text = "Mapeo de columnas (archivo)",
                    style = MaterialTheme.typography.titleLarge,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CsvField.entries.forEach { field ->
                        ColumnMapper(
                            field = field,
                            header = header,
                            selectedIndex = template.columnMap[field],
                            onSelect = { index -> viewModel.mapColumn(field, index) },
                        )
                    }
                }
            }

            if (rows.isNotEmpty()) {
                Text(
                    text = "Vista previa (${rows.size} filas)",
                    style = MaterialTheme.typography.titleLarge,
                )
                rows.take(5).forEachIndexed { i, row ->
                    Text(
                        text = "${formatDate(row.occurredAt)} — ${row.description} — " +
                            "${row.amountMinor / 100.0} ${row.currency}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (i < 4) Text("…", style = MaterialTheme.typography.labelSmall)
                }
            }

            if (rows.isNotEmpty()) {
                Text(
                    text = "Importar a la cuenta",
                    style = MaterialTheme.typography.titleLarge,
                )
                AccountDropdown(
                    accounts = accounts,
                    selectedId = selectedAccountId,
                    onSelect = { selectedAccountId = it },
                )
                Button(
                    enabled = selectedAccountId != null,
                    onClick = { viewModel.import(selectedAccountId!!) },
                ) { Text("Importar ${rows.size} movimientos") }
            }

            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            result?.let {
                Text(
                    text = "✓ Importados: ${it.inserted} · Reconciliados (ya existían): ${it.reconciled}" +
                        (if (it.failed > 0) " · Fallidos: ${it.failed}" else ""),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnMapper(
    field: CsvField,
    header: List<String>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.4f),
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(0.6f),
        ) {
            OutlinedTextField(
                value = selectedIndex?.let { header.getOrNull(it) ?: "Col $it" } ?: "—",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("(sin mapear)") },
                    onClick = { onSelect(null); expanded = false },
                )
                header.forEachIndexed { index, columnName ->
                    DropdownMenuItem(
                        text = { Text("$index: $columnName") },
                        onClick = { onSelect(index); expanded = false },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDropdown(
    accounts: List<dev.tubolsillo.app.core.db.entity.AccountEntity>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = accounts.firstOrNull { it.id == selectedId }?.name ?: "Elegir cuenta",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = { Text("${account.name} (${account.currency})") },
                    onClick = { onSelect(account.id); expanded = false },
                )
            }
        }
    }
}
