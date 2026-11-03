package dev.tubolsillo.app.functions

import androidx.appfunctions.AppFunctionComponentRegistry
import androidx.appfunctions.AppFunctionSerializable
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.domain.assistant.AssistantEngine
import dev.tubolsillo.app.domain.assistant.AssistantIntent
import dev.tubolsillo.app.domain.assistant.compareOptions
import dev.tubolsillo.app.domain.repo.CreditsRepository
import dev.tubolsillo.app.domain.repo.SettingsRepository
import kotlinx.coroutines.flow.first

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
    val explicacion: String,
)

/** Deuda viva con su próximo pago. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class PagoPendiente(
    val nombre: String,
    val monto: String,
    val vence: String,
    val diasRestantes: Int,
)

/**
 * Funciones que Gemini puede invocar desde el asistente del teléfono.
 *
 * Todas son de lectura y devuelven números calculados por la app: el modelo no
 * inventa cifras, solo redacta la respuesta final al usuario.
 */
@AppFunctionComponentRegistry(
    componentCategory = "finance",
    componentNames = ["compararPago", "gastosDelMes", "proximoPago"],
    componentDocStrings = [
        "Compara pagar un producto en dolares en efectivo contra pagarlo en bolivares a la tasa BCV o en bolivares directos, y dice cual conviene y cuanto se ahorra.",
        "Dice cuanto se gasto en un mes, opcionalmente de una categoria.",
        "Devuelve el proximo pago pendiente de una tarjeta de credito o una cuota, con monto y fecha.",
    ],
)
class TuBolsilloFunctions(
    private val settings: SettingsRepository,
    private val creditsRepository: CreditsRepository,
) {

    /**
     * Compara pagar un producto en dólares en efectivo contra pagarlo en
     * bolívares a la tasa BCV (o en bolívares directos), usando las tasas del
     * día (BCV y Binance P2P). Devuelve cuál conviene y cuánto se ahorra.
     */
    suspend fun compararPago(
        precioEnDivisasUsd: Double,
        precioEnDolaresPagadoABcvUsd: Double?,
        precioEnBolivares: Double?,
    ): ComparacionPago {
        val rates = settings.ratesSnapshot.value
            ?: return ComparacionPago(false, 0.0, 0.0, 0.0, "No hay tasas cargadas.")
        val p2p = if (rates.p2pUsdtVes > 0) rates.p2pUsdtVes else rates.bcvUsdBs
        val bsPrice = precioEnBolivares
            ?: precioEnDolaresPagadoABcvUsd?.times(rates.bcvUsdBs)
            ?: precioEnDivisasUsd * rates.bcvUsdBs
        val comparison = compareOptions(precioEnDivisasUsd, bsPrice, 0.0, p2p)
        val explicacion = buildString {
            append(if (comparison.cheaperInUsd) "Conviene pagar en divisas" else "Conviene pagar en bolívares")
            append(": $").append(format(comparison.usdCostUsd))
            append(" en divisas vs Bs. ").append(format(comparison.bsCostBs))
            append(" (≈ $").append(format(comparison.bsCostUsd)).append(" al P2P).")
        }
        return ComparacionPago(
            convieneDivisas = comparison.cheaperInUsd,
            costoDivisasUsd = comparison.usdCostUsd,
            costoBolivaresUsd = comparison.bsCostUsd,
            ahorroUsd = comparison.savingUsd,
            explicacion = explicacion,
        )
    }

    /** Los gastos de un mes, opcionalmente de una categoría; sale de la base local. */
    suspend fun gastosDelMes(
        mes: String?,
        categoria: String?,
    ): String {
        val intent = AssistantIntent.Stats(monthsBack = 0, category = categoria, text = null)
        val answer = engineAnswer(intent)
        return answer
    }

    /** El próximo pago de una tarjeta o cuota, con monto y fecha. */
    suspend fun proximoPago(): PagoPendiente? {
        val overview = creditsRepository.observe().first()
        val next = overview.nextPayment ?: return null
        val dueAt = next.dueAt ?: return null
        val days = ((dueAt - System.currentTimeMillis()) / 86_400_000L).toInt().coerceAtLeast(0)
        return PagoPendiente(
            nombre = next.name,
            monto = MoneyFormatter.formatMinor(next.dueAmountMinor ?: next.debtMinor, next.currency),
            vence = dev.tubolsillo.app.ui.components.formatDate(dueAt),
            diasRestantes = days,
        )
    }

    /** El motor real se inyecta aparte para no exponerlo como parámetro. */
    var engine: AssistantEngine? = null

    private suspend fun engineAnswer(intent: AssistantIntent): String =
        engine?.answer(intent)?.let { answer ->
            buildString {
                append(answer.text)
                answer.rows.take(5).forEach { row -> append("\n· ").append(row.label).append(": ").append(row.value) }
            }
        } ?: "No pude consultar tus datos."

    private fun format(value: Double): String {
        val rounded = kotlin.math.round(value * 100) / 100.0
        return rounded.toString().replace('.', ',')
    }
}
