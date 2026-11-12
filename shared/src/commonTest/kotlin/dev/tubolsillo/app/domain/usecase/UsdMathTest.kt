package dev.tubolsillo.app.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsdMathTest {

    @Test
    fun `bolivares a la tasa BCV del dia`() {
        // Bs. 3.600,00 con dólar a 893 → $4,03
        assertEquals(403L, UsdMath.usdMinor(360_000, 2, 1.0 / 893.0))
    }

    @Test
    fun `las monedas de 8 decimales no se inflan`() {
        // 0,001 BTC a 60.000 → $60,00 (antes daba 6.000.000.000 por dividir entre 100)
        assertEquals(6_000L, UsdMath.usdMinor(100_000, 8, 60_000.0))
    }

    @Test
    fun `un monto en dolares queda igual`() {
        assertEquals(1_250L, UsdMath.usdMinor(1_250, 2, 1.0))
    }

    @Test
    fun `sin tasa no hay equivalente`() {
        assertNull(UsdMath.usdMinor(1_000, 2, 0.0))
        assertNull(UsdMath.usdMinor(1_000, 2, Double.NaN))
    }
}
