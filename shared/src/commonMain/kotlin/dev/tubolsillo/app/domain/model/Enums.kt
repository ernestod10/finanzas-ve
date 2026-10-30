package dev.tubolsillo.app.domain.model

/** Categoría de una transacción. */
enum class TransactionCategory(val key: String, val displayName: String) {
    MERCHANT("MERCHANT", "Comercio"),
    TRANSFER("TRANSFER", "Transferencia interna"),
    PM_SENT("PM_SENT", "Pago móvil enviado"),
    PM_RECEIVED("PM_RECEIVED", "Pago móvil recibido"),
    FEE("FEE", "Comisión"),
    CASH("CASH", "Efectivo"),
    OTHER("OTHER", "Otro");

    companion object {
        fun fromKey(key: String?): TransactionCategory =
            entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/** Fuente de la que llegó una transacción. */
enum class SourceType(val key: String, val displayName: String) {
    SMS("SMS", "SMS"),
    NOTIFICATION("NOTIFICATION", "Push"),
    BINANCE("BINANCE", "Binance"),
    EMAIL("EMAIL", "Correo"),
    CSV("CSV", "CSV"),
    PORTAL("PORTAL", "Portal"),
    MANUAL("MANUAL", "Manual");

    companion object {
        fun fromKey(key: String?): SourceType =
            entries.firstOrNull { it.key == key } ?: MANUAL
    }
}

/** Estado de una transacción. */
enum class TxStatus(val key: String, val displayName: String) {
    CONFIRMED("CONFIRMED", "Confirmada"),
    PENDING("PENDING", "Pendiente");

    companion object {
        fun fromKey(key: String?): TxStatus =
            entries.firstOrNull { it.key == key } ?: CONFIRMED
    }
}

/** Origen de una tasa de cambio. */
enum class RateSource(val key: String, val displayName: String) {
    MANUAL("MANUAL", "Manual"),
    BCV("BCV", "BCV"),
    PARALLEL("PARALLEL", "Paralelo"),
    BINANCE_TICKER("BINANCE_TICKER", "Binance");

    companion object {
        fun fromKey(key: String?): RateSource =
            entries.firstOrNull { it.key == key } ?: MANUAL
    }
}

/** Veredicto del pipeline de captura sobre un evento crudo. */
enum class ParseStatus(val key: String) {
    PARSING("PARSING"),
    PARSED("PARSED"),
    UNMATCHED("UNMATCHED"),
    DUPLICATE("DUPLICATE"),
    IGNORED("IGNORED"),
    FAILED("FAILED");

    companion object {
        fun fromKey(key: String?): ParseStatus =
            entries.firstOrNull { it.key == key } ?: UNMATCHED
    }
}
