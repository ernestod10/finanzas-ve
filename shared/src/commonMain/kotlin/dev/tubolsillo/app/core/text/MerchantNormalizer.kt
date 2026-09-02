package dev.tubolsillo.app.core.text

/**
 * Normaliza nombres de comercios para comparar SMS vs push vs CSV y para las
 * reglas de auto-etiquetado: sin acentos (mapa es-VE), lowercase, solo
 * alfanuméricos, sufijos legales ("c.a.", "s.a.", "j-1234") eliminados,
 * truncado a 64 chars. "FARMACIA X, C.A." y "farmacia x" normalizan igual.
 * Sin dependencias JVM (java.text.Normalizer) para poder vivir en commonMain.
 */
object MerchantNormalizer {

    private val suffixRegex = Regex(
        "(\\s+)(c\\.?a\\.?|s\\.?a\\.?|s\\.?r\\.?l\\.?|j-?\\d{6,10})(\\s*)$",
        RegexOption.IGNORE_CASE,
    )

    private val accents = mapOf(
        'á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n',
    )

    fun normalize(raw: String): String {
        var s = raw.trim()
        // Quita sufijos legales del final.
        s = suffixRegex.replace(s, "")
        // Lowercase + sin acentos + solo alfanuméricos.
        s = s.lowercase().map { accents[it] ?: it }.joinToString("")
        s = s.replace(Regex("[^a-z0-9]+"), "")
        return s.take(64)
    }
}
