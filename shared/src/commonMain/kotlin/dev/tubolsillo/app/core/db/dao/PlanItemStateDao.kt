package dev.tubolsillo.app.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.tubolsillo.app.core.db.entity.PlanItemStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanItemStateDao {

    @Query("SELECT * FROM plan_item_states WHERE month = :month")
    fun observeForMonth(month: String): Flow<List<PlanItemStateEntity>>

    @Query("SELECT * FROM plan_item_states WHERE itemId = :itemId AND month = :month LIMIT 1")
    suspend fun getState(itemId: Long, month: String): PlanItemStateEntity?

    /** Busca por transacción enlazada para no enlazar el mismo movimiento dos veces. */
    @Query("SELECT * FROM plan_item_states WHERE txId = :txId LIMIT 1")
    suspend fun findByTx(txId: Long): PlanItemStateEntity?

    /**
     * Como el índice único es (itemId, month), REPLACE hace las veces de upsert:
     * marcar pagado dos veces no duplica la fila.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: PlanItemStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(states: List<PlanItemStateEntity>)

    @Query("DELETE FROM plan_item_states WHERE itemId = :itemId")
    suspend fun deleteByItem(itemId: Long)

    @Query("SELECT * FROM plan_item_states")
    suspend fun getAllForExport(): List<PlanItemStateEntity>
}
