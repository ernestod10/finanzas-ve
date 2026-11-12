package dev.tubolsillo.app.functions

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.assistant.AssistantEngine
import dev.tubolsillo.app.domain.assistant.AssistantIntent
import dev.tubolsillo.app.domain.assistant.compareOptions
import dev.tubolsillo.app.domain.repo.CreditsRepository
import dev.tubolsillo.app.domain.repo.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

/** Resultado de comparar pagar en divisas contra pagar en bolívares. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class ComparacionPago(
    /** true si conviene pagar en dólares en efectivo. */
    val convieneDivisas: Boolean,
    /** Lo que cuesta cada opción, en dólares reales. */
    val costoDivisasUsd: Double,
    val costoBolivaresUsd: Double,
    /** Cuánto se ahorra eligiendo la mejor opción, en dólares. */
    val ahorroUsd: Double,
    /** Frase para el usuario con los dos costos y las tasas usadas. */
    val explicacion: String,
)

/** Deuda viva con su próximo pago. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class PagoPendiente(
    /** Nombre de la tarjeta o de la cuota. */
    val nombre: String,
    /** Monto a pagar, ya formateado con su moneda. */
    val monto: String,
    /** Fecha de vencimiento en formato corto. */
    val vence: String,
    /** Días que faltan para el vencimiento. */
    val diasRestantes: Int,
)

/**
 * Funciones que Gemini puede invocar desde el asistente del teléfono: decir si
 * conviene pagar en divisas o en bolívares, cuánto se gastó en el mes y cuál
 * es el próximo pago.
 *
 * Todas son de lectura y devuelven cifras calculadas por la app con las tasas
 * y los datos locales; el modelo solo redacta la respuesta. El servicio lo
 * instancia Android, así que las dependencias se piden a Koin en el primer uso
 * ([applicationContext] es la `Application` que ya arrancó el grafo).
 */
@RequiresApi(Build.VERSION_CODES.BAKLAVA)
@AppFunctionServiceEntryPoint(
    serviceName = "TuBolsilloAppFunctionService",
    appFunctionXmlFileName = "tubolsillo_app_functions",
)
abstract class BaseTuBolsilloAppFunctionService : AppFunctionService() {

    private val koin get() = GlobalContext.get()

    private val settings: SettingsRepository by lazy { koin.get() }
    private val creditsRepository: CreditsRepository by lazy { koin.get() }
    private val engine: AssistantEngine by lazy { koin.get() }

    /**
     * Compara pagar un producto en dólares en efectivo contra pagarlo en
     * bolívares, usando las tasas del día (BCV y Binance P2P), y dice cuál
     * conviene y cuánto se ahorra.
     *
     * @param precioEnDivisasUsd Precio en dólares pagando en efectivo.
     * @param precioEnDolaresPagadoABcvUsd Precio en dólares que el comercio
     * cobra en bolívares al cambio oficial ("200 dólares en bolívares").
     * @param precioEnBolivares Precio ya expresado en bolívares.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    internal suspend fun compararPago(
        precioEnDivisasUsd: Double,
        precioEnDolaresPagadoABcvUsd: Double?,
        precioEnBolivares: Double?,
    ): ComparacionPago = withContext(Dispatchers.Default) {
        val rates = settings.ratesSnapshot.value
            ?: return@withContext ComparacionPago(
                convieneDivisas = true,
                costoDivisasUsd = 0.0,
                costoBolivaresUsd = 0.0,
                ahorroUsd = 0.0,
                explicacion = "No hay tasas cargadas: abre Menú → Tasa de cambio.",
            )
        val p2p = if (rates.p2pUsdtVes > 0) rates.p2pUsdtVes else rates.bcvUsdBs
        val bsPrice = precioEnBolivares
            ?: precioEnDolaresPagadoABcvUsd?.times(rates.bcvUsdBs)
            ?: precioEnDivisasUsd * rates.bcvUsdBs
        val comparison = compareOptions(precioEnDivisasUsd, bsPrice, 0.0, p2p)
        val explicacion = buildString {
            append(if (comparison.cheaperInUsd) "Conviene pagar en divisas" else "Conviene pagar en bolívares")
            append(": $").append(format(comparison.usdCostUsd))
            append(" en efectivo vs Bs. ").append(format(comparison.bsCostBs))
            append(" (≈ $").append(format(comparison.bsCostUsd)).append(" al P2P).")
        }
        ComparacionPago(
            convieneDivisas = comparison.cheaperInUsd,
            costoDivisasUsd = comparison.usdCostUsd,
            costoBolivaresUsd = comparison.bsCostUsd,
            ahorroUsd = comparison.savingUsd,
            explicacion = explicacion,
        )
    }

    /**
     * Dice cuánto se gastó en un mes, opcionalmente de una categoría.
     *
     * @param categoria Nombre de la categoría ("Comida"); null para todas.
     * @param mesesAtras 0 el mes actual, 1 el anterior, 2 hace dos meses.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    internal suspend fun gastosDelMes(
        categoria: String?,
        mesesAtras: Int?,
    ): String = withContext(Dispatchers.Default) {
        val intent = AssistantIntent.Stats(
            monthsBack = (mesesAtras ?: 0).coerceIn(0, 24),
            category = categoria,
            text = null,
        )
        val answer = engine.answer(intent)
        buildString {
            append(answer.text)
            answer.rows.take(5).forEach { row ->
                append("\n· ").append(row.label).append(": ").append(row.value)
            }
        }
    }

    /**
     * Devuelve el próximo pago pendiente de una tarjeta de crédito o de una
     * cuota, con su monto y su fecha. Si no hay deudas, devuelve null.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    internal suspend fun proximoPago(): PagoPendiente? = withContext(Dispatchers.Default) {
        val overview = creditsRepository.observe().first()
        val next = overview.nextPayment ?: return@withContext null
        val dueAt = next.dueAt ?: return@withContext null
        val days = ((dueAt - System.currentTimeMillis()) / 86_400_000L).toInt().coerceAtLeast(0)
        PagoPendiente(
            nombre = next.name,
            monto = MoneyFormatter.formatMinor(next.dueAmountMinor ?: next.debtMinor, next.currency),
            vence = dev.tubolsillo.app.ui.components.formatDate(dueAt),
            diasRestantes = days,
        )
    }

    private fun format(value: Double): String {
        val rounded = kotlin.math.round(value * 100) / 100.0
        return rounded.toString().replace('.', ',')
    }
}
