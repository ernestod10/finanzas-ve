package dev.tubolsillo.app.data.source.portal

import dev.tubolsillo.app.domain.model.Institution

/**
 * Cómo decidir si un movimiento suma o resta. Los portales casi nunca traen el
 * signo dentro del texto del monto, así que se declara por banco.
 */
enum class SignPolicy {
    /** El texto del monto ya trae el signo (incluye paréntesis contables). */
    AMOUNT_SIGNED,

    /** Columnas separadas: débito resta, crédito suma. */
    DEBIT_CREDIT_COLUMNS,

    /** Se deduce de la descripción ("compra" resta, "abono" suma). */
    HINTS_FROM_DESCRIPTION,

    /** Todo resta (estados de cuenta de tarjeta de crédito). */
    ALL_NEGATIVE,
}

/**
 * Receta de extracción de un banco: es DATO, no código. Soportar un banco nuevo
 * es añadir una entrada en [PortalRecipes], sin escribir clases Kotlin.
 */
data class PortalRecipe(
    val institution: Institution,
    val version: Int = 1,
    /** URL de movimientos a cargar tras el login; null = quedarse donde esté. */
    val movementsUrl: String? = null,
    /** Expresión JS booleana: "hay formulario de login en pantalla". */
    val loginFormMarkerJs: String = "(!!document.querySelector('input[type=password]'))",
    /** Expresión JS booleana: "estoy dentro". Sustituye al viejo url.length < 40. */
    val loggedInMarkerJs: String = "(!document.querySelector('input[type=password]'))",
    val userSelector: String? = null,
    val passSelector: String? = null,
    /** Botón de submit: se le hace .click(), nunca form.submit(). */
    val submitSelector: String? = null,
    val rowsSelector: String = "table tr",
    val cellDateSelector: String? = null,
    val cellDescriptionSelector: String? = null,
    val cellAmountSelector: String? = null,
    val cellDebitSelector: String? = null,
    val cellCreditSelector: String? = null,
    val cellReferenceSelector: String? = null,
    val balanceSelector: String? = null,
    /** Patrón de fecha; incluir HH:mm SIEMPRE que el portal muestre la hora. */
    val dateFormat: String? = null,
    val timeZoneId: String = "America/Caracas",
    val currency: String = "VES",
    val signPolicy: SignPolicy = SignPolicy.DEBIT_CREDIT_COLUMNS,
    val negativeHints: List<String> = DEFAULT_NEGATIVE,
    val positiveHints: List<String> = DEFAULT_POSITIVE,
    /** Espera tras detectar sesión, para que la SPA termine de pintar la tabla. */
    val settleMs: Long = 1_500,
    val pollMs: Long = 500,
    val timeoutMs: Long = 60_000,
    val maxRows: Int = 200,
) {
    /** La URL de arranque vive en Institution: una sola fuente de verdad. */
    val startUrl: String get() = institution.portalStartUrl.orEmpty()

    /** Si la fila trae hora del día, el dedup no necesita ventana de día. */
    val hasTimeOfDay: Boolean get() = dateFormat?.contains("HH") == true

    companion object {
        val DEFAULT_NEGATIVE = listOf(
            "debito", "cargo", "compra", "pago", "retiro", "comision", "pos", "punto de venta",
        )
        val DEFAULT_POSITIVE = listOf(
            "credito", "abono", "deposito", "recibido", "transferencia recibida", "nomina",
        )
    }
}
