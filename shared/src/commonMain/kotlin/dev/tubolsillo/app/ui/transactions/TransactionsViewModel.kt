package dev.tubolsillo.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.db.dao.TransactionWithTags
import dev.tubolsillo.app.core.db.entity.AccountEntity
import dev.tubolsillo.app.domain.ai.CaptureAnalyzer
import dev.tubolsillo.app.domain.ai.SearchFilter
import dev.tubolsillo.app.domain.repo.AccountRepository
import dev.tubolsillo.app.domain.repo.TransactionRepository
import dev.tubolsillo.app.ui.components.currentEpochDays
import dev.tubolsillo.app.ui.components.dayLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DayTransactionsGroup(
    val dayLabel: String,
    val transactions: List<TransactionWithTags>,
)

class TransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: dev.tubolsillo.app.domain.repo.CategoryRepository,
    private val tagRepository: dev.tubolsillo.app.domain.repo.TagRepository,
    private val ruleEngine: dev.tubolsillo.app.domain.usecase.RuleEngine,
    private val analyzer: CaptureAnalyzer,
) : ViewModel() {

    val allTransactions: StateFlow<List<TransactionWithTags>> =
        transactionRepository.observeAllWithTags()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<dev.tubolsillo.app.core.db.entity.CategoryEntity>> =
        categoryRepository.categoriesState

    val allTags: StateFlow<List<dev.tubolsillo.app.core.db.entity.TagEntity>> =
        tagRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categoryNames: StateFlow<Map<Long, String>> =
        categoryRepository.categoriesState
            .map { list -> list.associate { it.id to it.name } }
            .flowOn(Dispatchers.Default)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                categoryRepository.categoriesState.value.associate { it.id to it.name },
            )

    val accounts: StateFlow<List<AccountEntity>> =
        accountRepository.observeActive()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accountNames: StateFlow<Map<Long, String>> =
        accounts
            .map { list -> list.associate { it.id to it.name } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _selectedAccountId = MutableStateFlow<Long?>(null)
    val selectedAccountId: StateFlow<Long?> = _selectedAccountId

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    /** Filtro que produce una pregunta en lenguaje natural. */
    private val _aiFilter = MutableStateFlow<SearchFilter?>(null)
    val aiFilter: StateFlow<SearchFilter?> = _aiFilter

    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy

    private val _aiMessage = MutableStateFlow<String?>(null)
    val aiMessage: StateFlow<String?> = _aiMessage

    /** Los tres filtros combinados en uno para no anidar combine() de seis flujos. */
    private data class Filters(
        val accountId: Long?,
        val categoryId: Long?,
        val query: String,
        val ai: SearchFilter?,
    )

    private val filters = combine(
        _selectedAccountId,
        _selectedCategoryId,
        _searchQuery,
        _aiFilter,
    ) { accountId, categoryId, query, ai -> Filters(accountId, categoryId, query, ai) }

    /** Feed filtrado por cuenta, categoría y búsqueda de texto. */
    val filtered: StateFlow<List<TransactionWithTags>> =
        combine(
            allTransactions,
            filters,
            categoryNames,
        ) { txs, f, catMap ->
            val q = f.query.trim().lowercase()
            txs.filter { item ->
                val tx = item.transaction
                if (f.accountId != null && tx.accountId != f.accountId) return@filter false
                val categoryId = f.categoryId
                if (categoryId != null) {
                    if (categoryId == -1L && tx.categoryId != null) return@filter false
                    if (categoryId != -1L && tx.categoryId != categoryId) return@filter false
                }
                if (q.isNotEmpty()) {
                    val catName = tx.categoryId?.let { catMap[it]?.lowercase() } ?: ""
                    val matchesDesc = tx.description.lowercase().contains(q)
                    val matchesTag = item.tags.any { it.name.lowercase().contains(q) }
                    val matchesCat = catName.contains(q)
                    if (!matchesDesc && !matchesTag && !matchesCat) return@filter false
                }
                if (!matchesAi(tx.description, item, f.ai, catMap)) return@filter false
                true
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Agrupado por día en hilo secundario para no bloquear el hilo UI al componer. */
    val groupedByDay: StateFlow<List<DayTransactionsGroup>> =
        filtered
            .map { list ->
                if (list.isEmpty()) emptyList()
                else {
                    val today = currentEpochDays()
                    list.groupBy { dayLabel(it.transaction.occurredAt, today) }
                        .map { (day, txs) -> DayTransactionsGroup(day, txs) }
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectAccount(accountId: Long?) {
        _selectedAccountId.value = accountId
    }

    fun selectCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    /**
     * Pregunta en lenguaje natural ("¿cuánto gasté en farmacia en septiembre?").
     * El modelo solo produce el filtro; el filtrado es local y determinista.
     */
    fun askAi(question: String) {
        if (question.isBlank()) return
        viewModelScope.launch {
            _aiBusy.value = true
            val names = categories.value.map { it.name }
            val filter = runCatching { analyzer.parseSearch(question, names) }.getOrNull()
            _aiFilter.value = filter
            _aiMessage.value = if (filter == null) {
                "No pude interpretarla; búscala por texto."
            } else null
            if (filter == null) _searchQuery.value = question
            _aiBusy.value = false
        }
    }

    fun clearAiFilter() {
        _aiFilter.value = null
        _aiMessage.value = null
    }

    private fun matchesAi(
        description: String,
        item: TransactionWithTags,
        filter: SearchFilter?,
        catMap: Map<Long, String>,
    ): Boolean {
        if (filter == null) return true
        val tx = item.transaction
        filter.fromMs?.let { if (tx.occurredAt < it) return false }
        filter.toMs?.let { if (tx.occurredAt > it) return false }
        filter.income?.let { wantsIncome -> if ((tx.amountMinor > 0) != wantsIncome) return false }
        filter.categoryName?.let { name ->
            val catId = catMap.entries.firstOrNull { it.value.equals(name, ignoreCase = true) }?.key
            if (catId == null || tx.categoryId != catId) return false
        }
        filter.text?.takeIf { it.isNotBlank() }?.let { text ->
            val needle = text.lowercase()
            val inDesc = description.lowercase().contains(needle)
            val inTags = item.tags.any { it.name.lowercase().contains(needle) }
            if (!inDesc && !inTags) return false
        }
        return true
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setTransactionCategory(transactionId: Long, categoryId: Long?, learnMerchant: Boolean = true) {
        viewModelScope.launch {
            transactionRepository.setUserCategory(transactionId, categoryId, learnMerchant)
        }
    }

    fun tagTransaction(transactionId: Long, tagName: String, learn: Boolean) {
        viewModelScope.launch {
            tagRepository.tagTransaction(transactionId, tagName)
            if (learn) {
                val tx = transactionRepository.getById(transactionId)
                tx?.let { ruleEngine.learn(it.description, tagName) }
            }
        }
    }

    fun updateTransactionDetails(transactionId: Long, description: String, amountMinor: Long, currencyCode: String) {
        viewModelScope.launch {
            transactionRepository.updateDetails(transactionId, description, amountMinor, currencyCode)
        }
    }

    fun deleteTransaction(transactionId: Long) {
        viewModelScope.launch {
            transactionRepository.delete(transactionId)
        }
    }
}
