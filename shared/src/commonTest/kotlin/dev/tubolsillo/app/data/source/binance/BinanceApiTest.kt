package dev.tubolsillo.app.data.source.binance

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Prueba BinanceApi contra MockEngine (más fuerte que MockWebServer:
 * además de parsear los JSON reales de Binance, verifica el header de la
 * llave y los params firmados).
 */
class BinanceApiTest {

    private val jsonHeader = headersOf(HttpHeaders.ContentType, "application/json")

    private fun apiWith(engine: MockEngine): BinanceApi {
        val client = HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return BinanceApi(client)
    }

    @Test
    fun `parsea account con balances`() = runTest {
        val engine = MockEngine {
            respond(
                """
                {
                  "makerCommission": 15,
                  "balances": [
                    {"asset": "USDT", "free": "123.45670000", "locked": "0.00000000"},
                    {"asset": "BTC", "free": "0.00123456", "locked": "0.00001000"},
                    {"asset": "XYZ", "free": "0.00000000", "locked": "0.00000000"}
                  ]
                }
                """.trimIndent(),
                HttpStatusCode.OK,
                jsonHeader,
            )
        }
        val info = apiWith(engine).account(emptyMap(), "test-key")
        assertEquals(3, info.balances.size)
        assertEquals("USDT", info.balances[0].asset)
        assertEquals("123.45670000", info.balances[0].free)
    }

    @Test
    fun `parsea depositos y retiros`() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("deposit") -> respond(
                    """[{"id":"dep1","amount":"10.5","coin":"USDT","network":"TRC20","status":1,"txId":"abc","insertTime":1600000000000}]""",
                    HttpStatusCode.OK, jsonHeader,
                )
                else -> respond(
                    """[{"id":"wd1","amount":"0.5","coin":"BTC","network":"BTC","status":6,"txId":"def","applyTime":1600000001000}]""",
                    HttpStatusCode.OK, jsonHeader,
                )
            }
        }
        val api = apiWith(engine)
        val deposits = api.deposits(emptyMap(), "k")
        assertEquals(1, deposits.size)
        assertEquals("dep1", deposits[0].id)
        assertEquals(1, deposits[0].status)

        val withdrawals = api.withdrawals(emptyMap(), "k")
        assertEquals(1, withdrawals.size)
        assertEquals(6, withdrawals[0].status)
    }

    @Test
    fun `parsea ticker y hora`() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.contains("ticker") -> respond(
                    """{"symbol":"BTCUSDT","price":"60000.01"}""", HttpStatusCode.OK, jsonHeader,
                )
                else -> respond("""{"serverTime":1600000000000}""", HttpStatusCode.OK, jsonHeader)
            }
        }
        val api = apiWith(engine)
        assertEquals("60000.01", api.tickerPrice("BTCUSDT").price)
        assertEquals(1_600_000_000_000L, api.serverTime().serverTime)
    }

    @Test
    fun `envia header de llave y params firmados`() = runTest {
        var capturedKey: String? = null
        var capturedSignature: String? = null
        var capturedTimestamp: String? = null
        val engine = MockEngine { request ->
            capturedKey = request.headers["X-MBX-APIKEY"]
            capturedSignature = request.url.parameters["signature"]
            capturedTimestamp = request.url.parameters["timestamp"]
            respond("""{"balances":[]}""", HttpStatusCode.OK, jsonHeader)
        }
        apiWith(engine).account(
            mapOf("timestamp" to "12345", "signature" to "abc", "recvWindow" to "5000"),
            "my-key",
        )
        assertEquals("my-key", capturedKey)
        assertEquals("abc", capturedSignature)
        assertEquals("12345", capturedTimestamp)
    }

    @Test
    fun `error 401 lanza ClientRequestException`() = runTest {
        val engine = MockEngine {
            respond(
                """{"code":-2015,"msg":"Invalid API-key, IP, or permissions for action."}""",
                HttpStatusCode.Unauthorized,
                jsonHeader,
            )
        }
        assertFailsWith<io.ktor.client.plugins.ClientRequestException> {
            apiWith(engine).account(emptyMap(), "bad-key")
        }
    }
}
