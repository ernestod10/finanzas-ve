package dev.tubolsillo.app.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.tubolsillo.app.core.db.entity.CaptureEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureEventDao {

    @Query("SELECT * FROM capture_events ORDER BY capturedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<CaptureEventEntity>>

    /** Cola de revisión: capturas que el parser no pudo leer. */
    @Query(
        "SELECT * FROM capture_events WHERE parseStatus IN (:statuses) " +
            "ORDER BY capturedAt DESC LIMIT :limit"
    )
    fun observeByStatus(statuses: List<String>, limit: Int): Flow<List<CaptureEventEntity>>

    @Query("UPDATE capture_events SET parseStatus = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    /** Último evento con ese texto: el que acaba de insertar el pipeline. */
    @Query("SELECT * FROM capture_events WHERE rawText = :rawText ORDER BY id DESC LIMIT 1")
    suspend fun getLatestByRaw(rawText: String): CaptureEventEntity?

    @Query("SELECT * FROM capture_events WHERE id = :id")
    suspend fun getById(id: Long): CaptureEventEntity?

    @Insert
    suspend fun insert(event: CaptureEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(events: List<CaptureEventEntity>)

    @Update
    suspend fun update(event: CaptureEventEntity)
    @Query("SELECT * FROM capture_events")
    suspend fun getAllForExport(): List<CaptureEventEntity>
}
