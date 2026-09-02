package dev.tubolsillo.app.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.tubolsillo.app.core.db.entity.CurrencyRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyRateDao {

    @Query("SELECT * FROM currency_rates")
    fun observeAll(): Flow<List<CurrencyRateEntity>>

    @Query("SELECT * FROM currency_rates WHERE currency = :code")
    suspend fun get(code: String): CurrencyRateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rate: CurrencyRateEntity)
}
