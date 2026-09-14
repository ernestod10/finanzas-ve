package dev.tubolsillo.app.data.source.portal

import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

/**
 * Lo que devuelve el JS de la receta desde el WebView. Todo son textos crudos:
 * la normalización (monto, signo, fecha, comercio) ocurre en Kotlin dentro de
 * [PortalRowMapper], que es puro y testeable sin dispositivo.
 */
@Serializable
data class PortalPayload(
    /** ok | login | empty | error */
    val status: String = "ok",
    val url: String = "",
    val balanceText: String? = null,
    val currencyText: String? = null,
    val message: String? = null,
    val rows: List<PortalRawRow> = emptyList(),
)

/** Una fila de movimientos tal cual se leyó del DOM. */
@Serializable
data class PortalRawRow(
    val date: String = "",
    val description: String = "",
    val amount: String = "",
    /** Columnas separadas de débito/crédito, cuando el portal las usa. */
    val debit: String = "",
    val credit: String = "",
    /** Número de operación/documento: desambigua movimientos idénticos. */
    val reference: String = "",
    val balance: String = "",
    /** Texto completo de la fila, para auditoría y detección de TDC. */
    val raw: String = "",
)

object PortalJson {
    val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Decodifica el JSON de la receta; null si vino basura. */
    fun decode(raw: String?): PortalPayload? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text == "null") return null
        return runCatching { json.decodeFromString<PortalPayload>(text) }.getOrNull()
    }
}
