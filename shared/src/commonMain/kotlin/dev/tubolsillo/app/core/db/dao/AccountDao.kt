package dev.tubolsillo.app.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.tubolsillo.app.core.db.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeById(id: Long): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE institution = :institution AND currency = :currency AND isActive = 1 LIMIT 1")
    suspend fun findByInstitutionAndCurrency(institution: String, currency: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE institution = :institution AND isActive = 1 ORDER BY sortOrder, id")
    suspend fun findActiveByInstitution(institution: String): List<AccountEntity>

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET isActive = 0 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("UPDATE accounts SET lastBalanceMinor = :minor, lastBalanceAt = :at WHERE id = :id")
    suspend fun updateLastBalance(id: Long, minor: Long, at: Long)

    @Query("UPDATE accounts SET creditLimitMinor = :limit WHERE id = :id")
    suspend fun updateCreditLimit(id: Long, limit: Long?)

    @Query("UPDATE accounts SET colorHex = :colorHex WHERE id = :id")
    suspend fun updateColor(id: Long, colorHex: String?)
    @Query("SELECT * FROM accounts")
    suspend fun getAllForExport(): List<AccountEntity>
}
