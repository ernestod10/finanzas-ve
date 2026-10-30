package dev.tubolsillo.app.ui.components

import androidx.compose.ui.graphics.Color

/** Paleta de colores personalizables por cuenta/tarjeta. */
val ACCOUNT_PALETTE = listOf(
    "#2E5E43", // verde bosque
    "#1E6FA3", // azul
    "#7A3FA0", // violeta
    "#B3553C", // terracota
    "#3E7C74", // verde azulado
    "#8A5A2B", // ámbar profundo
    "#5E5E8C", // índigo apagado
    "#9B3E6E", // frambuesa
    "#4F6D9E", // azul acero
    "#8A7A2E", // oliva
)

private val STOP_WORDS = setOf("de", "del", "la", "el", "los", "las", "y", "al")
private val initialsCache = mutableMapOf<String, String>()
private val colorCache = mutableMapOf<String, Color>()

/**
 * Monograma de marca para el chip de la cuenta (hasta 2 iniciales de las
 * palabras significativas): Banesco→B, Banco de Venezuela→BV, Mercantil
 * Panamá→MP, BOD→BOD, 100% Banco→1B.
 */
fun initialsFor(displayName: String): String {
    initialsCache[displayName]?.let { return it }
    val words = displayName.split(" ").filter { it.isNotBlank() && it.lowercase() !in STOP_WORDS }
    val result = when {
        words.isEmpty() -> "?"
        words.size == 1 && displayName.length <= 4 -> displayName.uppercase()
        else -> words.take(2)
            .map { w -> w.firstOrNull()?.uppercase() ?: "" }
            .joinToString("")
    }
    initialsCache[displayName] = result
    return result
}

/** Convierte "#RRGGBB" a Color; null si no es parseable. */
fun parseAccountColor(hex: String?): Color? {
    if (hex == null) return null
    colorCache[hex]?.let { return it }
    val clean = hex.removePrefix("#")
    if (clean.length != 6) return null
    val parsed = runCatching {
        Color(("FF" + clean).toLong(16))
    }.getOrNull()
    if (parsed != null) colorCache[hex] = parsed
    return parsed
}
