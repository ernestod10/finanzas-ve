package dev.tubolsillo.app.domain.repo

import dev.tubolsillo.app.core.db.dao.SubscriptionDao
import dev.tubolsillo.app.core.db.entity.SubscriptionEntity
import dev.tubolsillo.app.core.time.nowMillis
import kotlinx.coroutines.flow.Flow

/** Suscripciones cargadas a mano: nombre, precio y día de cobro. */
class SubscriptionRepository(
    private val subscriptionDao: SubscriptionDao,
) {
    fun observe(): Flow<List<SubscriptionEntity>> = subscriptionDao.observeActive()

    suspend fun add(name: String, amountMinor: Long, currency: String, dayOfMonth: Int): Long =
        subscriptionDao.insert(
            SubscriptionEntity(
                name = name.trim(),
                amountMinor = amountMinor,
                currency = currency,
                dayOfMonth = dayOfMonth.coerceIn(1, 31),
                createdAt = nowMillis(),
            )
        )

    suspend fun update(id: Long, name: String, amountMinor: Long, currency: String, dayOfMonth: Int) =
        subscriptionDao.update(id, name.trim(), amountMinor, currency, dayOfMonth.coerceIn(1, 31))

    suspend fun archive(id: Long) = subscriptionDao.archive(id)
}
