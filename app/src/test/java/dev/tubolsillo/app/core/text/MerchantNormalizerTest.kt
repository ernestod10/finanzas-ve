package dev.tubolsillo.app.core.text

import org.junit.Assert.assertEquals
import org.junit.Test

class MerchantNormalizerTest {

    @Test
    fun `sufijos legales eliminados`() {
        assertEquals("farmaciax", MerchantNormalizer.normalize("FARMACIA X, C.A."))
        assertEquals("farmaciax", MerchantNormalizer.normalize("Farmacia X S.A"))
    }

    @Test
    fun `diacriticos y mayusculas`() {
        assertEquals("farmacia", MerchantNormalizer.normalize("Farmacía"))
        assertEquals("exito", MerchantNormalizer.normalize("ÉXITO"))
    }

    @Test
    fun `espacios y simbolos colapsados`() {
        // Los dígitos se conservan (solo alfanuméricos sobreviven).
        assertEquals("farmatodocaracas23", MerchantNormalizer.normalize("Farmatodo - Caracas #23!"))
    }

    @Test
    fun `sms y push normalizan igual`() {
        val sms = MerchantNormalizer.normalize("Compra Bs. 25,00 FARMACIA X, C.A.")
        val push = MerchantNormalizer.normalize("farmacia x")
        assertEquals("comprabs2500farmaciax", sms)
        assertEquals("farmaciax", push)
    }

    @Test
    fun `truncado a 64 chars`() {
        val long = "a".repeat(100)
        assertEquals(64, MerchantNormalizer.normalize(long).length)
    }
}
