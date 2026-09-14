package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.db.dao.TransactionDao
import dev.tubolsillo.app.core.text.MerchantNormalizer
import dev.tubolsillo.app.core.time.MonthBuckets
import dev.tubolsillo.app.domain.model.Institution
import okio.ByteString.Companion.toByteString

/**
 * Deduplicación de capturas: la misma operación puede llegar por SMS Y por
 * push segundos aparte. La clave combina institución, moneda, |monto|, bucket
 * de 60 minutos y comercio normalizado — "FARMACIA X, C.A." por SMS y
 * "farmacia x" por push caen en el mismo bucket y dedupean.
 *
 * El lookup vecino (±24h, misma institución+monto+comercio) para CSV llega en
 * Fase 4; la clave exacta cubre el caso SMS-vs-push.
 */
class DedupGate(
    private val transactionDao: TransactionDao,
) {

    fun dedupKey(
        institution: Institution,
        currency: String,
        amountMinor: Long,
        occurredAt: Long,
        merchant: String,
    ): String = companionDedupKey(institution, currency, amountMinor, occurredAt, merchant)

    suspend fun isDuplicate(key: String): Boolean =
        transactionDao.findByDedupKey(key) != null

    /** Lógica pura de la clave (estática, testeable sin DAO). */
    companion object {
        const val BUCKET_MINUTES = 60L

        fun companionDedupKey(
            institution: Institution,
            currency: String,
            amountMinor: Long,
            occurredAt: Long,
            merchant: String,
        ): String {
            val bucket = MonthBuckets.bucketStart(occurredAt, BUCKET_MINUTES)
            val normalized = MerchantNormalizer.normalize(merchant)
            val raw = "${institution.key}|$currency|${kotlin.math.abs(amountMinor)}|$bucket|$normalized"
            return sha256Hex(raw)
        }

        /**
         * Claves de los buckets vecinos (-1h y +1h). El bucket es de piso fijo,
         * así que una compra capturada por push a las 10:59 y la misma fila
         * leída del portal a las 11:00 caen en buckets distintos: sin esto, el
         * importador del portal la duplicaría.
         */
        fun companionNeighborKeys(
            institution: Institution,
            currency: String,
            amountMinor: Long,
            occurredAt: Long,
            merchant: String,
        ): List<String> {
            val step = BUCKET_MINUTES * 60_000L
            return listOf(occurredAt - step, occurredAt + step).map { shifted ->
                companionDedupKey(institution, currency, amountMinor, shifted, merchant)
            }
        }

        private fun sha256Hex(input: String): String =
            input.encodeToByteArray().toByteString().sha256().hex()
    }
}
