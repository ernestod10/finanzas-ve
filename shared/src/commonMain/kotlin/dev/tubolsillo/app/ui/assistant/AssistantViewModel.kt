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
        _messages.value = _messages.value + ChatMessage(fromUser = true, text = question)
        viewModelScope.launch {
            _busy.value = true
            val categories = categoryRepository.categoriesState.value.map { it.name }
            val raw = runCatching {
                analyzer.summarize(AiPrompts.assistant(question, todayIso(), categories))
            }.getOrNull()
            val intent = AssistantIntentParser.parse(raw, categories)
            val answer = runCatching { engine.answer(intent) }
                .getOrElse { AssistantAnswer("No pude consultar tus datos. Inténtalo otra vez.") }
            _messages.value = _messages.value + ChatMessage(
                fromUser = false,
                text = answer.text,
                rows = answer.rows,
            )
            _busy.value = false
        }
    }

    private fun todayIso(): String {
        val date = Instant.fromEpochMilliseconds(nowMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        return date.toString()
    }
}
