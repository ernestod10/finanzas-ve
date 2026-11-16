package dev.tubolsillo.app.domain.model

/** Un paso del plan de pago: qué deuda atacar y cuánto tardaría con el mínimo. */
data class PayoffStep(
    val rank: Int,
    val item: CreditItem,
    /** Meses estimados pagando el próximo pago de esa deuda; null si no hay monto. */
    val months: Int?,
)

/**
 * Orden "bola de nieve": de la deuda más pequeña a la más grande dentro de su
 * moneda. No mezcla bolívares con dólares porque no hay tasa en el modelo.
 * Los meses son una estimación gruesa usando el pago mínimo, no una promesa.
 */
object PayoffPlanner {

    fun plan(items: List<CreditItem>): List<PayoffStep> =
        items.filter { it.debtMinor > 0 }
            .sortedWith(compareBy({ it.currency }, { it.debtMinor }))
            .mapIndexed { index, item ->
                PayoffStep(rank = index + 1, item = item, months = monthsToPay(item))
            }

    private fun monthsToPay(item: CreditItem): Int? {
        val monthly = item.dueAmountMinor ?: return null
        if (monthly <= 0) return null
        return ((item.debtMinor + monthly - 1) / monthly).toInt()
    }
}
