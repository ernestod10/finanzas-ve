package dev.tubolsillo.app.domain.update

/** Una versión publicada en GitHub Releases, lista para descargar. */
data class UpdateInfo(
    val versionName: String,
    val apkUrl: String,
    val notes: String?,
    val pageUrl: String,
)

/**
 * ¿[remote] es más nueva que [local]? Compara por partes numéricas
 * ("0.10.0" > "0.9.9") y tolera el prefijo `v` y sufijos como `-beta`.
 * Cualquier texto no numérico se ignora: la primera diferencia decide.
 */
fun isNewerVersion(remote: String, local: String): Boolean {
    val r = versionParts(remote)
    val l = versionParts(local)
    if (r.isEmpty()) return false
    for (i in 0 until maxOf(r.size, l.size)) {
        val a = r.getOrElse(i) { 0 }
        val b = l.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}

private fun versionParts(raw: String): List<Int> =
    raw.trim()
        .removePrefix("v")
        .removePrefix("V")
        .split('.', '-', '+')
        .mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
