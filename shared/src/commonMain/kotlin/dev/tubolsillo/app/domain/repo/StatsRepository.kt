package dev.tubolsillo.app.domain.repo

import dev.tubolsillo.app.core.db.dao.AccountDao
import dev.tubolsillo.app.core.db.dao.BalanceSnapshotDao
import dev.tubolsillo.app.core.db.dao.TransactionDao
import dev.tubolsillo.app.core.time.MonthBuckets
import dev.tubolsillo.app.core.time.nowMillis
import dev.tubolsillo.app.domain.usecase.BalanceTrend
import dev.tubolsillo.app.domain.usecase.BalanceTrendBuilder
import dev.tubolsillo.app.domain.usecase.CategorySpend
import dev.tubolsillo.app.domain.usecase.ExpenseBreakdown
import dev.tubolsillo.app.domain.usecase.PeriodMetrics
import dev.tubolsillo.app.domain.usecase.PeriodMetricsCalculator
import dev.tubolsillo.app.domain.usecase.RateProvider
import dev.tubolsillo.app.domain.usecase.TotalsCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Todo lo que alimenta Inicio › métricas y la pantalla de Estadísticas. */
@androidx.compose.runtime.Immutable
data class StatsData(
    val month: String,
    val metrics: PeriodMetrics,
    val previousMetrics: PeriodMetrics,
    val trend: BalanceTrend,
    val expenseByCategory: List<CategorySpend>,
)

/**
 * Datos del período elegido: métricas del mes, comparación con el mes
 * anterior, serie de saldo y reparto de gasto por categoría.
 *
 * Es reactivo: reacciona a cuentas, transacciones, snapshots, categorías y
 * tasas. Un solo repositorio alimenta Inicio y Estadísticas para que las dos
 * pantallas no puedan discrepar.
 */
class StatsRepository(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val balanceSnapshotDao: BalanceSnapshotDao,
    private val categoryRepository: CategoryRepository,
    private val rateProvider: RateProvider,
    private val metricsCalculator: PeriodMetricsCalculator,
    private val trendBuilder: BalanceTrendBuilder,
    private val totalsCalculator: TotalsCalculator,
) {

    fun observeMonth(month: String): Flow<StatsData> {
        val (start, end) = MonthBuckets.bounds(month)
        val previous = MonthBuckets.plusMonths(month, -1)
        val (previousStart, previousEnd) = MonthBuckets.bounds(previous)

        // 30 días de serie; se piden 90 más atrás para poder arrastrar el último
        // saldo conocido de cada cuenta al inicio del rango.
        val now = nowMillis()
        val trendFrom = MonthBuckets.startOfDay(now) - 29L * DAY_MS
        val snapshotFrom = trendFrom - 90L * DAY_MS

        val monthSide = combine(
            accountDao.observeActive(),
            transactionDao.observeBetween(start, end),
            rateProvider.observeRates(),
        ) { accounts, transactions, rates -> Triple(accounts, transactions, rates) }

        return combine(
            monthSide,
            transactionDao.observeBetween(previousStart, previousEnd),
            balanceSnapshotDao.observeSince(snapshotFrom),
            categoryRepository.observeAll(),
        ) { (accounts, transactions, rates), previousTransactions, snapshots, categories ->
            StatsData(
                month = month,
                metrics = metricsCalculator.compute(transactions, rates),
                previousMetrics = metricsCalculator.compute(previousTransactions, rates),
                trend = trendBuilder.build(
                    accounts = accounts,
                    snapshots = snapshots,
                    rates = rates,
                    fromEpochMs = trendFrom,
                    nowEpochMs = now,
                ),
                expenseByCategory = ExpenseBreakdown.compute(
                    transactions = transactions,
                    categories = categories,
                    rates = rates,
                    totalsCalculator = totalsCalculator,
                ),
            )
        }
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
