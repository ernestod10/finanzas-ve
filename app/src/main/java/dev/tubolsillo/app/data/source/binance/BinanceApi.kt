package dev.tubolsillo.app.data.source.binance

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * Subconjunto de solo lectura de la API de Binance:
 * hora del servidor, ticker, saldos de spot, historial de depósitos y retiros.
 * Las llamadas firmadas reciben params ya firmados (BinanceSigner).
 */
interface BinanceApi {

    @GET("api/v3/time")
    suspend fun serverTime(): ServerTime

    @GET("api/v3/ticker/price")
    suspend fun tickerPrice(@Query("symbol") symbol: String): TickerPrice

    @GET("api/v3/account")
    suspend fun account(
        @QueryMap params: Map<String, String>,
        @Header("X-MBX-APIKEY") apiKey: String,
    ): AccountInfo

    @GET("sapi/v1/capital/deposit/hisrec")
    suspend fun deposits(
        @QueryMap params: Map<String, String>,
        @Header("X-MBX-APIKEY") apiKey: String,
    ): List<DepositRecord>

    @GET("sapi/v1/capital/withdraw/hisrec")
    suspend fun withdrawals(
        @QueryMap params: Map<String, String>,
        @Header("X-MBX-APIKEY") apiKey: String,
    ): List<WithdrawRecord>
}
