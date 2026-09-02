package dev.tubolsillo.app.data.source.binance

import kotlin.test.assertEquals
import kotlin.test.Test

class BinanceSignerTest {

    private val signer = BinanceSigner()

    // Vector de ejemplo de la documentación oficial de Binance:
    // https://developers.binance.com/docs/binance-spot-api-docs/rest-api (SIGNED examples)
    @Test
    fun `firma el vector de la documentacion oficial`() {
        val secret = "NhqPtmdSJYdKjVHjA7PZj4Mge3R5YNiP1e3UZjInClVN65XAbvqqM6A7H5fATj0j"
        val query = "symbol=LTCBTC&side=BUY&type=LIMIT&timeInForce=GTC&quantity=1&price=0.1&recvWindow=5000&timestamp=1499827319559"
        val expected = "c8db56825ae71d6d79447849e617115f4a920fa2acdcab2b053c4b2838bd6b71"
        assertEquals(expected, signer.sign(query, secret))
    }

    @Test
    fun `signedParams ordena alfabeticamente e incluye firma`() {
        val params = signer.signedParams(
            base = mapOf("symbol" to "BTCUSDT", "limit" to "5"),
            apiSecret = "secret",
            timestampMs = 1_600_000_000_000L,
        )
        // Orden esperado: limit, recvWindow, signature, symbol, timestamp
        assertEquals(listOf("limit", "recvWindow", "signature", "symbol", "timestamp"), params.keys.toList())
        assertEquals(5, params.size)
        // La firma debe coincidir con la query ordenada (sin signature).
        val query = "limit=5&recvWindow=5000&symbol=BTCUSDT&timestamp=1600000000000"
        assertEquals(signer.sign(query, "secret"), params["signature"])
    }

    @Test
    fun `firma deterministica`() {
        val s = signer.sign("a=1&b=2", "k")
        assertEquals(s, signer.sign("a=1&b=2", "k"))
        assertEquals(64, s.length) // hex de SHA-256
    }
}
