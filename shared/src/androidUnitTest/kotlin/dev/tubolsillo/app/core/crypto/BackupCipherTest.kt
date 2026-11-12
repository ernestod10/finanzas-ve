package dev.tubolsillo.app.core.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackupCipherTest {

    private val json = """{"accounts":[{"id":1,"name":"Bnc credit"}],"transactions":[]}"""

    @Test
    fun `cifra y descifra con la misma contrasena`() {
        val armored = BackupCipher.encrypt(json, "clave-secreta")
        assertTrue(isEncryptedBackup(armored))
        assertNotEquals(json, armored)
        assertEquals(json, BackupCipher.decrypt(armored, "clave-secreta"))
    }

    @Test
    fun `el sobre no repite el texto plano`() {
        val armored = BackupCipher.encrypt(json, "clave-secreta")
        assertFalse(armored.contains("Bnc credit"))
        assertFalse(armored.contains("transactions"))
    }

    @Test
    fun `con otra contrasena falla`() {
        val armored = BackupCipher.encrypt(json, "clave-secreta")
        assertFailsWith<IllegalArgumentException> {
            BackupCipher.decrypt(armored, "otra-clave")
        }
    }

    @Test
    fun `dos cifrados de lo mismo no son iguales`() {
        val a = BackupCipher.encrypt(json, "clave-secreta")
        val b = BackupCipher.encrypt(json, "clave-secreta")
        assertNotEquals(a, b)
    }

    @Test
    fun `un json plano no se confunde con un respaldo cifrado`() {
        assertFalse(isEncryptedBackup(json))
    }
}
