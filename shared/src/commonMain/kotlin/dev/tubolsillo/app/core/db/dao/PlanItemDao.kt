package dev.tubolsillo.app.core.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.tubolsillo.app.core.db.entity.PlanItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanItemDao {

    /** Todas las líneas vivas. El filtro por mes lo hace PlanCalculator. */
    @Query("SELECT * FROM plan_items WHERE isActive = 1 ORDER BY kind, label")
    fun observeActive(): Flow<List<PlanItemEntity>>

    @Query("SELECT * FROM plan_items WHERE id = :id")
    suspend fun getById(id: Long): PlanItemEntity?

    @Query("SELECT * FROM plan_items")
    suspend fun getAllForExport(): List<PlanItemEntity>

    @Insert
    suspend fun insert(item: PlanItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PlanItemEntity>)

    @Update
    suspend fun update(item: PlanItemEntity)

    @Delete
    suspend fun delete(item: PlanItemEntity)
}
