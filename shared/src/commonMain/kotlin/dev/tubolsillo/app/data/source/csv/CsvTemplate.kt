package dev.tubolsillo.app.data.source.csv

import dev.tubolsillo.app.domain.model.Institution
import kotlinx.datetime.TimeZone
import kotlin.time.ExperimentalTime

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
 * Template de importación CSV. El parser es propio (core/csv/CsvParser) y la
 * zona horaria usa kotlinx-datetime (KMP).
 */
data class CsvTemplate(
    val id: String,
    val displayName: String,
    val delimiter: Char = ',',
    val skipLines: Int = 1,
    val columnMap: Map<CsvField, Int> = emptyMap(),
    /** Patrón java.time-like para la fecha (yyyy-MM-dd HH:mm:ss, dd/MM/yyyy…). */
    val datePattern: String? = null,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    val defaultCurrency: String = "VES",
    val institution: Institution? = null,
    val hasBalanceColumn: Boolean = false,
)

@OptIn(ExperimentalTime::class)
object CsvPresets {

    private val system = TimeZone.currentSystemDefault()

    val GENERIC = CsvTemplate(
        id = "generic",
        displayName = "Genérico (mapeo manual)",
        timeZone = system,
    )

    val BINANCE_DEPOSITS = CsvTemplate(
        id = "binance_deposits",
        displayName = "Binance — depósitos",
        delimiter = ',',
        skipLines = 1,
        columnMap = mapOf(
            CsvField.DATE to 0,
            CsvField.CURRENCY to 1,
            CsvField.DESCRIPTION to 2,
            CsvField.AMOUNT to 3,
        ),
        datePattern = "yyyy-MM-dd HH:mm:ss",
        timeZone = TimeZone.UTC,
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
        timeZone = system,
        defaultCurrency = "USD",
        institution = Institution.MERCANTIL_PA,
    )

    /** Placeholder: se codifica con un exporte real de Banesco. */
    val BANESCO = CsvTemplate(
        id = "banesco",
        displayName = "Banesco (por confirmar)",
        timeZone = system,
        defaultCurrency = "VES",
        institution = Institution.BANESCO,
    )

    val ALL = listOf(GENERIC, BINANCE_DEPOSITS, BINANCE_WITHDRAWALS, MERCANTIL_PA, BANESCO)
}
