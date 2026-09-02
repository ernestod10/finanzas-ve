package dev.tubolsillo.app.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.tubolsillo.app.core.db.entity.InstallmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallmentDao {

    @Query("SELECT * FROM installments WHERE accountId = :accountId ORDER BY dueAt")
    fun observeByAccount(accountId: Long): Flow<List<InstallmentEntity>>

    @Query("SELECT * FROM installments WHERE status = 'PENDING' ORDER BY dueAt")
    fun observePending(): Flow<List<InstallmentEntity>>

    @Insert
    suspend fun insert(installment: InstallmentEntity): Long

    @Query("UPDATE installments SET status = 'PAID' WHERE id = :id")
    suspend fun markPaid(id: Long)

    @Query("UPDATE installments SET status = 'PAID' WHERE accountId = :accountId")
    suspend fun markAllPaid(accountId: Long)
}
