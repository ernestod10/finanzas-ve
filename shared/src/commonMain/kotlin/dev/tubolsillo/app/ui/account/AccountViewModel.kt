package dev.tubolsillo.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.db.dao.TransactionWithTags
import dev.tubolsillo.app.core.db.entity.AccountEntity
import dev.tubolsillo.app.core.db.entity.BalanceSnapshotEntity
import dev.tubolsillo.app.core.money.AmountNormalizer
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.SourceType
import dev.tubolsillo.app.domain.repo.AccountRepository
import dev.tubolsillo.app.domain.repo.BalanceRepository
import dev.tubolsillo.app.domain.repo.TagRepository
import dev.tubolsillo.app.domain.repo.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(
    private val accountId: Long,
    private val accountRepository: AccountRepository,
    private val balanceRepository: BalanceRepository,
    private val transactionRepository: TransactionRepository,
    private val tagRepository: TagRepository,
    private val categoryRepository: dev.tubolsillo.app.domain.repo.CategoryRepository,
    private val ruleEngine: dev.tubolsillo.app.domain.usecase.RuleEngine,
) : ViewModel() {

    val account: StateFlow<AccountEntity?> = accountRepository.observeById(accountId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val snapshots: StateFlow<List<BalanceSnapshotEntity>> =
        balanceRepository.observeHistory(accountId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val transactions: StateFlow<List<TransactionWithTags>> =
        transactionRepository.observeWithTagsByAccount(accountId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Actualiza el saldo manualmente (el usuario lee su saldo real en el banco). */
    fun updateBalance(amountText: String) {
        val account = account.value ?: return
        val minor = AmountNormalizer.parseMinor(amountText, account.currency) ?: return
        viewModelScope.launch {
            // Cashea muestra "saldo disponible": la deuda = límite - disponible.
            val esCashea = AccountKind.fromKey(account.kind) == AccountKind.BNPL &&
                Institution.fromKey(account.institution) == Institution.CASHEA
            val storedMinor = if (esCashea && account.creditLimitMinor != null) {
                -((account.creditLimitMinor!! - kotlin.math.abs(minor)).coerceAtLeast(0L))
            } else if (esCashea) {
                -kotlin.math.abs(minor)
            } else {
                minor
            }
            accountRepository.recordBalance(
                accountId = account.id,
                amountMinor = storedMinor,
                currencyCode = account.currency,
                source = SourceType.MANUAL,
                rawText = amountText,
            )
        }
    }

    fun updateColor(colorHex: String?) {
        viewModelScope.launch {
            accountRepository.updateColor(accountId, colorHex)
        }
    }

    /** Renombra la cuenta; un nombre vacío no se guarda. */
    fun updateName(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            accountRepository.updateName(accountId, name)
        }
    }

    fun updateCreditLimit(limitText: String) {
        val account = account.value ?: return
        val minor = AmountNormalizer.parseMinor(limitText, account.currency)
        viewModelScope.launch {
            accountRepository.updateCreditLimit(account.id, minor)
        }
    }

    fun archiveAccount() {
        viewModelScope.launch { accountRepository.archive(accountId) }
    }

    val allTags: StateFlow<List<dev.tubolsillo.app.core.db.entity.TagEntity>> =
        tagRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<dev.tubolsillo.app.core.db.entity.CategoryEntity>> =
        categoryRepository.categoriesState

    /** Asigna la categoría de gasto a una transacción (null = sin categoría). */
    fun setTransactionCategory(transactionId: Long, categoryId: Long?, learnMerchant: Boolean = true) {
        viewModelScope.launch {
            transactionRepository.setUserCategory(transactionId, categoryId, learnMerchant)
        }
    }

    /** Etiqueta una transacción y opcionalmente aprende la regla del comercio. */
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
