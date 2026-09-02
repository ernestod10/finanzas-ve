package dev.tubolsillo.app.data.source.binance

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

/** Prueba el parseo de los DTOs contra JSON real de Binance (grabado). */
class BinanceApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: BinanceApi

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BinanceApi::class.java)
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parsea account con balances`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "makerCommission": 15,
                  "balances": [
                    {"asset": "USDT", "free": "123.45670000", "locked": "0.00000000"},
                    {"asset": "BTC", "free": "0.00123456", "locked": "0.00001000"},
                    {"asset": "XYZ", "free": "0.00000000", "locked": "0.00000000"}
                  ]
                }
                """.trimIndent()
            )
        )
        val info = api.account(emptyMap(), "test-key")
        assertEquals(3, info.balances.size)
        assertEquals("USDT", info.balances[0].asset)
        assertEquals("123.45670000", info.balances[0].free)
    }

    @Test
    fun `parsea depositos y retiros`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """[{"id":"dep1","amount":"10.5","coin":"USDT","network":"TRC20","status":1,"txId":"abc","insertTime":1600000000000}]"""
            )
        )
        server.enqueue(
            MockResponse().setBody(
                """[{"id":"wd1","amount":"0.5","coin":"BTC","network":"BTC","status":6,"txId":"def","applyTime":1600000001000}]"""
            )
        )
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
        server.enqueue(MockResponse().setBody("""{"symbol":"BTCUSDT","price":"60000.01"}"""))
        server.enqueue(MockResponse().setBody("""{"serverTime":1600000000000}"""))
        assertEquals("60000.01", api.tickerPrice("BTCUSDT").price)
        assertEquals(1_600_000_000_000L, api.serverTime().serverTime)
    }

    @Test
    fun `errores de api propagan exception con codigo`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":-2015,"msg":"Invalid API-key, IP, or permissions for action."}"""))
        var message: String? = null
        try {
            api.account(emptyMap(), "bad-key")
        } catch (e: retrofit2.HttpException) {
            message = e.message
        }
        assertTrue(message.orEmpty().contains("401"))
    }
}
