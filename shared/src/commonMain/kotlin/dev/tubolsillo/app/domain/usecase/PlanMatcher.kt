package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.domain.model.PlanSuggestion

/**
 * Sugiere qué transacción real corresponde a una línea del plan.
 *
 * El estimado es aproximado por naturaleza (el recibo del seguro sube, la
 * matrícula se paga en dos partes), así que la tolerancia de monto es ancha
 * (15%) y la cercanía de fecha pesa casi tanto como el monto. La ventana de
 * días evita emparejar un gasto de agosto con su gemelo de septiembre.
 *
 * La lógica es pura y estática (como [TransferMatcher]) para poder testearla
 * sin base de datos. El llamador ya filtró por signo (ingreso/gasto), por
 * categoría TRANSFER y por las transacciones ya enlazadas a otra línea: aquí
 * todos los montos llegan en valor absoluto.
 */
object PlanMatcher {

    /** Diferencia máxima de monto aceptada, relativa al mayor de los dos. */
    const val AMOUNT_TOLERANCE = 0.15

    /** Distancia máxima en días entre el vencimiento y la transacción. */
    const val WINDOW_DAYS = 10L

    /** Debajo de esto la sugerencia es ruido: no se propone. */
    const val MIN_CONFIDENCE = 0.45

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Una transacción real candidata: monto en USD absoluto y su fecha. */
    data class Candidate(
        val txId: Long,
        val amountUsd: Double,
        val occurredAt: Long,
    )

    /** La mejor candidata para [estimatedUsd] con vencimiento en [occursAt], o null. */
    fun suggest(
        estimatedUsd: Double,
        occursAt: Long,
        candidates: List<Candidate>,
    ): PlanSuggestion? =
        candidates
            .mapNotNull { candidate ->
                val ratio = amountRatio(estimatedUsd, candidate.amountUsd) ?: return@mapNotNull null
                if (ratio > AMOUNT_TOLERANCE) return@mapNotNull null
                val daysApart = daysApart(occursAt, candidate.occurredAt)
                if (daysApart > WINDOW_DAYS) return@mapNotNull null
                val confidence = confidence(ratio, daysApart)
                if (confidence < MIN_CONFIDENCE) return@mapNotNull null
                PlanSuggestion(
                    txId = candidate.txId,
                    amountUsd = candidate.amountUsd,
                    occurredAt = candidate.occurredAt,
                    daysApart = daysApart,
                    confidence = confidence,
                )
            }
            // La más segura; a igual confianza, la más cercana en el tiempo.
            .minWithOrNull(
                compareByDescending<PlanSuggestion> { it.confidence }.thenBy { it.daysApart }
            )

    /** Diferencia relativa entre dos montos (0 = idénticos), o null si no comparables. */
    fun amountRatio(estimatedUsd: Double, candidateUsd: Double): Double? =
        if (estimatedUsd <= 0 || candidateUsd <= 0) null
        else kotlin.math.abs(estimatedUsd - candidateUsd) / kotlin.math.max(estimatedUsd, candidateUsd)

    /** Días completos entre dos épocas, en valor absoluto. */
    fun daysApart(a: Long, b: Long): Long =
        kotlin.math.abs(a - b) / DAY_MS

    /** Confianza 0..1: 60% qué tan parecido es el monto, 40% qué tan cerca la fecha. */
    fun confidence(ratio: Double, daysApart: Long): Double {
        val amountScore = 1.0 - (ratio / AMOUNT_TOLERANCE)
        val dateScore = 1.0 - (daysApart.toDouble() / WINDOW_DAYS)
        return (0.6 * amountScore + 0.4 * dateScore).coerceIn(0.0, 1.0)
    }
}
