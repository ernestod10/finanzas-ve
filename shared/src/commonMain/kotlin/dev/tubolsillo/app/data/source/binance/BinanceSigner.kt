package dev.tubolsillo.app.data.source.binance

import okio.ByteString.Companion.toByteString

/**
 * Firmado HMAC-SHA256 de la API de Binance (KMP: okio en vez de javax.crypto):
 * la query string ordenada alfabéticamente (TreeMap garantiza el orden) +
 * timestamp + recvWindow, firma en hex, y la misma query se envía como params.
 */
class BinanceSigner() {

    fun sign(query: String, apiSecret: String): String =
        query.encodeToByteArray().toByteString()
            .hmacSha256(apiSecret.encodeToByteArray().toByteString())
            .hex()

    /**
     * Devuelve el mapa de params listo para la request: base ordenada +
     * timestamp + recvWindow + firma calculada sobre la query ordenada.
     */
    fun signedParams(
        base: Map<String, String>,
        apiSecret: String,
        timestampMs: Long = nowMillis(),
    ): Map<String, String> {
        val sorted = sortedMapOf<String, String>()
        sorted.putAll(base)
        sorted["recvWindow"] = "5000"
        sorted["timestamp"] = timestampMs.toString()
        val query = sorted.entries.joinToString("&") { "${it.key}=${it.value}" }
        sorted["signature"] = sign(query, apiSecret)
        return sorted
    }

    private fun nowMillis(): Long = dev.tubolsillo.app.core.time.nowMillis()
}
