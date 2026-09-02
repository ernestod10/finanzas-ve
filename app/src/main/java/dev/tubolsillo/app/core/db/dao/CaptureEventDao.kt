package dev.tubolsillo.app.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.tubolsillo.app.core.db.entity.CaptureEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureEventDao {

    @Query("SELECT * FROM capture_events ORDER BY capturedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<CaptureEventEntity>>

    @Query("SELECT * FROM capture_events WHERE id = :id")
    suspend fun getById(id: Long): CaptureEventEntity?

    @Insert
    suspend fun insert(event: CaptureEventEntity): Long

    @Update
    suspend fun update(event: CaptureEventEntity)
    @Query("SELECT * FROM capture_events")
    suspend fun getAllForExport(): List<CaptureEventEntity>
}
