package dev.tubolsillo.app.core.crypto

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Cabecera del respaldo cifrado: versión 1 del formato. */
private const val PREFIX = "TUBS1:"

private const val SALT_BYTES = 16
private const val IV_BYTES = 12

/**
 * Respaldo cifrado con contraseña. El JSON completo (cuentas, movimientos,
 * capturas) se guarda como un sobre de texto:
 *
 * `TUBS1:` + base64(sal) + `:` + base64(iv) + `:` + base64(cifrado)
 *
 * La clave sale de la contraseña con PBKDF2-SHA256 (120 000 iteraciones) y el
 * contenido va en AES-256-GCM, que además detecta si la contraseña no era la
 * correcta. Un respaldo sin contraseña sigue siendo JSON plano.
 */
object BackupCipherFormat {
    const val PREFIX = "TUBS1:"
    const val TAG = "TUBS1"
}

/** ¿Este texto es un respaldo cifrado (y no un JSON plano)? */
fun isEncryptedBackup(content: String): Boolean =
    content.startsWith(BackupCipherFormat.PREFIX)

expect object BackupCipher {
    /** JSON → sobre cifrado. */
    fun encrypt(json: String, password: String): String

    /** Sobre cifrado → JSON. Lanza si la contraseña no es la correcta. */
    fun decrypt(armored: String, password: String): String
}

@OptIn(ExperimentalEncodingApi::class)
internal fun armoredPayload(salt: ByteArray, iv: ByteArray, data: ByteArray): String =
    buildString {
        append(BackupCipherFormat.PREFIX)
        append(Base64.encode(salt))
        append(':')
        append(Base64.encode(iv))
        append(':')
        append(Base64.encode(data))
    }

@OptIn(ExperimentalEncodingApi::class)
internal fun splitArmored(armored: String): Triple<ByteArray, ByteArray, ByteArray> {
    require(isEncryptedBackup(armored)) { "El archivo no es un respaldo cifrado." }
    val parts = armored.removePrefix(BackupCipherFormat.PREFIX).split(':')
    require(parts.size == 3) { "El respaldo cifrado está incompleto." }
    return Triple(
        Base64.decode(parts[0]),
        Base64.decode(parts[1]),
        Base64.decode(parts[2]),
    )
}

internal val SALT_SIZE = SALT_BYTES
internal val IV_SIZE = IV_BYTES
