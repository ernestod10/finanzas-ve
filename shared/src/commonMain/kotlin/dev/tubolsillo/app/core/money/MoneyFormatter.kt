package dev.tubolsillo.app.core.money

import kotlin.math.abs

/**
 * Formateo de montos para pantalla, sin dependencias JVM (KMP).
 * Fiat (USD/VES/USDT) en convención es-VE: Bs. 1.234,56 · $ 1.234,56.
 * Crypto de alta precisión (8 decimales) en convención US sin agrupación:
 * ₿ 0.00001234, recortando ceros finales.
 */
object MoneyFormatter {

    fun format(money: Money, withSymbol: Boolean = true): String =
        formatMinor(money.minor, money.currency, withSymbol)

    fun formatMinor(minor: Long, code: String, withSymbol: Boolean = true): String =
        formatMinor(minor, Currency.of(code), withSymbol)

    fun formatMinor(minor: Long, currency: Currency, withSymbol: Boolean): String {
        val body = if (currency.decimals <= 2) {
            formatFixed(minor, currency.decimals, groupSeparator = '.', decimalSeparator = ',')
        } else {
            formatCrypto(minor, currency.decimals)
        }
        return if (withSymbol) "${currency.symbol} $body" else body
    }

    /** Equivalente en USD para subtítulos del dashboard: "$ 123,45". */
    fun formatUsd(usd: Double): String =
        Currency.USD.symbol + " " + formatFixed(
            (usd * 100).toLong(),
            2,
            groupSeparator = '.',
            decimalSeparator = ',',
        )

    /** Fiat con 2 decimales fijos y agrupación de miles. */
    private fun formatFixed(minor: Long, decimals: Int, groupSeparator: Char, decimalSeparator: Char): String {
        val sign = if (minor < 0) "-" else ""
        val unsigned = abs(minor)
        val pow = Money.pow10Long(decimals)
        val intPart = (unsigned / pow).toString()
        val frac = (unsigned % pow).toString().padStart(decimals, '0')
        val grouped = intPart.reversed().chunked(3).joinToString(groupSeparator.toString()).reversed()
        return "$sign$grouped$decimalSeparator$frac"
    }

    /** Crypto: hasta [decimals] decimales, ceros finales recortados, sin agrupación. */
    private fun formatCrypto(minor: Long, decimals: Int): String {
        val sign = if (minor < 0) "-" else ""
        val unsigned = abs(minor)
        val pow = Money.pow10Long(decimals)
        val intPart = (unsigned / pow).toString()
        val frac = (unsigned % pow).toString().padStart(decimals, '0').trimEnd('0')
        return if (frac.isEmpty()) "$sign$intPart" else "$sign$intPart.$frac"
    }
}
