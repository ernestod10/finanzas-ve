package dev.tubolsillo.app.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    /** Candidatas a la otra pata de una transferencia: signo opuesto, sin par, en ventana de tiempo. */
    @Query(
        "SELECT * FROM transactions WHERE id != :excludeId AND transferPairId IS NULL " +
            "AND category != 'TRANSFER' AND occurredAt BETWEEN :from AND :to " +
            "AND amountMinor * :sign < 0 ORDER BY occurredAt DESC"
    )
    suspend fun findMatchCandidates(excludeId: Long, from: Long, to: Long, sign: Long): List<TransactionEntity>

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("UPDATE transactions SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String)

    @Query("UPDATE transactions SET amountMinor = :amountMinor WHERE id = :id")
    suspend fun updateAmount(id: Long, amountMinor: Long)

    @Query("UPDATE transactions SET description = :description, amountMinor = :amountMinor, usdEquivalentMinor = :usdEquivalentMinor WHERE id = :id")
    suspend fun updateDetails(id: Long, description: String, amountMinor: Long, usdEquivalentMinor: Long?)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE transactions SET transferPairId = :pairId WHERE id = :id")
    suspend fun setTransferPair(id: Long, pairId: Long?)

    @Query("UPDATE transactions SET isReconciled = 1 WHERE id = :id")
    suspend fun markReconciled(id: Long)

    @Query(
        "SELECT * FROM transactions WHERE accountId = :accountId AND amountMinor = :amountMinor " +
            "AND occurredAt >= :from AND occurredAt < :to LIMIT 1"
    )
    suspend fun findByAmountInWindow(
        accountId: Long,
        amountMinor: Long,
        from: Long,
        to: Long,
    ): TransactionEntity?

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id")
    suspend fun setCategory(id: Long, categoryId: Long?)
    @Query("SELECT * FROM transactions")
    suspend fun getAllForExport(): List<TransactionEntity>
}
