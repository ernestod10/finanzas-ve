package dev.tubolsillo.app.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.tubolsillo.app.core.db.entity.BalanceSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BalanceSnapshotDao {

    @Query("SELECT * FROM balance_snapshots WHERE accountId = :accountId ORDER BY capturedAt DESC LIMIT 1")
    fun observeLatest(accountId: Long): Flow<BalanceSnapshotEntity?>

    @Query("SELECT * FROM balance_snapshots WHERE accountId = :accountId ORDER BY capturedAt DESC")
    fun observeHistory(accountId: Long): Flow<List<BalanceSnapshotEntity>>

    /**
     * Snapshots desde una fecha, para la serie de saldo. El llamador pide un
     * poco antes del inicio del rango para poder arrastrar el último valor
     * conocido de cada cuenta.
     */
    @Query("SELECT * FROM balance_snapshots WHERE capturedAt >= :from ORDER BY capturedAt")
    fun observeSince(from: Long): Flow<List<BalanceSnapshotEntity>>

    @Insert
    suspend fun insert(snapshot: BalanceSnapshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(snapshots: List<BalanceSnapshotEntity>)

    /**
     * Inserta el snapshot y actualiza el saldo desnormalizado de la cuenta en
     * la misma transacción de base de datos (el saldo mostrado nunca queda
     * desincronizado del historial).
     */
    @Transaction
    suspend fun recordBalance(accountId: Long, snapshot: BalanceSnapshotEntity): Long {
        val id = insert(snapshot)
        updateLastBalance(accountId, snapshot.amountMinor, snapshot.capturedAt)
        return id
    }

    @Query("UPDATE accounts SET lastBalanceMinor = :minor, lastBalanceAt = :at WHERE id = :accountId")
    suspend fun updateLastBalance(accountId: Long, minor: Long, at: Long)
    @Query("SELECT * FROM balance_snapshots")
    suspend fun getAllForExport(): List<BalanceSnapshotEntity>
}
