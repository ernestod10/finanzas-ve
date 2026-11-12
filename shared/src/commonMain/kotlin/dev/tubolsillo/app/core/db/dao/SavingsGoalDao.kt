package dev.tubolsillo.app.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.tubolsillo.app.core.db.entity.SavingsContributionEntity
import dev.tubolsillo.app.core.db.entity.SavingsGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsGoalDao {

    @Query("SELECT * FROM savings_goals WHERE isActive = 1 ORDER BY createdAt")
    fun observeGoals(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_contributions ORDER BY contributedAt")
    fun observeContributions(): Flow<List<SavingsContributionEntity>>

    @Query("SELECT * FROM savings_goals")
    suspend fun getAllForExport(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_contributions")
    suspend fun getAllContributionsForExport(): List<SavingsContributionEntity>

    @Insert
    suspend fun insertGoal(goal: SavingsGoalEntity): Long

    @Insert
    suspend fun insertContribution(contribution: SavingsContributionEntity): Long

    @Query("UPDATE savings_goals SET isActive = 0 WHERE id = :id")
    suspend fun archiveGoal(id: Long)

    @Query("UPDATE savings_goals SET name = :name, targetMinor = :target, deadlineAt = :deadline WHERE id = :id")
    suspend fun updateGoal(id: Long, name: String, target: Long, deadline: Long?)
}
