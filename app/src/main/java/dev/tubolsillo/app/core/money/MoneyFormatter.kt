package dev.tubolsillo.app.core.money

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Formateo de montos para pantalla.
 * Fiat (USD/VES/USDT) en convención es-VE: Bs. 1.234,56 · $ 1.234,56.
 * Crypto de alta precisión (8 decimales) en convención US sin agrupación:
 * ₿ 0,00001234 → "₿ 0.00001234", recortando ceros finales.
 */
object MoneyFormatter {

    private val veSymbols = DecimalFormatSymbols(Locale.forLanguageTag("es-VE")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    private val usSymbols = DecimalFormatSymbols(Locale.US)

    private val fiatPattern = DecimalFormat("#,##0.00", veSymbols)
    private val cryptoPattern = DecimalFormat("0.########", usSymbols)

    fun format(money: Money, withSymbol: Boolean = true): String =
        formatMinor(money.minor, money.currency, withSymbol)

    fun formatMinor(minor: Long, code: String, withSymbol: Boolean = true): String =
        formatMinor(minor, Currency.of(code), withSymbol)

    fun formatMinor(minor: Long, currency: Currency, withSymbol: Boolean): String {
        val body = when (currency.decimals) {
            0, 1, 2 -> fiatPattern.format(minor / Money.pow10(currency.decimals))
            else -> cryptoPattern.format(minor / Money.pow10(currency.decimals))
        }
        return if (withSymbol) "${currency.symbol} $body" else body
    }

    /** Equivalente en USD para subtítulos del dashboard: "$ 123,45". */
    fun formatUsd(usd: Double): String =
        Currency.USD.symbol + " " + fiatPattern.format(usd)
}
