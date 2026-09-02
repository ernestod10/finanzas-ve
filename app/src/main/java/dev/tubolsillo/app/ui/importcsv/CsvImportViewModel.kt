package dev.tubolsillo.app.ui.importcsv

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tubolsillo.app.core.db.entity.AccountEntity
import dev.tubolsillo.app.data.source.csv.CsvField
import dev.tubolsillo.app.data.source.csv.CsvImportResult
import dev.tubolsillo.app.data.source.csv.CsvImporter
import dev.tubolsillo.app.data.source.csv.CsvRow
import dev.tubolsillo.app.data.source.csv.CsvPresets
import dev.tubolsillo.app.data.source.csv.CsvTemplate
import dev.tubolsillo.app.domain.repo.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CsvImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importer: CsvImporter,
    accountRepository: AccountRepository,
) : ViewModel() {

    val accounts: StateFlow<List<AccountEntity>> = accountRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _template = MutableStateFlow(CsvPresets.GENERIC)
    val template: StateFlow<CsvTemplate> = _template

    private val _rows = MutableStateFlow<List<CsvRow>>(emptyList())
    val rows: StateFlow<List<CsvRow>> = _rows

    private val _header = MutableStateFlow<List<String>>(emptyList())
    val header: StateFlow<List<String>> = _header

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _result = MutableStateFlow<CsvImportResult?>(null)
    val result: StateFlow<CsvImportResult?> = _result

    private var lastCsvText: String = ""

    fun loadFile(uri: Uri) {
        viewModelScope.launch {
            _error.value = null
            _result.value = null
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: run {
                        _error.value = "No se pudo leer el archivo"
                        return@launch
                    }
                lastCsvText = text
                // Header crudo para el mapeo manual.
                _header.value = text.lineSequence().firstOrNull()
                    ?.split(',')?.map { it.trim() } ?: emptyList()
                reparse()
            } catch (e: Exception) {
                _error.value = "Error leyendo archivo: ${e.message}"
            }
        }
    }

    fun selectTemplate(template: CsvTemplate) {
        _template.value = template
        reparse()
    }

    /** Re-parsea con el template actual (y su mapeo manual si es generic). */
    private fun reparse() {
        if (lastCsvText.isBlank()) return
        val t = _template.value
        _rows.value = importer.parseRows(lastCsvText, t)
        if (_rows.value.isEmpty()) {
            _error.value = "Sin filas válidas con este template — ajusta el mapeo o el formato."
        }
    }

    /** Actualiza el mapeo manual de una columna en el template genérico. */
    fun mapColumn(field: CsvField, columnIndex: Int?) {
        val current = _template.value
        val newMap = current.columnMap.toMutableMap()
        if (columnIndex == null) newMap.remove(field) else newMap[field] = columnIndex
        _template.value = current.copy(columnMap = newMap)
        reparse()
    }

    fun import(accountId: Long) {
        viewModelScope.launch {
            _result.value = importer.importRows(accountId, _rows.value, _template.value)
        }
    }
}
