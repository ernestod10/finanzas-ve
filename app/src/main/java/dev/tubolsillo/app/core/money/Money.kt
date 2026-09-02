package dev.tubolsillo.app.core.money

import kotlin.math.abs

/**
 * Monto en unidades menores (minor units) + su moneda.
 * Todo el almacenamiento en la app usa minor units: céntimos para USD/VES,
 * 8 decimales para BTC/ETH, etc. Así nunca hay errores de redondeo flotante.
 */
data class Money(val minor: Long, val currency: Currency) {

    val isNegative: Boolean get() = minor < 0

    fun abs(): Money = Money(abs(minor), currency)

    /** Mayor value del monto (minor / 10^decimals). Solo para mostrar o convertir. */
    fun toMajor(): Double = minor / pow10(currency.decimals)

    operator fun plus(other: Money): Money {
        require(other.currency.code == currency.code) {
            "No se pueden sumar ${currency.code} y ${other.currency.code}"
        }
        return Money(minor + other.minor, currency)
    }

    operator fun minus(other: Money): Money {
        require(other.currency.code == currency.code) {
            "No se pueden restar ${currency.code} y ${other.currency.code}"
        }
        return Money(minor - other.minor, currency)
    }

    companion object {
        private val POWERS = LongArray(19) { i -> pow10Long(i) }

        fun pow10Long(exp: Int): Long {
            var result = 1L
            repeat(exp) { result *= 10 }
            return result
        }

        fun pow10(exp: Int): Double = POWERS[exp.coerceIn(0, 18)].toDouble()

        /** Construye un Money desde su valor "mayor" (ej. 25.5 USD) redondeando al minor más cercano. */
        fun ofMajor(major: Double, currency: Currency): Money =
            Money(Math.round(major * pow10(currency.decimals)), currency)
    }
}
