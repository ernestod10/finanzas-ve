package dev.tubolsillo.app.core.money

/**
 * Moneda con su símbolo y cantidad de decimales.
 * Los códigos se guardan como String en la base de datos; [of] resuelve las
 * monedas conocidas y cae a un genérico de 8 decimales para assets de Binance
 * que no estén en la lista (ej. una memecoin nueva).
 */
data class Currency(val code: String, val symbol: String, val decimals: Int) {

    companion object {
        val USD = Currency("USD", "$", 2)
        val VES = Currency("VES", "Bs.", 2)
        val EUR = Currency("EUR", "€", 2)
        val USDT = Currency("USDT", "USDT", 2)
        val BTC = Currency("BTC", "₿", 8)
        val ETH = Currency("ETH", "ETH", 8)
        val BNB = Currency("BNB", "BNB", 8)

        private val known = listOf(USD, VES, EUR, USDT, BTC, ETH, BNB)

        fun of(code: String): Currency =
            known.firstOrNull { it.code.equals(code, ignoreCase = true) }
                ?: Currency(code.uppercase(), code.uppercase(), 8)
    }
}
