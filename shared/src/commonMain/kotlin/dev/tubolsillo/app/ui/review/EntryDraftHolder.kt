package dev.tubolsillo.app.ui.review

import kotlinx.coroutines.flow.MutableStateFlow

/** Datos que la revisión pasa al alta manual para no volver a escribirlos. */
data class EntryDraft(
    val amount: String,
    val description: String,
    val income: Boolean,
    val categoryId: Long? = null,
)

/**
 * Puente de una sola vez entre la cola de revisión y "Nuevo registro".
 * Se toma y se limpia al abrir el formulario, para que un segundo alta no
 * herede los datos de la anterior.
 */
class EntryDraftHolder {
    private val _draft = MutableStateFlow<EntryDraft?>(null)

    fun put(draft: EntryDraft) {
        _draft.value = draft
    }

    fun take(): EntryDraft? = _draft.value.also { _draft.value = null }
}
