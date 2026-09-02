package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.db.dao.TransactionDao
import dev.tubolsillo.app.core.text.MerchantNormalizer
import dev.tubolsillo.app.core.time.MonthBuckets
import dev.tubolsillo.app.domain.model.Institution
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deduplicación de capturas: la misma operación puede llegar por SMS Y por
 * push segundos aparte. La clave combina institución, moneda, |monto|, bucket
 * de 60 minutos y comercio normalizado — "FARMACIA X, C.A." por SMS y
 * "farmacia x" por push caen en el mismo bucket y dedupean.
 *
 * El lookup vecino (±24h, misma institución+monto+comercio) para CSV llega en
 * Fase 4; la clave exacta cubre el caso SMS-vs-push.
 */
@Singleton
class DedupGate @Inject constructor(
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

        private fun sha256Hex(input: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
    }
}
