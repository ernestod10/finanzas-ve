package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.time.nowMillis

import dev.tubolsillo.app.core.db.dao.CurrencyRateDao
import dev.tubolsillo.app.core.db.entity.CurrencyRateEntity
import dev.tubolsillo.app.domain.model.RateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Provee tasas USD con valores por defecto sensatos cuando no hay fila en BD:
 * USD y USDT a 1.0 (peg), VES a 1.0 hasta que el usuario la configure.
 */
class RateProvider(
    private val rateDao: CurrencyRateDao,
) {

    companion object {
        const val DEFAULT_VES_RATE = 1.0
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val ratesState: StateFlow<Map<String, Double>> =
        rateDao.observeAll()
            .map { rows ->
                defaults() + rows.associate { it.currency to it.usdRate }
            }
            .stateIn(scope, SharingStarted.Eagerly, defaults())

    fun observeRates(): Flow<Map<String, Double>> = ratesState

    suspend fun getRate(code: String): Double =
        rateDao.get(code)?.usdRate ?: defaultRate(code)

    /** Guarda la tasa VES (típicamente) elegida por el usuario. */
    suspend fun setRate(code: String, usdRate: Double, source: RateSource) {
        rateDao.upsert(
            CurrencyRateEntity(
                currency = code,
                usdRate = usdRate,
                source = source.key,
                updatedAt = nowMillis(),
            )
        )
    }

    private fun defaults(): Map<String, Double> = mapOf(
        "USD" to 1.0,
        "USDT" to 1.0,
        "VES" to DEFAULT_VES_RATE,
    )

    private fun defaultRate(code: String): Double = defaults()[code] ?: 1.0
}
