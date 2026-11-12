package dev.tubolsillo.app.domain.repo

import dev.tubolsillo.app.core.db.dao.SavingsGoalDao
import dev.tubolsillo.app.core.db.entity.SavingsContributionEntity
import dev.tubolsillo.app.core.db.entity.SavingsGoalEntity
import dev.tubolsillo.app.core.time.nowMillis
import dev.tubolsillo.app.domain.model.SavingsBuilder
import dev.tubolsillo.app.domain.model.SavingsGoalView
import dev.tubolsillo.app.domain.model.TransactionCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/** Objetivos de ahorro y sus aportes (todo local, sin cuentas ni backend). */
class SavingsRepository(
    private val savingsGoalDao: SavingsGoalDao,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
) {
    fun observe(): Flow<List<SavingsGoalView>> = combine(
        savingsGoalDao.observeGoals(),
        savingsGoalDao.observeContributions(),
    ) { goals, contributions ->
        SavingsBuilder.build(goals, contributions)
    }.flowOn(Dispatchers.Default)

    suspend fun addGoal(name: String, targetMinor: Long, currency: String, deadlineAt: Long?): Long =
        savingsGoalDao.insertGoal(
            SavingsGoalEntity(
                name = name.trim(),
                targetMinor = targetMinor,
                currency = currency,
                deadlineAt = deadlineAt,
                createdAt = nowMillis(),
            )
        )

    /**
     * Aparta dinero para un objetivo. Si se indica [accountId], además crea el
     * movimiento en esa cuenta (categoría TRANSFER, que el presupuesto y las
     * estadísticas ignoran) y descuenta el saldo: el dinero no aparece dos
     * veces, sale de la cuenta y queda en la meta.
     */
    suspend fun contribute(
        goalId: Long,
        goalName: String,
        amountMinor: Long,
        currency: String,
        accountId: Long? = null,
    ): Long {
        val signed = -kotlin.math.abs(amountMinor)
        val txId = if (accountId != null) {
            transactionRepository.insertManual(
                accountId = accountId,
                amountMinor = signed,
                currencyCode = currency,
                description = "Aporte a $goalName",
                category = TransactionCategory.TRANSFER,
            ).also {
                accountRepository.applyTransactionDelta(accountId, signed, currency)
            }
        } else {
            null
        }
        return savingsGoalDao.insertContribution(
            SavingsContributionEntity(
                goalId = goalId,
                amountMinor = kotlin.math.abs(amountMinor),
                contributedAt = nowMillis(),
                txId = txId,
            )
        )
    }

    suspend fun archive(goalId: Long) = savingsGoalDao.archiveGoal(goalId)
}
