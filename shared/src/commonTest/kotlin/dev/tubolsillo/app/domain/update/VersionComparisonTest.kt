package dev.tubolsillo.app.domain.update

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionComparisonTest {

    @Test
    fun `una version mayor es mas nueva`() {
        assertTrue(isNewerVersion("0.2.0", "0.1.0"))
        assertTrue(isNewerVersion("1.0.0", "0.9.9"))
        assertTrue(isNewerVersion("0.1.1", "0.1.0"))
    }

    @Test
    fun `la misma version no es nueva`() {
        assertFalse(isNewerVersion("0.1.0", "0.1.0"))
        assertFalse(isNewerVersion("v0.1.0", "0.1.0"))
    }

    @Test
    fun `una version menor no es nueva`() {
        assertFalse(isNewerVersion("0.1.0", "0.2.0"))
        assertFalse(isNewerVersion("0.9.9", "1.0.0"))
    }

    @Test
    fun `compara numero a numero y no como texto`() {
        // Como texto "0.10.0" < "0.9.0"; como números es al revés.
        assertTrue(isNewerVersion("0.10.0", "0.9.0"))
        assertFalse(isNewerVersion("0.9.0", "0.10.0"))
    }

    @Test
    fun `tolera prefijos sufijos y partes faltantes`() {
        assertTrue(isNewerVersion("v1.2", "1.1.9"))
        assertTrue(isNewerVersion("1.2.0-beta", "1.1.0"))
        assertFalse(isNewerVersion("basura", "1.0.0"))
        assertFalse(isNewerVersion("1.0.0", "1.0.0.1"))
    }
}
