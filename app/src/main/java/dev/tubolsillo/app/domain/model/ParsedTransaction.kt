package dev.tubolsillo.app.domain.model

/** Salida normalizada de un parser de captura (SMS/push), lista para persistir. */
data class ParsedTransaction(
    val institution: Institution,
    val occurredAt: Long,
    val amountMinor: Long,
    val currency: String,
    val merchant: String,
    val description: String,
    val category: TransactionCategory,
    val balanceMinor: Long? = null,
    val balanceCurrency: String? = null,
    val parserVersion: Int,
    val raw: String,
)

/** Veredicto del pipeline de captura para una ingesta. */
sealed interface CaptureOutcome {
    data class Parsed(val transactionId: Long) : CaptureOutcome
    data object Duplicate : CaptureOutcome
    data object Unmatched : CaptureOutcome
    data class Failed(val error: String) : CaptureOutcome
}
