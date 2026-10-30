package dev.tubolsillo.app.domain.model

/** Totales del dashboard, todo en USD. */
data class Totals(
    val assetsUsd: Double,
    val debtUsd: Double,
) {
    val netWorthUsd: Double get() = assetsUsd - debtUsd
}

/** Vista de una cuenta para el dashboard: saldo nativo + equivalente USD. */
data class AccountBalanceView(
    val accountId: Long,
    val institutionKey: String,
    val name: String,
    val kindKey: String,
    val currencyCode: String,
    val balanceMinor: Long?,
    val creditLimitMinor: Long?,
    /** Disponible publicado por el banco para una TDC (BNC solo manda este). */
    val creditAvailableMinor: Long? = null,
    val minPaymentMinor: Long? = null,
    val creditDueAt: Long? = null,
    val colorHex: String? = null,
    val usdEquivalent: Double?,
    val lastUpdateAt: Long?,
)
