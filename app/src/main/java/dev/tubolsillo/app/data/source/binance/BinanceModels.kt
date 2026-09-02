package dev.tubolsillo.app.data.source.binance

import kotlinx.serialization.Serializable

/** DTOs de la API pública de Binance (solo lectura). */

@Serializable
data class ServerTime(val serverTime: Long)

@Serializable
data class TickerPrice(val symbol: String, val price: String)

@Serializable
data class AccountInfo(val balances: List<AssetBalance>)

@Serializable
data class AssetBalance(
    val asset: String,
    val free: String,
    val locked: String,
)

@Serializable
data class DepositRecord(
    val id: String,
    val amount: String,
    val coin: String,
    val network: String,
    val status: Int,
    val txId: String? = null,
    val insertTime: Long = 0,
)

@Serializable
data class WithdrawRecord(
    val id: String,
    val amount: String,
    val coin: String,
    val network: String,
    val status: Int,
    val txId: String? = null,
    val applyTime: Long = 0,
)
