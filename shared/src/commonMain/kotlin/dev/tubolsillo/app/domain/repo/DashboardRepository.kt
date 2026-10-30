package dev.tubolsillo.app.domain.repo

import dev.tubolsillo.app.core.db.dao.AccountDao
import dev.tubolsillo.app.core.db.dao.TransactionDao
import dev.tubolsillo.app.core.db.dao.TransactionWithTags
import dev.tubolsillo.app.domain.model.AccountBalanceView
import dev.tubolsillo.app.domain.model.AccountKind
import dev.tubolsillo.app.domain.model.Totals
import dev.tubolsillo.app.domain.usecase.RateProvider
import dev.tubolsillo.app.domain.usecase.TotalsCalculator
import dev.tubolsillo.app.core.db.entity.AccountEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** Estado agregado del dashboard, reactivo ante cualquier cambio en BD. */
data class DashboardData(
    val totals: Totals,
    val accountViews: List<AccountBalanceView>,
    val recentTransactions: List<TransactionWithTags>,
    val accounts: List<AccountEntity>,
    val assetViews: List<AccountBalanceView> = accountViews.filter { !AccountKind.fromKey(it.kindKey).isDebt },
    val debtViews: List<AccountBalanceView> = accountViews.filter { AccountKind.fromKey(it.kindKey).isDebt },
    val accountNames: Map<Long, String> = accounts.associate { it.id to it.name },
)

class DashboardRepository(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val rateProvider: RateProvider,
    private val totalsCalculator: TotalsCalculator,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val dashboardState: StateFlow<DashboardData?> = combine(
        accountDao.observeActive(),
        rateProvider.observeRates(),
        transactionDao.observeRecentWithTags(20),
    ) { accounts, rates, recent ->
        val views = accounts.map { totalsCalculator.toView(it, rates) }
        val (debts, assets) = views.partition { AccountKind.fromKey(it.kindKey).isDebt }
        DashboardData(
            totals = totalsCalculator.totals(accounts, rates),
            accountViews = views,
            recentTransactions = recent,
            accounts = accounts,
            assetViews = assets,
            debtViews = debts,
            accountNames = accounts.associate { it.id to it.name },
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun observeDashboard(): Flow<DashboardData> = combine(
        accountDao.observeActive(),
        rateProvider.observeRates(),
        transactionDao.observeRecentWithTags(20),
    ) { accounts, rates, recent ->
        val views = accounts.map { totalsCalculator.toView(it, rates) }
        val (debts, assets) = views.partition { AccountKind.fromKey(it.kindKey).isDebt }
        DashboardData(
            totals = totalsCalculator.totals(accounts, rates),
            accountViews = views,
            recentTransactions = recent,
            accounts = accounts,
            assetViews = assets,
            debtViews = debts,
            accountNames = accounts.associate { it.id to it.name },
        )
    }.flowOn(Dispatchers.Default)
}
