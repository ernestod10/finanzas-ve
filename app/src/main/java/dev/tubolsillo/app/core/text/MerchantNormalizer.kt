package dev.tubolsillo.app.core.text

import java.text.Normalizer

/**
 * Normaliza nombres de comercios para comparar SMS vs push vs CSV y para las
 * reglas de auto-etiquetado: sin diacríticos, lowercase, solo alfanuméricos,
 * sufijos legales ("c.a.", "s.a.", "j-1234") eliminados, truncado a 64 chars.
 * "FARMACIA X, C.A." y "farmacia x" normalizan al mismo string.
 */
object MerchantNormalizer {

    private val suffixRegex = Regex("(\\s+)(c\\.?a\\.?|s\\.?a\\.?|s\\.?r\\.?l\\.?|j-?\\d{6,10})(\\s*)$", RegexOption.IGNORE_CASE)

    fun normalize(raw: String): String {
        var s = raw.trim()
        // Quita sufijos legales del final.
        s = suffixRegex.replace(s, "")
        // Descompone diacríticos y deja solo ASCII.
        s = Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        s = s.lowercase()
        s = s.replace(Regex("[^a-z0-9]+"), "")
        return s.take(64)
    }
}
