package dev.tubolsillo.app.domain.model

/**
 * Tipo de cuenta. CHECKING/SAVINGS/CASH/CRYPTO suman a los activos;
 * CREDIT_CARD/BNPL suman a la sección Deuda.
 */
enum class AccountKind(val key: String, val displayName: String, val isDebt: Boolean) {
    CHECKING("CHECKING", "Cuenta corriente", false),
    SAVINGS("SAVINGS", "Cuenta de ahorro", false),
    CASH("CASH", "Efectivo", false),
    CRYPTO("CRYPTO", "Crypto", false),
    CREDIT_CARD("CREDIT_CARD", "Tarjeta de crédito", true),
    BNPL("BNPL", "BNPL (Cashea)", true),
    OTHER("OTHER", "Otra", false);

    companion object {
        fun fromKey(key: String?): AccountKind =
            entries.firstOrNull { it.key == key } ?: OTHER
    }
}
