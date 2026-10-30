package dev.tubolsillo.app.domain.repo

import dev.tubolsillo.app.core.time.nowMillis
import dev.tubolsillo.app.data.source.rates.RatesApi
import dev.tubolsillo.app.data.source.rates.RatesDto
import dev.tubolsillo.app.domain.model.RateSource
import dev.tubolsillo.app.domain.usecase.RateProvider

/**
 * Trae las referencias del endpoint (BCV dólar/euro y P2P USDT), las persiste
 * y aplica la que el usuario eligió para valorar el patrimonio.
 *
 * Vive fuera del ViewModel de Tasas para que la app pueda refrescar al abrir
 * sin depender de que esa pantalla se haya visitado. El snapshot persistido es
 * lo que Inicio muestra en "Tasas de hoy": valores reales de BCV/P2P, no la
 * tasa convertida que usa el total.
 */
class RatesRefresher(
    private val ratesApi: RatesApi,
    private val rateProvider: RateProvider,
    private val settings: SettingsRepository,
) {

    /** Fetches, persiste el snapshot y aplica la fuente elegida. Null si falló la red. */
    suspend fun refresh(source: String = settings.rateSource.value): RatesDto? {
        val dto = runCatching { ratesApi.fetch() }.getOrNull() ?: return null

        settings.setRatesSnapshot(dto.bcv_usd_bs, dto.bcv_eur_bs, dto.p2p_usdt_ves)

        // Cruce EUR→USD (usd/eur del BCV), que es lo que guarda RateProvider.
        val usd = dto.bcv_usd_bs
        val eur = dto.bcv_eur_bs
        if (usd != null && eur != null && usd > 0.0 && eur > 0.0) {
            rateProvider.setRate("EUR", usd / eur, RateSource.BCV)
        }

        // La tasa con la que se valora el patrimonio respeta la fuente elegida;
        // en MANUAL no se pisa lo que el usuario cargó a mano.
        if (source != "MANUAL") {
            val bsPerUsd = when (source) {
                "P2P" -> dto.p2p_usdt_ves
                "EUR_BCV" -> dto.bcv_eur_bs
                else -> dto.bcv_usd_bs
            }
            if (bsPerUsd != null && bsPerUsd > 0.0) {
                rateProvider.setRate(
                    "VES",
                    1.0 / bsPerUsd,
                    if (source == "P2P") RateSource.PARALLEL else RateSource.BCV,
                )
            }
        }
        return dto
    }

    /**
     * Refresca al abrir la app solo si el último snapshot es viejo o no existe.
     * Evita un request por cada arranque sin dejar las tasas congeladas.
     */
    suspend fun refreshIfStale(maxAgeMs: Long = 6 * 60 * 60 * 1000L): RatesDto? {
        val snapshot = settings.ratesSnapshot.value
        val age = nowMillis() - (snapshot?.fetchedAt ?: 0L)
        if (snapshot != null && age in 0 until maxAgeMs) return null
        return refresh()
    }
}
