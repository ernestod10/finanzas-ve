package dev.tubolsillo.app.core.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM con clave derivada de la contraseña (PBKDF2-SHA256). */
actual object BackupCipher {

    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128

    actual fun encrypt(json: String, password: String): String {
        require(password.isNotBlank()) { "La contraseña no puede estar vacía." }
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            deriveKey(password, salt),
            GCMParameterSpec(TAG_BITS, iv),
        )
        val encrypted = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        return armoredPayload(salt, iv, encrypted)
    }

    actual fun decrypt(armored: String, password: String): String {
        val (salt, iv, data) = splitArmored(armored)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(password, salt),
            GCMParameterSpec(TAG_BITS, iv),
        )
        return try {
            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalArgumentException("Contraseña incorrecta o respaldo dañado.")
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val key = factory.generateSecret(spec).encoded
        return SecretKeySpec(key, "AES")
    }
}
