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
    val id: String = "",
    val amount: String = "0",
    val coin: String = "",
    val network: String = "",
    val status: Int = 0,
    val txId: String? = null,
    val insertTime: Long = 0,
)

@Serializable
data class WithdrawRecord(
    val id: String = "",
    val amount: String = "0",
    val coin: String = "",
    val network: String = "",
    val status: Int = 0,
    val txId: String? = null,
    val applyTime: String? = null,
)

/**
 * Historial de P2P (C2C). `amount` es la cantidad de cripto y `totalPrice` lo
 * pagado/cobrado en fiat.
 */
@Serializable
data class P2pHistoryResponse(
    val total: Int = 0,
    val success: Boolean = false,
    val data: List<P2pOrder> = emptyList(),
)

@Serializable
data class P2pOrder(
    val orderNumber: String = "",
    val tradeType: String = "",
    val asset: String = "",
    val fiat: String = "",
    val fiatSymbol: String = "",
    val amount: String = "0",
    val totalPrice: String = "0",
    val unitPrice: String = "0",
    val orderStatus: String = "",
    val createTime: Long = 0,
    val counterPartNickName: String? = null,
    val payMethodName: String? = null,
)

/** Historial de Binance Pay. `amount` ya viene firmado (positivo = entrada). */
@Serializable
data class PayHistoryResponse(
    val code: String = "",
    val message: String = "",
    val data: List<PayTransaction> = emptyList(),
)

@Serializable
data class PayTransaction(
    val orderType: String = "",
    val transactionId: String = "",
    val transactionTime: Long = 0,
    val amount: String = "0",
    val currency: String = "",
    val walletType: Int = 0,
    val payerInfo: PayParty? = null,
    val receiverInfo: PayParty? = null,
)

@Serializable
data class PayParty(
    val name: String? = null,
    val type: String? = null,
)
