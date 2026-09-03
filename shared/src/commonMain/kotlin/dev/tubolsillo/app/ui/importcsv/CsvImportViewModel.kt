package dev.tubolsillo.app.ui.importcsv

import androidx.lifecycle.ViewModel
import dev.tubolsillo.app.core.db.entity.AccountEntity
import dev.tubolsillo.app.data.source.csv.CsvField
import dev.tubolsillo.app.data.source.csv.CsvImportResult
import dev.tubolsillo.app.data.source.csv.CsvImporter
import dev.tubolsillo.app.data.source.csv.CsvPresets
import dev.tubolsillo.app.data.source.csv.CsvRow
import dev.tubolsillo.app.data.source.csv.CsvTemplate
import dev.tubolsillo.app.domain.repo.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

/** Importación CSV: el contenido llega del picker de la plataforma (expect). */
class CsvImportViewModel(
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

    fun loadContent(content: String?) {
        if (content == null) return
        _error.value = null
        _result.value = null
        lastCsvText = content
        _header.value = content.lineSequence().firstOrNull()
            ?.split(',')?.map { it.trim() } ?: emptyList()
        reparse()
    }

    fun selectTemplate(template: CsvTemplate) {
        _template.value = template
        reparse()
    }

    private fun reparse() {
        if (lastCsvText.isBlank()) return
        val t = _template.value
        _rows.value = importer.parseRows(lastCsvText, t)
        if (_rows.value.isEmpty()) {
            _error.value = "Sin filas válidas con este template — ajusta el mapeo o el formato."
        }
    }

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
