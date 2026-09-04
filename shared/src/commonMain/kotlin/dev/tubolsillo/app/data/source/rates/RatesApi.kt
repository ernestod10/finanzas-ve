package dev.tubolsillo.app.data.source.rates

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

/** Respuesta del endpoint de tasas (Lambda tubolsillo-tasas en siscoinfra). */
@Serializable
data class RatesDto(
    val fecha_fetch: String? = null,
    val fecha_valor_bcv: String? = null,
    val bcv_usd_bs: Double? = null,
    val bcv_eur_bs: Double? = null,
    val p2p_usdt_ves: Double? = null,
    val error: String? = null,
)

/** Cliente del endpoint público de tasas BCV + P2P (lee el estado del bot en S3). */
class RatesApi(
    private val client: HttpClient,
) {
    suspend fun fetch(): RatesDto =
        client.get(DEFAULT_URL).body()

    companion object {
        // Lambda tubolsillo-tasas (siscoinfra) con Function URL pública.
        const val DEFAULT_URL =
            "https://mm35jwb53baozuuilornimlv5a0rgelb.lambda-url.us-east-1.on.aws/"
    }
}
