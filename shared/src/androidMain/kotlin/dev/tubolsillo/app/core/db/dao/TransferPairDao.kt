package dev.tubolsillo.app.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.tubolsillo.app.core.db.entity.TransferPairEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferPairDao {

    @Query("SELECT * FROM transfer_pairs WHERE isConfirmed = 0 ORDER BY createdAt")
    fun observeUnconfirmed(): Flow<List<TransferPairEntity>>

    @Query("SELECT * FROM transfer_pairs WHERE id = :id")
    suspend fun getById(id: Long): TransferPairEntity?

    @Insert
    suspend fun insert(pair: TransferPairEntity): Long

    @Query("UPDATE transfer_pairs SET isConfirmed = 1 WHERE id = :id")
    suspend fun confirm(id: Long)
    @Query("SELECT * FROM transfer_pairs")
    suspend fun getAllForExport(): List<TransferPairEntity>
}
