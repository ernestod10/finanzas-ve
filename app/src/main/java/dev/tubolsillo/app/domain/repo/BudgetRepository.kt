package dev.tubolsillo.app.domain.repo

import dev.tubolsillo.app.core.db.dao.BudgetDao
import dev.tubolsillo.app.core.db.dao.TransactionDao
import dev.tubolsillo.app.core.db.entity.BudgetEntity
import dev.tubolsillo.app.domain.model.BudgetMonthData
import dev.tubolsillo.app.domain.usecase.BudgetCalculator
import dev.tubolsillo.app.domain.usecase.RateProvider
import dev.tubolsillo.app.core.time.MonthBuckets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao,
    private val rateProvider: RateProvider,
    private val budgetCalculator: BudgetCalculator,
) {

    fun observeMonth(month: String): Flow<BudgetMonthData> {
        val (start, end) = MonthBuckets.bounds(month)
        return combine(
            budgetDao.observeForMonth(month),
            transactionDao.observeWithTagsBetween(start, end),
            rateProvider.observeRates(),
        ) { budgets, transactions, rates ->
            budgetCalculator.monthData(month, transactions, budgets, rates)
        }
    }

    suspend fun setBudget(month: String, tagName: String, limitMinor: Long, currencyCode: String) {
        budgetDao.upsert(
            BudgetEntity(
                month = month,
                tagName = tagName,
                limitMinor = limitMinor,
                currency = currencyCode,
            )
        )
    }
}
