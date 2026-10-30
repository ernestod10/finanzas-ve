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
import kotlin.test.assertTrue

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
                    """[{"id":"wd1","amount":"0.5","coin":"BTC","network":"BTC","status":6,"txId":"def","applyTime":"2020-09-13 12:00:00"}]""",
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
    fun `parsea historial P2P`() = runTest {
        var path: String? = null
        var rows: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            rows = request.url.parameters["rows"]
            respond(
                """
                {"total":2,"success":true,"data":[
                  {"orderNumber":"111","tradeType":"BUY","asset":"USDT","fiat":"VES",
                   "fiatSymbol":"Bs","amount":"12.5","totalPrice":"1000","unitPrice":"80",
                   "orderStatus":"COMPLETED","createTime":1600000000000,
                   "counterPartNickName":"juan"},
                  {"orderNumber":"112","tradeType":"SELL","asset":"USDT","fiat":"VES",
                   "fiatSymbol":"Bs","amount":"5","totalPrice":"400","unitPrice":"80",
                   "orderStatus":"CANCELLED","createTime":1600000001000}
                ]}
                """.trimIndent(),
                HttpStatusCode.OK,
                jsonHeader,
            )
        }
        val response = apiWith(engine).p2pOrders(mapOf("rows" to "100"), "k")
        assertEquals(2, response.data.size)
        assertEquals("BUY", response.data[0].tradeType)
        assertEquals("12.5", response.data[0].amount)
        assertEquals("COMPLETED", response.data[0].orderStatus)
        assertEquals("juan", response.data[0].counterPartNickName)
        assertTrue(path!!.contains("listUserOrderHistory"))
        assertEquals("100", rows)
    }

    @Test
    fun `parsea Binance Pay`() = runTest {
        var path: String? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            respond(
                """
                {"code":"000000","message":"success","data":[
                  {"orderType":"PAY","transactionId":"tx1","transactionTime":1600000000000,
                   "amount":"-25.5","currency":"USDT","walletType":1,
                   "receiverInfo":{"name":"Cafe","type":"MERCHANT"}},
                  {"orderType":"PAY","transactionId":"tx2","transactionTime":1600000001000,
                   "amount":"10","currency":"USDT","walletType":1,
                   "payerInfo":{"name":"Ana","type":"USER"}}
                ]}
                """.trimIndent(),
                HttpStatusCode.OK,
                jsonHeader,
            )
        }
        val response = apiWith(engine).payTransactions(mapOf("limit" to "100"), "k")
        assertEquals(2, response.data.size)
        assertEquals("-25.5", response.data[0].amount)
        assertEquals("MERCHANT", response.data[0].receiverInfo?.type)
        assertEquals("Ana", response.data[1].payerInfo?.name)
        assertTrue(path!!.contains("/sapi/v1/pay/transactions"))
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
