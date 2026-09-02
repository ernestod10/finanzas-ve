package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.time.nowMillis

import dev.tubolsillo.app.core.db.dao.CaptureEventDao
import dev.tubolsillo.app.core.db.dao.TagDao
import dev.tubolsillo.app.core.db.dao.TransactionDao
import dev.tubolsillo.app.core.db.entity.CaptureEventEntity
import dev.tubolsillo.app.core.db.entity.TransactionEntity
import dev.tubolsillo.app.core.notify.CaptureNotifier
import dev.tubolsillo.app.data.parse.ParserRegistry
import dev.tubolsillo.app.domain.model.CaptureOutcome
import dev.tubolsillo.app.domain.model.Institution
import dev.tubolsillo.app.domain.model.ParseStatus
import dev.tubolsillo.app.domain.model.ParsedTransaction
import dev.tubolsillo.app.domain.model.SourceType
import dev.tubolsillo.app.domain.model.TxStatus
import dev.tubolsillo.app.domain.repo.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vía ÚNICA de ingesta de transacciones capturadas (SMS, push, y en el futuro
 * email/CSV). Pasos: 1) persistir el evento crudo (un crash nunca pierde el
 * texto) → 2) detectar institución (hint > sender > keywords) → 3) parsear →
 * 4) dedup → 5) matcher de transferencias → 6) insertar en una transacción
 * Room (tx + balance + reglas de etiquetado) → 7) actualizar evento → 8)
 * notificar.
 */
@Singleton
class CapturePipeline @Inject constructor(
    private val captureEventDao: CaptureEventDao,
    private val transactionDao: TransactionDao,
    private val tagDao: TagDao,
    private val registry: ParserRegistry,
    private val dedupGate: DedupGate,
    private val transferMatcher: TransferMatcher,
    private val accountRepository: AccountRepository,
    private val ruleEngine: RuleEngine,
    private val notifier: CaptureNotifier,
) {

    suspend fun ingest(
        rawText: String,
        source: SourceType,
        sender: String? = null,
        hintedInstitution: Institution? = null,
    ): CaptureOutcome = withContext(Dispatchers.IO) {
        val now = nowMillis()

        // 1) El raw se persiste ANTES de procesar.
        val eventId = captureEventDao.insert(
            CaptureEventEntity(
                source = source.key,
                capturedAt = now,
                rawText = rawText,
                sender = sender,
                parseStatus = ParseStatus.PARSING.key,
            )
        )

        try {
            // 2) Detección de institución.
            val institution = hintedInstitution
                ?: registry.detect(rawText)
                ?: senderInstitution(sender)
                ?: run {
                    markUnmatched(eventId)
                    return@withContext CaptureOutcome.Unmatched
                }

            // 3) Parseo.
            val parsed = registry.parse(institution, rawText, now)
                ?: run {
                    markUnmatched(eventId, institution)
                    return@withContext CaptureOutcome.Unmatched
                }

            // 4) Dedup (SMS + push del mismo evento).
            val dedupKey = dedupGate.dedupKey(
                institution = institution,
                currency = parsed.currency,
                amountMinor = parsed.amountMinor,
                occurredAt = parsed.occurredAt,
                merchant = parsed.merchant,
            )
            if (dedupGate.isDuplicate(dedupKey)) {
                // Duplicado: igual aplicar el saldo si el texto lo trae.
                parsed.balanceMinor?.let { balance ->
                    applyBalance(parsed, balance)
                }
                captureEventDao.update(
                    captureEventDao.getById(eventId)!!.copy(
                        institution = institution.key,
                        parseStatus = ParseStatus.DUPLICATE.key,
                        parserVersion = parsed.parserVersion,
                    )
                )
                return@withContext CaptureOutcome.Duplicate
            }

            // 5) Insertar transacción.
            val account = accountRepository.findForCapture(institution, parsed)
            val transactionId = transactionDao.insert(
                TransactionEntity(
                    accountId = account.id,
                    occurredAt = parsed.occurredAt,
                    amountMinor = parsed.amountMinor,
                    currency = parsed.currency,
                    description = parsed.description,
                    rawDescription = parsed.merchant,
                    category = parsed.category.key,
                    status = TxStatus.CONFIRMED.key,
                    dedupKey = dedupKey,
                    source = source.key,
                    rawJson = rawText,
                )
            )

            // 6) Saldo mencionado en el texto + auto-etiquetado por reglas.
            parsed.balanceMinor?.let { balance ->
                accountRepository.recordBalance(
                    accountId = account.id,
                    amountMinor = balance,
                    currencyCode = parsed.balanceCurrency ?: parsed.currency,
                    source = source,
                    rawText = rawText,
                )
            }
            ruleEngine.match(normalizeMerchant(parsed.merchant))?.let { rule ->
                rule.tagName?.let { tagName ->
                    tagDao.link(transactionId, tagDao.getOrCreate(tagName).id)
                }
                rule.categoryKey?.let { category ->
                    transactionDao.updateCategory(transactionId, category)
                }
            }

            // 5b) Matching de transferencias (fuera de la transacción para no
            // bloquear la ingesta si falla el matcher).
            try {
                val tx = transactionDao.getById(transactionId)
                if (tx != null) transferMatcher.matchFor(tx)
            } catch (_: Exception) {
                // El matcher re-corre en cada ingesta; un fallo aquí no es fatal.
            }

            // 7) Evento PARSED.
            captureEventDao.update(
                captureEventDao.getById(eventId)!!.copy(
                    institution = institution.key,
                    parseStatus = ParseStatus.PARSED.key,
                    parsedTransactionId = transactionId,
                    parserVersion = parsed.parserVersion,
                )
            )

            // 8) Notificar.
            notifier.notifyCaptured(parsed, transactionId)
            CaptureOutcome.Parsed(transactionId)
        } catch (e: Exception) {
            captureEventDao.getById(eventId)?.let { event ->
                captureEventDao.update(
                    event.copy(
                        parseStatus = ParseStatus.FAILED.key,
                        errorMessage = e.message,
                    )
                )
            }
            CaptureOutcome.Failed(e.message ?: "Error desconocido")
        }
    }

    private suspend fun markUnmatched(eventId: Long, institution: Institution? = null) {
        captureEventDao.getById(eventId)?.let { event ->
            captureEventDao.update(
                event.copy(
                    institution = institution?.key,
                    parseStatus = ParseStatus.UNMATCHED.key,
                )
            )
        }
    }

    private suspend fun applyBalance(parsed: ParsedTransaction, balance: Long) {
        // El saldo del duplicado se aplica igual: es el dato más fresco del banco.
        try {
            val account = accountRepository.findForCapture(parsed.institution, parsed)
            accountRepository.recordBalance(
                accountId = account.id,
                amountMinor = balance,
                currencyCode = parsed.balanceCurrency ?: parsed.currency,
                source = SourceType.SMS,
            )
        } catch (_: Exception) {
            // best-effort.
        }
    }

    private fun senderInstitution(sender: String?): Institution? =
        Institution.bySmsSender(sender)

    private fun normalizeMerchant(merchant: String): String =
        dev.tubolsillo.app.core.text.MerchantNormalizer.normalize(merchant)
}
