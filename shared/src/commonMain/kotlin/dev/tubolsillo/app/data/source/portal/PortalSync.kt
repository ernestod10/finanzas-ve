package dev.tubolsillo.app.data.source.portal

import dev.tubolsillo.app.core.crypto.SecretStore
import dev.tubolsillo.app.domain.model.Institution
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText

/** Resultado crudo de una consulta al portal de un banco (sesión vinculada). */
data class PortalDump(
    val institutionKey: String,
    val url: String,
    val htmlSample: String,
    val candidateRows: List<String>,
)

/**
 * Consulta el portal usando la sesión capturada en el WebView (cookies).
 * v1 "descubrimiento": devuelve la muestra cruda + filas candidatas para
 * afinar el parser por banco con datos reales.
 */
class PortalSync(
    private val client: HttpClient,
    private val secretStore: SecretStore,
) {

    /** GET con las cookies de la sesión; null si no hay sesión o falla. */
    suspend fun fetchDump(institutionKey: String): PortalDump? {
        val session = secretStore.portalSession(institutionKey) ?: return null
        val (cookies, url) = session
        if (cookies.isBlank() || url.isBlank()) return null
        val body = runCatching {
            client.get(url) { header("Cookie", cookies) }.bodyAsText()
        }.getOrNull() ?: return null
        val clean = body.replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("<[^>]+>"), " | ")
            .replace(Regex("\\s+"), " ")
        return PortalDump(
            institutionKey = institutionKey,
            url = url,
            htmlSample = clean.take(6000),
            candidateRows = candidateRows(clean),
        )
    }

    /** Líneas con apariencia de movimiento (contienen fecha y monto). */
    private fun candidateRows(clean: String): List<String> {
        val amount = Regex("(?:Bs\\.?\\s*)?[\\d]{1,3}(?:[.,][\\d]{3})*[.,]\\d{2}|B/\\.?\\s*[\\d.,]+")
        return clean.split(" | ")
            .map { it.trim() }
            .filter { it.length in 4..120 && it.contains(Regex("\\d")) }
            .filter { amount.containsMatchIn(it) }
            .distinct()
            .take(30)
    }
}
