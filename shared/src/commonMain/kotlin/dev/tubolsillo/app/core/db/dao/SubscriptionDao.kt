package dev.tubolsillo.app.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.tubolsillo.app.core.db.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {

    @Query("SELECT * FROM manual_subscriptions WHERE isActive = 1 ORDER BY dayOfMonth, name")
    fun observeActive(): Flow<List<SubscriptionEntity>>

    @Insert
    suspend fun insert(subscription: SubscriptionEntity): Long

    @Query("UPDATE manual_subscriptions SET name = :name, amountMinor = :amountMinor, currency = :currency, dayOfMonth = :dayOfMonth WHERE id = :id")
    suspend fun update(id: Long, name: String, amountMinor: Long, currency: String, dayOfMonth: Int)

    @Query("UPDATE manual_subscriptions SET isActive = 0 WHERE id = :id")
    suspend fun archive(id: Long)
}
