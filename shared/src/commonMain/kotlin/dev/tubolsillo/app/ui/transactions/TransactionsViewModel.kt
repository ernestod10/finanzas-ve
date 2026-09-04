package dev.tubolsillo.app.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.db.dao.TransactionWithTags
import dev.tubolsillo.app.core.db.entity.AccountEntity
import dev.tubolsillo.app.domain.repo.AccountRepository
import dev.tubolsillo.app.domain.repo.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class TransactionsViewModel(
    transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    private val categoryRepository: dev.tubolsillo.app.domain.repo.CategoryRepository,
) : ViewModel() {

    val allTransactions: StateFlow<List<TransactionWithTags>> =
        transactionRepository.observeAllWithTags()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<dev.tubolsillo.app.core.db.entity.CategoryEntity>> =
        categoryRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> =
        accountRepository.observeActive()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedAccountId = MutableStateFlow<Long?>(null)
    val selectedAccountId: StateFlow<Long?> = _selectedAccountId

    /** Feed filtrado por cuenta. */
    val filtered: StateFlow<List<TransactionWithTags>> =
        combine(allTransactions, _selectedAccountId) { txs, accountId ->
            if (accountId == null) txs
            else txs.filter { it.transaction.accountId == accountId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectAccount(accountId: Long?) {
        _selectedAccountId.value = accountId
    }
}
