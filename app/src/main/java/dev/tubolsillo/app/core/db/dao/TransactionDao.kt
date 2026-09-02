package dev.tubolsillo.app.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import dev.tubolsillo.app.core.db.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY occurredAt DESC")
    fun observeByAccount(accountId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE occurredAt >= :start AND occurredAt < :end ORDER BY occurredAt DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE occurredAt >= :start AND occurredAt < :end ORDER BY occurredAt DESC")
    fun observeWithTagsBetween(start: Long, end: Long): Flow<List<TransactionWithTags>>

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecentWithTags(limit: Int): Flow<List<TransactionWithTags>>

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC")
    fun observeAllWithTags(): Flow<List<TransactionWithTags>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observeWithTagsById(id: Long): Flow<TransactionWithTags?>

    @Transaction
    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY occurredAt DESC")
    fun observeWithTagsByAccount(accountId: Long): Flow<List<TransactionWithTags>>

    @Query("SELECT * FROM transactions WHERE dedupKey = :key LIMIT 1")
    suspend fun findByDedupKey(key: String): TransactionEntity?

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("UPDATE transactions SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String)

    @Query("UPDATE transactions SET amountMinor = :amountMinor WHERE id = :id")
    suspend fun updateAmount(id: Long, amountMinor: Long)

    @Query("UPDATE transactions SET transferPairId = :pairId WHERE id = :id")
    suspend fun setTransferPair(id: Long, pairId: Long?)

    @Query("UPDATE transactions SET isReconciled = 1 WHERE id = :id")
    suspend fun markReconciled(id: Long)
}
