package dev.tubolsillo.app.domain.repo

import dev.tubolsillo.app.core.db.dao.SavingsGoalDao
import dev.tubolsillo.app.core.db.entity.SavingsContributionEntity
import dev.tubolsillo.app.core.db.entity.SavingsGoalEntity
import dev.tubolsillo.app.core.time.nowMillis
import dev.tubolsillo.app.domain.model.SavingsBuilder
import dev.tubolsillo.app.domain.model.SavingsGoalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/** Objetivos de ahorro y sus aportes (todo local, sin cuentas ni backend). */
class SavingsRepository(
    private val savingsGoalDao: SavingsGoalDao,
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

    suspend fun contribute(goalId: Long, amountMinor: Long): Long =
        savingsGoalDao.insertContribution(
            SavingsContributionEntity(
                goalId = goalId,
                amountMinor = amountMinor,
                contributedAt = nowMillis(),
            )
        )

    suspend fun archive(goalId: Long) = savingsGoalDao.archiveGoal(goalId)
}
