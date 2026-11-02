@file:OptIn(kotlin.time.ExperimentalTime::class)

package dev.tubolsillo.app.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.tubolsillo.app.core.time.nowMillis
import dev.tubolsillo.app.domain.ai.AiPrompts
import dev.tubolsillo.app.domain.ai.CaptureAnalyzer
import dev.tubolsillo.app.domain.assistant.AnswerRow
import dev.tubolsillo.app.domain.assistant.AssistantAnswer
import dev.tubolsillo.app.domain.assistant.AssistantEngine
import dev.tubolsillo.app.domain.assistant.AssistantIntentParser
import dev.tubolsillo.app.domain.repo.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Un mensaje del chat flotante. */
data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val rows: List<AnswerRow> = emptyList(),
    val slices: List<dev.tubolsillo.app.domain.assistant.AnswerSlice> = emptyList(),
    val chartTitle: String? = null,
)

/**
 * Chat del asistente. El modelo traduce la frase a una intención; las cuentas
 * y las búsquedas las hace [AssistantEngine] sobre la base de datos.
 */
class AssistantViewModel(
    private val analyzer: CaptureAnalyzer,
    private val engine: AssistantEngine,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _messages = MutableStateFlow(
        listOf(
            ChatMessage(
                fromUser = false,
                text = "Hola. Pregúntame por tus gastos, pídeme corregir un movimiento o " +
                    "compárame pagar en divisas contra la tasa BCV.",
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun send(text: String) {
        val question = text.trim()
        if (question.isEmpty() || _busy.value) return
        // La charla previa va al prompt: así "no, son 580 dólares" corrige la
        // comparación anterior en vez de buscar un movimiento.
        val history = _messages.value
            .filterNot { it.text.startsWith("Hola.") }
            .takeLast(4)
            .joinToString("\n") { (if (it.fromUser) "Usuario: " else "Asistente: ") + it.text }
        _messages.value = _messages.value + ChatMessage(fromUser = true, text = question)
        viewModelScope.launch {
            _busy.value = true
            val categories = categoryRepository.categoriesState.value.map { it.name }
            // Si el modelo tarda (AICore puede ir lento en frío), a los 8 s se
            // usa la lectura por reglas: la conversación no se queda colgada.
            val raw = runCatching {
                kotlinx.coroutines.withTimeoutOrNull(8_000) {
                    analyzer.summarize(AiPrompts.assistant(question, todayIso(), categories, history))
                }
            }.getOrNull()
            val intent = chooseIntent(
                model = AssistantIntentParser.parse(raw, categories),
                text = question,
                categories = categories,
            )
            val answer = runCatching { engine.answer(intent) }
                .getOrElse { AssistantAnswer("No pude consultar tus datos. Inténtalo otra vez.") }
            _messages.value = _messages.value + ChatMessage(
                fromUser = false,
                text = answer.text,
                rows = answer.rows,
                slices = answer.slices,
                chartTitle = answer.chartTitle,
            )
            _busy.value = false
        }
    }

    /**
     * El modelo a veces pierde un dato de la frase ("360 en divisas o 500 a
     * BCV" → solo 360). Se combina con la lectura por reglas: lo que el modelo
     * no trajo, lo aporta la heurística; y si el modelo no entendió, manda la
     * heurística.
     */
    private fun chooseIntent(
        model: dev.tubolsillo.app.domain.assistant.AssistantIntent,
        text: String,
        categories: List<String>,
    ): dev.tubolsillo.app.domain.assistant.AssistantIntent {
        val rule = AssistantIntentParser.heuristic(text)
        return when {
            model is dev.tubolsillo.app.domain.assistant.AssistantIntent.Compare &&
                rule is dev.tubolsillo.app.domain.assistant.AssistantIntent.Compare -> model.copy(
                usdPrice = model.usdPrice ?: rule.usdPrice,
                bcvUsdPrice = model.bcvUsdPrice ?: rule.bcvUsdPrice,
                bsPrice = model.bsPrice ?: rule.bsPrice,
                discountPct = model.discountPct ?: rule.discountPct,
            )

            model is dev.tubolsillo.app.domain.assistant.AssistantIntent.Help -> rule
            else -> model
        }
    }

    private fun todayIso(): String {
        val date = Instant.fromEpochMilliseconds(nowMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        return date.toString()
    }
}
