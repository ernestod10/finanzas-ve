@file:OptIn(kotlin.time.ExperimentalTime::class)

package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.money.Currency
import dev.tubolsillo.app.core.money.Money
import dev.tubolsillo.app.domain.repo.SettingsRepository
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToLong

/**
 * Regla de la app para el equivalente en USD de un movimiento:
 *
 * - **Gastos y movimientos**: se CONGELAN a la tasa **BCV del día** en que
 *   ocurrieron. Así el mes pasado no cambia de valor cuando el dólar se mueve.
 * - **Saldos y patrimonio**: NO se congelan; se calculan con la tasa vigente
 *   (eso vive en TotalsCalculator y no pasa por aquí).
 *
 * Corrige además el bug de decimales: se divide por 10^decimales de la moneda
 * (BTC/ETH tienen 8), no por 100 fijo.
 */
class UsdEquivalence(
    private val settings: SettingsRepository,
    private val rateProvider: RateProvider,
) {

    /** Centavos de USD del monto, con la tasa del día de [epochMs]. */
    suspend fun usdMinor(amountMinor: Long, currencyCode: String, epochMs: Long): Long? {
        val code = currencyCode.uppercase()
        val decimals = Currency.of(code).decimals
        if (code == "USD" || code == "USDT") return UsdMath.usdMinor(amountMinor, decimals, 1.0)
        val perUnit = rateFor(code, epochMs) ?: return null
        return UsdMath.usdMinor(amountMinor, decimals, perUnit)
    }

    /**
     * USD por unidad de [code] para ese día: histórico si existe, si no la tasa
     * vigente. Para monedas fiat se usa BCV (no la fuente elegida por el usuario).
     */
    private suspend fun rateFor(code: String, epochMs: Long): Double? {
        val history = settings.ratesHistory.value
        if (history.isNotEmpty()) {
            val day = dayKey(epochMs)
            val entry = history[day]
                ?: history.entries.filter { it.key <= day }.maxByOrNull { it.key }?.value
            entry?.get(code)?.takeIf { it > 0.0 }?.let { return it }
        }
        return when (code) {
            "VES" -> settings.ratesSnapshot.value?.bcvUsdBs?.takeIf { it > 0.0 }?.let { 1.0 / it }
            "EUR" -> settings.ratesSnapshot.value?.bcvEurBs?.takeIf { it > 0.0 }?.let { 1.0 / it }
            else -> rateProvider.getRate(code).takeIf { it > 0.0 }
        }
    }

    private fun dayKey(epochMs: Long): String =
        kotlin.time.Instant.fromEpochMilliseconds(epochMs)
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toString()
}

/**
 * Aritmética pura del equivalente USD. [usdPerUnit] es cuántos dólares vale una
 * unidad de la moneda; [decimals] son los decimales de esa moneda (8 en BTC).
 */
object UsdMath {
    fun usdMinor(amountMinor: Long, decimals: Int, usdPerUnit: Double): Long? {
        if (usdPerUnit <= 0.0 || usdPerUnit.isNaN() || usdPerUnit.isInfinite()) return null
        val major = amountMinor.toDouble() / Money.pow10(decimals)
        return (major * usdPerUnit * 100).roundToLong()
    }
}
