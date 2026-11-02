package dev.tubolsillo.app.domain.assistant

import dev.tubolsillo.app.core.db.dao.TransactionWithTags
import dev.tubolsillo.app.core.money.MoneyFormatter
import dev.tubolsillo.app.core.time.MonthBuckets
import dev.tubolsillo.app.core.time.nowMillis
import dev.tubolsillo.app.domain.repo.CategoryRepository
import dev.tubolsillo.app.domain.repo.SettingsRepository
import dev.tubolsillo.app.domain.repo.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Comparación entre dos precios del mismo producto: X en divisas o Y en
 * bolívares. Los bolívares se valoran al P2P (lo que realmente cuestan), no al
 * BCV: si te cobran 500 Bs, esos Bs valen 500/p2p dólares.
 */
data class OptionsComparison(
    val usdCostUsd: Double,
    val bsCostBs: Double,
    val bsCostUsd: Double,
    val cheaperInUsd: Boolean,
    val savingUsd: Double,
    /** Descuento sobre el precio en divisas que igualaría las dos opciones. */
    val breakEvenPct: Double,
)

fun compareOptions(
    usdPrice: Double,
    bsPrice: Double,
    discountPct: Double,
    p2pVes: Double,
): OptionsComparison {
    val discount = discountPct.coerceIn(0.0, 100.0) / 100.0
    val usdCost = usdPrice * (1 - discount)
    val bsCostUsd = if (p2pVes > 0) bsPrice / p2pVes else bsPrice
    return OptionsComparison(
        usdCostUsd = usdCost,
        bsCostBs = bsPrice,
        bsCostUsd = bsCostUsd,
        cheaperInUsd = usdCost <= bsCostUsd,
        savingUsd = kotlin.math.abs(bsCostUsd - usdCost),
        breakEvenPct = if (usdPrice > 0) (((usdCost - bsCostUsd) / usdPrice) * 100).coerceIn(-100.0, 100.0) else 0.0,
    )
}

/** Una línea de la respuesta (categoría y monto, con su barra). */
data class AnswerRow(val label: String, val value: String, val fraction: Float = 0f)

data class AssistantAnswer(
    val text: String,
    val rows: List<AnswerRow> = emptyList(),
    /** true si además de responder, cambió un movimiento. */
    val applied: Boolean = false,
)

/**
 * Ejecuta lo que pidió el usuario **con los datos reales de la app**. El modelo
 * solo interpreta la frase; las cuentas y las búsquedas son de este motor, así
 * no hay cifras inventadas ni escrituras a ciegas.
 */
class AssistantEngine(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
) {

    suspend fun answer(intent: AssistantIntent): AssistantAnswer = when (intent) {
        is AssistantIntent.Stats -> stats(intent)
        is AssistantIntent.Find -> find(intent.text, intent.amount, intent.daysAgo, null, null)
        is AssistantIntent.Update -> find(intent.text, intent.amount, intent.daysAgo, intent.description, intent.category)
        is AssistantIntent.Compare -> compare(intent)
        AssistantIntent.Help -> help()
    }

    // --- Estadísticas ---

    private suspend fun stats(intent: AssistantIntent.Stats): AssistantAnswer {
        val all = transactionRepository.observeAllWithTags().first()
        val start = startOfWindow(intent.monthsBack)
        val categories = categoryRepository.categoriesState.value.associateBy { it.id }
        val categoryId = intent.category?.let { wanted ->
            categories.values.firstOrNull { it.name.equals(wanted, ignoreCase = true) }?.id
        }
        val needle = intent.text?.lowercase()?.takeIf { it.isNotBlank() }

        val matched = all.filter { item ->
            val tx = item.transaction
            tx.occurredAt >= start && tx.amountMinor < 0 &&
                (categoryId == null || tx.categoryId == categoryId) &&
                (needle == null || tx.description.lowercase().contains(needle))
        }
        if (matched.isEmpty()) {
            return AssistantAnswer("No encuentro gastos en ese período.")
        }

        val currency = matched.groupingBy { it.transaction.currency }.eachCount()
            .maxByOrNull { it.value }?.key ?: "VES"
        val inCurrency = matched.filter { it.transaction.currency == currency }
        val total = inCurrency.sumOf { abs(it.transaction.amountMinor) }
        val byCategory = inCurrency.groupBy { item ->
            item.transaction.categoryId?.let { categories[it]?.name } ?: "Sin categoría"
        }
            .mapValues { (_, list) -> list.sumOf { abs(it.transaction.amountMinor) } }
            .toList()
            .sortedByDescending { it.second }
            .take(6)

        val rows = byCategory.map { (name, amount) ->
            AnswerRow(
                label = name,
                value = MoneyFormatter.formatMinor(amount, currency),
                fraction = if (total > 0) amount.toFloat() / total else 0f,
            )
        }
        val period = when (intent.monthsBack) {
            0 -> "este mes"
            1 -> "los últimos 2 meses"
            else -> "los últimos " + (intent.monthsBack + 1) + " meses"
        }
        val what = intent.category ?: intent.text ?: "en total"
        return AssistantAnswer(
            text = "Gastaste " + MoneyFormatter.formatMinor(total, currency) + " en " + what +
                " " + period + ".",
            rows = rows,
        )
    }

    // --- Buscar / corregir un movimiento ---

    private suspend fun find(
        text: String?,
        amount: Double?,
        daysAgo: Int?,
        newDescription: String?,
        newCategory: String?,
    ): AssistantAnswer {
        val all = transactionRepository.observeAllWithTags().first()
        val today = MonthBuckets.startOfDay(nowMillis())
        val needle = text?.lowercase()?.takeIf { it.isNotBlank() }

        val candidates = all.filter { item ->
            val tx = item.transaction
            val matchesAmount = amount == null || abs(abs(tx.amountMinor) - (amount * 100).roundToInt()) <=
                maxOf(100, (amount * 100 * 0.02).roundToInt())
            val matchesDay = daysAgo == null ||
                MonthBuckets.startOfDay(tx.occurredAt) == today - daysAgo * 86_400_000L
            val matchesText = needle == null || tx.description.lowercase().contains(needle)
            matchesAmount && matchesDay && matchesText
        }

        if (candidates.isEmpty()) {
            return AssistantAnswer("No encuentro ese movimiento. Prueba con el monto exacto o el día.")
        }
        if (candidates.size > 1 && (newDescription != null || newCategory != null)) {
            return AssistantAnswer(
                text = "Hay " + candidates.size + " movimientos que encajan. ¿Cuál es?",
                rows = candidates.take(5).map { rowOf(it) },
            )
        }
        val target = candidates.first()
        if (newDescription == null && newCategory == null) {
            return AssistantAnswer(text = "Encontré este movimiento:", rows = listOf(rowOf(target)))
        }

        val tx = target.transaction
        transactionRepository.updateDetails(
            id = tx.id,
            description = newDescription?.takeIf { it.isNotBlank() } ?: tx.description,
            amountMinor = tx.amountMinor,
            currencyCode = tx.currency,
        )
        val categoryId = newCategory?.let { wanted ->
            categoryRepository.categoriesState.value
                .firstOrNull { it.name.equals(wanted, ignoreCase = true) }?.id
        }
        if (categoryId != null) {
            transactionRepository.setUserCategory(tx.id, categoryId, learnMerchant = true)
        }
        return AssistantAnswer(
            text = "Listo: " + (newDescription ?: tx.description) +
                (newCategory?.let { " · categoría " + it } ?: ""),
            rows = listOf(rowOf(target)),
            applied = true,
        )
    }

    // --- Divisas vs tasa BCV/Binance ---

    private fun compare(intent: AssistantIntent.Compare): AssistantAnswer {
        val rates = settingsRepository.ratesSnapshot.value
        if (rates == null || rates.bcvUsdBs <= 0.0) {
            return AssistantAnswer("Todavía no tengo las tasas de hoy. Actualízalas en Menú → Tasa de cambio.")
        }
        val p2p = if (rates.p2pUsdtVes > 0) rates.p2pUsdtVes else rates.bcvUsdBs
        // Si solo dieron un precio, la otra opción es el mismo producto a BCV.
        val usdPrice = intent.usdPrice
            ?: intent.bsPrice?.let { it / rates.bcvUsdBs }
            ?: return AssistantAnswer("Necesito un precio para comparar.")
        val bsPrice = intent.bsPrice ?: usdPrice * rates.bcvUsdBs
        val comparison = compareOptions(usdPrice, bsPrice, intent.discountPct ?: 0.0, p2p)

        val text = buildString {
            append(if (comparison.cheaperInUsd) "Te conviene pagar en divisas" else "Te conviene pagar en bolívares")
            append(": $")
            append(formatUsd(comparison.usdCostUsd))
            append(" en divisas vs Bs. ")
            append(formatBs(comparison.bsCostBs))
            append(" (≈ $")
            append(formatUsd(comparison.bsCostUsd))
            append(" al P2P). Ahorras $")
            append(formatUsd(comparison.savingUsd))
            append(".")
        }
        return AssistantAnswer(
            text = text,
            rows = listOf(
                AnswerRow("Diferencia", "$" + formatUsd(comparison.savingUsd)),
                AnswerRow("Equivale a BCV", "Bs. " + formatBs(rates.bcvUsdBs) + " / USD"),
                AnswerRow("P2P hoy", "Bs. " + formatBs(p2p) + " / USD"),
            ),
        )
    }

    private fun help(): AssistantAnswer = AssistantAnswer(
        text = "Puedo con esto:\n" +
            "· \"¿cuánto gasté este mes en comida?\"\n" +
            "· \"el gasto de 20 de ayer fue un almuerzo\"\n" +
            "· \"si me dan 10 % de descuento, ¿pago en divisas o a tasa BCV?\"",
    )

    private fun rowOf(item: TransactionWithTags): AnswerRow {
        val tx = item.transaction
        val day = dev.tubolsillo.app.ui.components.dayLabel(tx.occurredAt)
        return AnswerRow(
            label = day + " · " + tx.description.take(40),
            value = MoneyFormatter.formatMinor(abs(tx.amountMinor), tx.currency),
        )
    }

    /** Primer día del mes, [monthsBack] meses atrás (0 = mes actual). */
    private fun startOfWindow(monthsBack: Int): Long {
        val month = MonthBuckets.monthKey(nowMillis())
        return MonthBuckets.bounds(MonthBuckets.plusMonths(month, -monthsBack.coerceAtLeast(0))).first
    }

    private fun formatUsd(value: Double): String {
        val rounded = (value * 100).roundToInt() / 100.0
        return rounded.toString().replace('.', ',')
    }

    private fun formatBs(value: Double): String {
        val rounded = (value * 100).roundToInt() / 100.0
        return rounded.toString().replace('.', ',')
    }

    private fun formatPct(value: Double): String {
        val rounded = value.roundToInt()
        return rounded.toString() + " %"
    }
}
