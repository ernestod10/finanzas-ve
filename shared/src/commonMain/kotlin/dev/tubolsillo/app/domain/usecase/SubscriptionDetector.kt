@file:OptIn(kotlin.time.ExperimentalTime::class)

package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.db.entity.TransactionEntity
import dev.tubolsillo.app.core.text.MerchantNormalizer
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs

/** Cobro que se repite todos los meses con el mismo monto. */
data class SubscriptionSuggestion(
    /** Comercio tal como se muestra ("Netflix"). */
    val merchant: String,
    val amountMinor: Long,
    val currency: String,
    /** Cada cuántos días aparece (≈30 = mensual). */
    val periodDays: Int,
    /** Cuántas veces se cobró en la ventana analizada. */
    val occurrences: Int,
    val lastAt: Long,
    /** Estimación del próximo cobro. */
    val nextAt: Long,
    /** Día del mes en que suele caer (para el plan mensual). */
    val dayOfMonth: Int,
    /** Precio que venía cobrando antes del último cambio, si lo hubo. */
    val previousAmountMinor: Long? = null,
    /** true si el último cobro es mayor que el histórico (subida de precio). */
    val priceIncreased: Boolean = false,
)

/**
 * Encuentra cobros recurrentes en el historial: mismo comercio, mismo monto
 * (±8 %) y un intervalo estable de ~30 días. Es aritmética, no IA: así no
 * inventa suscripciones que no existen.
 */
object SubscriptionDetector {

    private const val MIN_OCCURRENCES = 3
    private const val AMOUNT_TOLERANCE = 0.08
    /** Cambio de precio admitido entre el histórico y el último cobro. */
    private const val STEP_TOLERANCE = 0.45
    private const val DAY_MS = 86_400_000L
    private const val MONTH_DAYS = 30

    fun detect(
        transactions: List<TransactionEntity>,
        nowMs: Long,
        monthsBack: Int = 6,
    ): List<SubscriptionSuggestion> {
        val from = nowMs - monthsBack * 30L * DAY_MS
        // Solo gastos confirmados dentro de la ventana.
        val expenses = transactions.filter {
            it.amountMinor < 0 && it.occurredAt in from..nowMs
        }
        if (expenses.isEmpty()) return emptyList()

        return expenses
            .groupBy { merchantKey(it) }
            .mapNotNull { (key, list) -> suggestionOf(key, list, nowMs) }
            .sortedByDescending { it.occurrences }
    }

    private fun merchantKey(tx: TransactionEntity): String {
        val normalized = MerchantNormalizer.normalize(tx.description).trim()
        return normalized.ifBlank { tx.description.take(24).lowercase() }
    }

    private fun suggestionOf(
        key: String,
        group: List<TransactionEntity>,
        nowMs: Long,
    ): SubscriptionSuggestion? {
        if (group.size < MIN_OCCURRENCES) return null
        val currency = group.groupingBy { it.currency }.eachCount().maxByOrNull { it.value }?.key
            ?: return null
        val items = group.filter { it.currency == currency }.sortedBy { it.occurredAt }
        if (items.size < MIN_OCCURRENCES) return null

        val amounts = items.map { abs(it.amountMinor) }
        val average = amounts.average()
        if (average <= 0) return null

        // El histórico (todo menos el último cobro) tiene que ser estable; el
        // último puede ser distinto si hubo subida o bajada de precio. Antes
        // una subida del 10 % hacía desaparecer la suscripción.
        val historical = amounts.dropLast(1)
        val previousAverage = if (historical.size >= 2) historical.average() else average
        val lastAmount = amounts.last()
        if (historical.size >= 2 &&
            historical.any { abs(it - previousAverage) / previousAverage > AMOUNT_TOLERANCE }
        ) return null
        if (abs(lastAmount - previousAverage) / previousAverage > STEP_TOLERANCE) return null

        val gaps = items.zipWithNext { a, b -> (b.occurredAt - a.occurredAt) / DAY_MS }.toList()
        if (gaps.isEmpty()) return null
        val averageGap = gaps.average()
        if (averageGap !in (MONTH_DAYS - 6).toDouble()..(MONTH_DAYS + 8).toDouble()) return null
        // Intervalo estable: ninguna pausa se aleja más de 10 días de la media.
        if (gaps.any { kotlin.math.abs(it - averageGap) > 10.0 }) return null

        val last = items.last()
        val lastDate = kotlin.time.Instant.fromEpochMilliseconds(last.occurredAt)
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
        val changed = historical.size >= 2 &&
            kotlin.math.abs(lastAmount - previousAverage) / previousAverage > 0.05
        return SubscriptionSuggestion(
            merchant = displayName(items.first().description),
            amountMinor = lastAmount,
            currency = currency,
            periodDays = averageGap.toInt(),
            occurrences = items.size,
            lastAt = last.occurredAt,
            nextAt = last.occurredAt + (averageGap * DAY_MS).toLong(),
            dayOfMonth = lastDate.dayOfMonth,
            previousAmountMinor = if (changed) previousAverage.toLong() else null,
            priceIncreased = changed && lastAmount > previousAverage,
        )
    }

    /** "NETFLIX.COM 1234" → "Netflix" para que el plan se lea bien. */
    private fun displayName(description: String): String {
        val base = description.substringBefore("·").trim()
        val firstWords = base.split(' ').filter { it.isNotBlank() }.take(3).joinToString(" ")
        val clean = firstWords.replace(Regex("[*#]+.*$"), "").trim()
        return clean.ifBlank { base.take(20) }.let { candidate ->
            if (candidate.all { it.isUpperCase() || it.isDigit() || !it.isLetter() }) {
                candidate.lowercase().replaceFirstChar { it.uppercase() }
            } else candidate
        }
    }
}
