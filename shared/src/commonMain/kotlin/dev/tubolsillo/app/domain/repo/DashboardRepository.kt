package dev.tubolsillo.app.domain.repo

import dev.tubolsillo.app.core.db.dao.AccountDao
import dev.tubolsillo.app.core.db.dao.TransactionDao
import dev.tubolsillo.app.core.db.dao.TransactionWithTags
import dev.tubolsillo.app.domain.model.AccountBalanceView
import dev.tubolsillo.app.domain.model.Totals
import dev.tubolsillo.app.domain.usecase.RateProvider
import dev.tubolsillo.app.domain.usecase.TotalsCalculator
import dev.tubolsillo.app.core.db.entity.AccountEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Estado agregado del dashboard, reactivo ante cualquier cambio en BD. */
data class DashboardData(
    val totals: Totals,
    val accountViews: List<AccountBalanceView>,
    val recentTransactions: List<TransactionWithTags>,
    val accounts: List<AccountEntity>,
)

class DashboardRepository(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val rateProvider: RateProvider,
    private val totalsCalculator: TotalsCalculator,
) {

    fun observeDashboard(): Flow<DashboardData> = combine(
        accountDao.observeActive(),
        rateProvider.observeRates(),
        transactionDao.observeRecentWithTags(20),
    ) { accounts, rates, recent ->
        DashboardData(
            totals = totalsCalculator.totals(accounts, rates),
            accountViews = accounts.map { totalsCalculator.toView(it, rates) },
            recentTransactions = recent,
            accounts = accounts,
        )
    }
}
