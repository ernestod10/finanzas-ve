package dev.tubolsillo.app.data.source.binance

import java.util.TreeMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firmado HMAC-SHA256 de la API de Binance: la query string ordenada
 * alfabéticamente (TreeMap garantiza el orden) + timestamp + recvWindow,
 * firma en hex, y la misma query se envía con @QueryMap.
 */
@Singleton
class BinanceSigner @Inject constructor() {

    fun sign(query: String, apiSecret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(apiSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(query.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    /**
     * Devuelve el mapa de params listo para @QueryMap: base ordenada +
     * timestamp + recvWindow + firma calculada sobre la query ordenada.
     */
    fun signedParams(
        base: Map<String, String>,
        apiSecret: String,
        timestampMs: Long = System.currentTimeMillis(),
    ): Map<String, String> {
        val sorted = TreeMap<String, String>()
        sorted.putAll(base)
        sorted["recvWindow"] = "5000"
        sorted["timestamp"] = timestampMs.toString()
        val query = sorted.entries.joinToString("&") { "${it.key}=${it.value}" }
        sorted["signature"] = sign(query, apiSecret)
        return sorted
    }
}
