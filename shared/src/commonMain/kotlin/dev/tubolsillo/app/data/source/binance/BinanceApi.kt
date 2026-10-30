package dev.tubolsillo.app.data.source.binance

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter

/**
 * Subconjunto de solo lectura de la API de Binance sobre Ktor (KMP):
 * hora del servidor, ticker, saldos de spot, historial de depósitos y retiros.
 * Las llamadas firmadas reciben params ya firmados (BinanceSigner).
 */
class BinanceApi(private val client: HttpClient) {

    private val base = "https://api.binance.com"

    suspend fun serverTime(): ServerTime =
        client.get("$base/api/v3/time").body()

    suspend fun tickerPrice(symbol: String): TickerPrice =
        client.get("$base/api/v3/ticker/price") {
            parameter("symbol", symbol)
        }.body()

    suspend fun account(signed: Map<String, String>, apiKey: String): AccountInfo =
        client.get("$base/api/v3/account") {
            header("X-MBX-APIKEY", apiKey)
            signed.forEach { (key, value) -> parameter(key, value) }
        }.body()

    suspend fun deposits(signed: Map<String, String>, apiKey: String): List<DepositRecord> =
        client.get("$base/sapi/v1/capital/deposit/hisrec") {
            header("X-MBX-APIKEY", apiKey)
            signed.forEach { (key, value) -> parameter(key, value) }
        }.body()

    suspend fun withdrawals(signed: Map<String, String>, apiKey: String): List<WithdrawRecord> =
        client.get("$base/sapi/v1/capital/withdraw/history") {
            header("X-MBX-APIKEY", apiKey)
            signed.forEach { (key, value) -> parameter(key, value) }
        }.body()

    /** Órdenes P2P (C2C) completadas o no; la app filtra por estado. */
    suspend fun p2pOrders(signed: Map<String, String>, apiKey: String): P2pHistoryResponse =
        client.get("$base/sapi/v1/c2c/orderMatch/listUserOrderHistory") {
            header("X-MBX-APIKEY", apiKey)
            signed.forEach { (key, value) -> parameter(key, value) }
        }.body()

    /** Movimientos de Binance Pay (USER_DATA). */
    suspend fun payTransactions(signed: Map<String, String>, apiKey: String): PayHistoryResponse =
        client.get("$base/sapi/v1/pay/transactions") {
            header("X-MBX-APIKEY", apiKey)
            signed.forEach { (key, value) -> parameter(key, value) }
        }.body()
}
