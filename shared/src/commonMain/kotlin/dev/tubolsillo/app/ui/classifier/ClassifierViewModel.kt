package dev.tubolsillo.app.ui.classifier

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.domain.ai.CaptureAnalyzer
import dev.tubolsillo.app.domain.repo.CategoryRepository
import dev.tubolsillo.app.domain.repo.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Una sugerencia de categoría para un movimiento ya guardado. */
data class ClassProposal(
    val txId: Long,
    val description: String,
    val categoryName: String,
    val categoryId: Long,
)

/**
 * Clasifica en lote los movimientos que quedaron sin categoría. El modelo
 * propone; nada se aplica hasta que el usuario toca "Aplicar".
 */
class ClassifierViewModel(
    private val analyzer: CaptureAnalyzer,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    /** Cuántos movimientos no tienen categoría. */
    val pendingCount: StateFlow<Int> = transactionRepository.observeAll()
        .map { list -> list.count { it.categoryId == null && it.amountMinor != 0L } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _proposals = MutableStateFlow<List<ClassProposal>>(emptyList())
    val proposals: StateFlow<List<ClassProposal>> = _proposals.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun scan() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            _proposals.value = emptyList()
            _progress.value = 0f
            try {
                val categories = categoryRepository.categoriesState.value
                if (categories.isEmpty()) {
                    _message.value = "No hay categorías creadas."
                    return@launch
                }
                val uncategorized = transactionRepository.observeAll().first()
                    .filter { it.categoryId == null && it.amountMinor != 0L }
                    .take(MAX_ITEMS)

                if (uncategorized.isEmpty()) {
                    _message.value = "Todo está clasificado."
                    return@launch
                }

                val names = categories.map { it.name }
                val byName = categories.associateBy { it.name.lowercase() }
                val found = mutableListOf<ClassProposal>()
                uncategorized.chunked(BATCH).forEachIndexed { index, batch ->
                    _progress.value = index.toFloat() / (uncategorized.size / BATCH + 1).toFloat()
                    val guesses = runCatching {
                        analyzer.classifyBatch(batch.map { it.description }, names)
                    }.getOrDefault(List(batch.size) { null })
                    batch.forEachIndexed { i, tx ->
                        val name = guesses.getOrNull(i) ?: return@forEachIndexed
                        val category = byName[name.lowercase()] ?: return@forEachIndexed
                        found.add(ClassProposal(tx.id, tx.description, category.name, category.id))
                    }
                }
                _proposals.value = found
                _progress.value = 1f
                _message.value = if (found.isEmpty()) {
                    "El modelo no encontró categorías claras para estos movimientos."
                } else {
                    null
                }
            } finally {
                _busy.value = false
            }
        }
    }

    /** Aplica las propuestas y hace que el motor de reglas aprenda. */
    fun apply() {
        val toApply = _proposals.value
        if (toApply.isEmpty()) return
        viewModelScope.launch {
            var applied = 0
            toApply.forEach { proposal ->
                runCatching {
                    transactionRepository.setUserCategory(proposal.txId, proposal.categoryId, learnMerchant = true)
                }.onSuccess { applied++ }
            }
            _proposals.value = emptyList()
            _message.value = applied.toString() + " movimientos clasificados."
        }
    }

    fun discard() {
        _proposals.value = emptyList()
    }

    private companion object {
        const val BATCH = 12
        const val MAX_ITEMS = 60
    }
}

