package dev.tubolsillo.app.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.tubolsillo.app.core.db.entity.TransactionSplitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitDao {

    @Query("SELECT * FROM transaction_splits")
    fun observeAll(): Flow<List<TransactionSplitEntity>>

    @Query("SELECT * FROM transaction_splits WHERE txId = :txId")
    suspend fun getByTx(txId: Long): List<TransactionSplitEntity>

    @Insert
    suspend fun insertAll(splits: List<TransactionSplitEntity>)

    @Query("DELETE FROM transaction_splits WHERE txId = :txId")
    suspend fun deleteForTx(txId: Long)
}
