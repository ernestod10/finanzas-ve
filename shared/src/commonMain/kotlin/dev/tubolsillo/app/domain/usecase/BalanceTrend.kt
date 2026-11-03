package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.db.entity.AccountEntity
import dev.tubolsillo.app.core.db.entity.BalanceSnapshotEntity
import dev.tubolsillo.app.core.time.MonthBuckets
import kotlinx.datetime.TimeZone

/** Un punto de la serie: inicio del día y saldo total en USD. */
data class TrendPoint(val epochMs: Long, val valueUsd: Double)

/**
 * Serie de saldo de las cuentas a lo largo de un rango.
 *
 * [points] va del primer día con datos hasta hoy. [firstUsd] y [lastUsd] son
 * los extremos para calcular la variación.
 */
@androidx.compose.runtime.Immutable
data class BalanceTrend(
    val points: List<TrendPoint>,
    val firstUsd: Double,
    val lastUsd: Double,
) {
    val hasEnoughPoints: Boolean get() = points.size >= 2

    /** Variación porcentual del rango; null si el saldo inicial era cero. */
    val changePct: Double?
        get() = if (firstUsd == 0.0) null else (lastUsd - firstUsd) / kotlin.math.abs(firstUsd) * 100.0

    companion object {
        val Empty = BalanceTrend(emptyList(), 0.0, 0.0)
    }
}

/**
 * Construye la serie diaria del saldo total en USD.
 *
 * Reglas:
 *  - Cada cuenta aporta su último snapshot conocido; en los días sin captura
 *    se arrastra el valor anterior, para que la línea no caiga a cero.
 *  - La serie empieza en el primer día con algún dato (no rellena con ceros).
 *  - El saldo es la suma con signo de los snapshots: activos menos deudas, es
 *    decir el patrimonio neto.
 *  - La conversión usa la tasa del día cuando existe histórico de tasas
 *    ([ratesAt]); si no, la de hoy.
 */
class BalanceTrendBuilder(
    private val totalsCalculator: TotalsCalculator,
) {

    fun build(
        accounts: List<AccountEntity>,
        snapshots: List<BalanceSnapshotEntity>,
        rates: Map<String, Double>,
        fromEpochMs: Long,
        nowEpochMs: Long,
        zone: TimeZone = TimeZone.currentSystemDefault(),
        /** Tasa aplicable a cada día; si falta, se usa [rates] (la de hoy). */
        ratesAt: ((epochMs: Long) -> Map<String, Double>)? = null,
    ): BalanceTrend {
        if (accounts.isEmpty() || snapshots.isEmpty()) return BalanceTrend.Empty

        val startDay = MonthBuckets.startOfDay(fromEpochMs, zone)
        val today = MonthBuckets.startOfDay(nowEpochMs, zone)
        if (today < startDay) return BalanceTrend.Empty

        val knownAccounts = accounts.map { it.id }.toSet()
        val ordered = snapshots
            .filter { it.accountId in knownAccounts }
            .sortedBy { it.capturedAt }
        if (ordered.isEmpty()) return BalanceTrend.Empty

        val dayMs = 86_400_000L
        val lastByAccount = mutableMapOf<Long, BalanceSnapshotEntity>()
        val points = ArrayList<TrendPoint>()
        var cursor = 0
        var sawData = false

        var day = startDay
        while (day <= today) {
            val dayEnd = day + dayMs - 1
            while (cursor < ordered.size && ordered[cursor].capturedAt <= dayEnd) {
                val snapshot = ordered[cursor]
                lastByAccount[snapshot.accountId] = snapshot
                sawData = true
                cursor++
            }
            if (sawData) {
                val dayRates = ratesAt?.invoke(day) ?: rates
                val total = lastByAccount.values.sumOf {
                    totalsCalculator.usdEquivalent(it.amountMinor, it.currency, dayRates)
                }
                points += TrendPoint(epochMs = day, valueUsd = total)
            }
            day += dayMs
        }

        if (points.isEmpty()) return BalanceTrend.Empty
        return BalanceTrend(
            points = points,
            firstUsd = points.first().valueUsd,
            lastUsd = points.last().valueUsd,
        )
    }
}
