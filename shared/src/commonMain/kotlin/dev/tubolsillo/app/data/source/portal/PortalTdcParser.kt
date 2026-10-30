package dev.tubolsillo.app.data.source.portal

import dev.tubolsillo.app.core.money.AmountNormalizer
import kotlin.math.abs

/**
 * Lee el bloque de una tarjeta de crédito en las vistas de productos del
 * portal (Posición Consolidada de Bancaribe, Resumen de BNC, etc.).
 *
 * El DOM cambia de banco en banco, pero el texto visible que arma el portal es
 * bastante estable:
 *
 * ```
 * Visa Avant Estand 4812***1901 Paga antes del 15-11-2026
 *      Disp. Bs. 2.467,45  Deuda Bs. 22.232,55  Pago min. Bs. 3.705,43
 * ```
 *
 * Se extrae cada etiqueta por separado en vez de quedarse con "el último
 * número": el disponible y la deuda son dos datos distintos y de los dos sale
 * el límite, que es lo que la app necesita para mostrar la tarjeta completa.
 *
 * Función pura: sin DOM, sin corrutinas. Se testea con el texto real.
 */
object PortalTdcParser {

    /** Lectura de una tarjeta. Los montos ya vienen en minor units. */
    data class Tdc(
        val cardName: String = "",
        val mask: String = "",
        /** Moneda explícita del texto ("USD" o "VES"); null si no la declara. */
        val currency: String? = null,
        val debtMinor: Long? = null,
        val availableMinor: Long? = null,
        val limitMinor: Long? = null,
        val minPaymentMinor: Long? = null,
        /** Día de pago ("paga antes del 15-11-2026"), epoch ms a medianoche UTC. */
        val dueAt: Long? = null,
    ) {
        /** Sin deuda ni disponible no hay nada que registrar. */
        val hasAmounts: Boolean get() = debtMinor != null || availableMinor != null

        /** Límite declarado o deducido de deuda + disponible. */
        val effectiveLimitMinor: Long?
            get() = limitMinor ?: if (debtMinor != null && availableMinor != null) {
                debtMinor + availableMinor
            } else null
    }

    /** Monto con separador de miles y dos decimales: "22.232,55" o "1,234.56". */
    private const val AMT = "((?:\\d{1,3}(?:[.,]\\d{3})+|\\d+)[.,]\\d{2})"

    /**
     * Moneda opcional entre la etiqueta y el monto. BNC la imprime entre
     * paréntesis ("Disponible (Bs.): 114.311,14") y Bancaribe con punto
     * ("Disp. Bs. 2.467,45"): ambas formas entran.
     */
    private const val CUR = "(?:\\s*\\(?\\s*(usd|us\\$|\\$|bs\\.?|ves)\\s*\\)?)?"

    private val DEBT = Regex(
        "(?:deuda|saldo\\s+(?:actual|deudor|al\\s+corte|utilizado)|utilizado|consumo|monto\\s+deudor)" +
            CUR + "\\s*:?\\s*" + AMT,
        RegexOption.IGNORE_CASE,
    )

    private val AVAILABLE = Regex(
        "(?:disp(?:onible)?\\.?|saldo\\s+disponible|cr[eé]dito\\s+disponible|monto\\s+disponible)" +
            CUR + "\\s*:?\\s*" + AMT,
        RegexOption.IGNORE_CASE,
    )

    private val LIMIT = Regex(
        "(?:l[ií]mite|cupo(?:\\s+total)?|l[ií]nea\\s+de\\s+cr[eé]dito)" + CUR + "\\s*:?\\s*" + AMT,
        RegexOption.IGNORE_CASE,
    )

    private val MIN_PAYMENT = Regex(
        "(?:pago\\s+m[ií]nim[oa]|cuota\\s+m[ií]nima|pago\\s+m[ií]n\\.?)" + CUR + "\\s*:?\\s*" + AMT,
        RegexOption.IGNORE_CASE,
    )

    private val DUE = Regex(
        "(?:paga\\s+antes\\s+del|pagar\\s+antes\\s+del|vence(?:ncia)?(?:\\s+el)?|fecha\\s+l[ií]mite|" +
            "fecha\\s+de\\s+pago|pr[oó]ximo\\s+pago)\\s*:?\\s*" +
            "(\\d{1,2}[\\/\\-. ]\\d{1,2}[\\/\\-. ]\\d{2,4}|\\d{4}-\\d{2}-\\d{2})",
        RegexOption.IGNORE_CASE,
    )

    /** `4812***1901`, `****1901`, `4512 **** 1901`. */
    private val MASK = Regex(
        "(?:^|[\\s(])((?:[45]\\d{3}|\\d{4})[\\d*•\\-]{4,16}|\\*{2,8}\\d{3,4})\\b",
    )

    /** Prefijos de sección que el portal concatena antes del nombre real. */
    private val SECTION_NOISE = Regex(
        "^(?:tarjetas?\\s+de\\s+cr[eé]dito|tarjetas?|cr[eé]dito|productos|mis\\s+productos)\\s*[:\\-]?\\s*",
        RegexOption.IGNORE_CASE,
    )

    fun parse(rawText: String, defaultCurrency: String = "VES"): Tdc {
        val text = rawText.replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return Tdc()

        val debtMatch = DEBT.find(text)
        val availMatch = AVAILABLE.find(text)
        val limitMatch = LIMIT.find(text)
        val minMatch = MIN_PAYMENT.find(text)
        val dueMatch = DUE.find(text)

        // "Disp. Bs. X" y "Deuda Bs. Y" pueden compartir moneda: se usa la
        // primera que la declare explícitamente.
        val currency = listOfNotNull(
            debtMatch?.groupValues?.getOrNull(1),
            availMatch?.groupValues?.getOrNull(1),
            limitMatch?.groupValues?.getOrNull(1),
            minMatch?.groupValues?.getOrNull(1),
        ).firstOrNull { it.isNotBlank() }?.let(::currencyOf) ?: defaultCurrency

        val maskMatch = MASK.find(text)

        return Tdc(
            cardName = cardNameOf(text, maskMatch?.range?.first),
            mask = maskMatch?.groupValues?.get(1).orEmpty().trim(),
            currency = currency,
            debtMinor = debtMatch?.groupValues?.get(2)?.let { minorOf(it, currency) },
            availableMinor = availMatch?.groupValues?.get(2)?.let { minorOf(it, currency) },
            limitMinor = limitMatch?.groupValues?.get(2)?.let { minorOf(it, currency) },
            minPaymentMinor = minMatch?.groupValues?.get(2)?.let { minorOf(it, currency) },
            dueAt = dueMatch?.groupValues?.get(1)?.let(::dueAtOf),
        )
    }

    private fun currencyOf(token: String): String {
        val t = token.lowercase()
        return if (t.contains("usd") || t.contains("$")) "USD" else "VES"
    }

    private fun minorOf(amount: String, currency: String): Long? =
        AmountNormalizer.parseMinor(amount, currency)?.let { abs(it) }

    /** Nombre comercial impreso justo antes de la máscara. */
    private fun cardNameOf(text: String, maskStart: Int?): String {
        val head = if (maskStart != null && maskStart > 0) text.substring(0, maskStart) else ""
        if (head.isBlank()) return ""
        val cleaned = head
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(SECTION_NOISE, "")
            .trim()
            .trim('.', ',', '-', ':')
        // Sin letras no es un nombre (sería un monto o un rótulo).
        if (cleaned.length < 3 || cleaned.length > 40) return ""
        if (cleaned.none { it.isLetter() }) return ""
        return cleaned
    }

    /** "15-11-2026", "15/11/26", "2026-11-15" → epoch ms a medianoche UTC. */
    private fun dueAtOf(raw: String): Long? {
        val parts = raw.trim().split(Regex("[\\/\\-. ]")).filter { it.isNotBlank() }
        if (parts.size != 3) return null
        val nums = parts.map { it.toIntOrNull() ?: return null }
        val (year, month, day) = if (nums[0] > 31) {
            Triple(nums[0], nums[1], nums[2])
        } else {
            Triple(nums[2].let { if (it < 100) 2000 + it else it }, nums[1], nums[0])
        }
        if (month !in 1..12 || day !in 1..31) return null
        // Mediodía UTC: al formatear en hora local (UTC-4 en Venezuela) el día
        // no se corre ni hacia atrás ni hacia adelante.
        return daysFromCivil(year, month, day) * 86_400_000L + 43_200_000L
    }

    /** Días desde 1970-01-01 para una fecha civil (algoritmo de Howard Hinnant). */
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        var y = year
        y -= if (month <= 2) 1 else 0
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097L + doe - 719_468L
    }
}
