package dev.tubolsillo.app.data.source.csv

import dev.tubolsillo.app.domain.model.Institution
import java.time.ZoneId

/** Campos mapeables de un estado de cuenta CSV. */
enum class CsvField(val label: String) {
    DATE("Fecha"),
    DESCRIPTION("Descripción"),
    AMOUNT("Monto"),
    CURRENCY("Moneda"),
    BALANCE("Saldo"),
    INFLOW("Entradas"),
    OUTFLOW("Salidas"),
}

/**
 * Template de importación CSV. Los presets por institución se codifican con
 * los exportes reales del usuario; mientras tanto el mapeo manual (generic)
 * cubre cualquier formato.
 */
data class CsvTemplate(
    val id: String,
    val displayName: String,
    val delimiter: Char = ',',
    val skipLines: Int = 1,
    val columnMap: Map<CsvField, Int> = emptyMap(),
    /** Patrón java.time para la fecha (dd/MM/yyyy, yyyy-MM-dd, …); null = ISO o epoch. */
    val datePattern: String? = null,
    val dateZone: ZoneId = ZoneId.systemDefault(),
    val defaultCurrency: String = "VES",
    val institution: Institution? = null,
    val hasBalanceColumn: Boolean = false,
)

object CsvPresets {

    val GENERIC = CsvTemplate(
        id = "generic",
        displayName = "Genérico (mapeo manual)",
    )

    val BINANCE_DEPOSITS = CsvTemplate(
        id = "binance_deposits",
        displayName = "Binance — depósitos",
        delimiter = ',',
        skipLines = 1,
        // CSV real de Binance: Date(UTC), Coin, Network, Amount, TxId
        columnMap = mapOf(
            CsvField.DATE to 0,
            CsvField.CURRENCY to 1,
            CsvField.DESCRIPTION to 2,
            CsvField.AMOUNT to 3,
        ),
        datePattern = "yyyy-MM-dd HH:mm:ss",
        dateZone = ZoneId.of("UTC"),
        defaultCurrency = "USD",
        institution = Institution.BINANCE,
    )

    val BINANCE_WITHDRAWALS = BINANCE_DEPOSITS.copy(
        id = "binance_withdrawals",
        displayName = "Binance — retiros",
    )

    /** Placeholder: se codifica con un exporte real de Mercantil Panamá. */
    val MERCANTIL_PA = CsvTemplate(
        id = "mercantil_pa",
        displayName = "Mercantil Panamá (por confirmar)",
        defaultCurrency = "USD",
        institution = Institution.MERCANTIL_PA,
    )

    /** Placeholder: se codifica con un exporte real de Banesco. */
    val BANESCO = CsvTemplate(
        id = "banesco",
        displayName = "Banesco (por confirmar)",
        defaultCurrency = "VES",
        institution = Institution.BANESCO,
    )

    val ALL = listOf(GENERIC, BINANCE_DEPOSITS, BINANCE_WITHDRAWALS, MERCANTIL_PA, BANESCO)
}
