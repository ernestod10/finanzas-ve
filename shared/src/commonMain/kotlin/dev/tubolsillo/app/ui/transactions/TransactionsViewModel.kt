package dev.tubolsillo.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.db.dao.TransactionWithTags
import dev.tubolsillo.app.core.db.entity.AccountEntity
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

    /** Feed filtrado por cuenta, categoría y búsqueda de texto. */
    val filtered: StateFlow<List<TransactionWithTags>> =
        combine(
            allTransactions,
            _selectedAccountId,
            _selectedCategoryId,
            _searchQuery,
            categoryNames,
        ) { txs, accountId, categoryId, query, catMap ->
            val q = query.trim().lowercase()
            txs.filter { item ->
                val tx = item.transaction
                if (accountId != null && tx.accountId != accountId) return@filter false
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
