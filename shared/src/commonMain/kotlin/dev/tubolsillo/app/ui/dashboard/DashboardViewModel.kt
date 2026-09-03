package dev.tubolsillo.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.domain.repo.AccountRepository
import dev.tubolsillo.app.domain.repo.DashboardData
import dev.tubolsillo.app.domain.repo.DashboardRepository
import dev.tubolsillo.app.domain.repo.TagRepository
import dev.tubolsillo.app.domain.repo.TransactionRepository
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.TransactionCategory
import dev.tubolsillo.app.domain.usecase.RateProvider
import dev.tubolsillo.app.domain.usecase.RuleEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    dashboardRepository: DashboardRepository,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val tagRepository: TagRepository,
    private val ruleEngine: RuleEngine,
    private val rateProvider: RateProvider,
) : ViewModel() {

    val dashboard: StateFlow<DashboardData?> = dashboardRepository.observeDashboard()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Bolívares por dólar, para la sublínea VES del patrimonio neto. */
    val vesPerDollar: StateFlow<Double> = rateProvider.observeRates()
        .map { rates -> 1.0 / (rates["VES"] ?: 1.0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0)

    fun addAccount(
        institution: Institution,
        name: String,
        kind: AccountKind,
        currencyCode: String,
        initialBalanceMinor: Long?,
        creditLimitMinor: Long?,
    ) {
        viewModelScope.launch {
            accountRepository.create(
                institution = institution,
                name = name,
                kind = kind,
                currencyCode = currencyCode,
                initialBalanceMinor = initialBalanceMinor,
                creditLimitMinor = creditLimitMinor,
            )
        }
    }

    fun addManualTransaction(
        accountId: Long,
        amountMinor: Long,
        currencyCode: String,
        description: String,
        category: TransactionCategory,
    ) {
        viewModelScope.launch {
            transactionRepository.insertManual(
                accountId = accountId,
                amountMinor = amountMinor,
                currencyCode = currencyCode,
                description = description,
                category = category,
            )
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
}
