package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.domain.model.Institution
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.Test

/** Prueba la generación pura de dedup keys (función estática del companion). */
class DedupGateTest {

    @Test
    fun `misma captura genera la misma clave`() {
        val key1 = key(Institution.BANESCO, -2500, 1_000_000L, "FARMACIA X")
        val key2 = key(Institution.BANESCO, -2500, 1_000_000L, "FARMACIA X")
        assertEquals(key1, key2)
    }

    @Test
    fun `sms y push con comercio escrito distinto dedupean`() {
        // "FARMACIA X, C.A." vs "farmacia x" → mismo merchant normalizado.
        val sms = key(Institution.BANESCO, -2500, 1_000_000L, "FARMACIA X, C.A.")
        val push = key(Institution.BANESCO, -2500, 1_000_000L, "farmacia x")
        assertEquals(sms, push)
    }

    @Test
    fun `mismo monto en el mismo bucket de hora`() {
        val a = key(Institution.BANESCO, -2500, 1_000_000L, "TIENDA A")
        val b = key(Institution.BANESCO, -2500, 1_000_000L + 30_000, "TIENDA A")
        assertEquals(a, b) // mismos 60 min
    }

    @Test
    fun `bucket distinto no dedupea`() {
        val a = key(Institution.BANESCO, -2500, 1_000_000L, "TIENDA A")
        val b = key(Institution.BANESCO, -2500, 1_000_000L + 3_600_000, "TIENDA A")
        assertNotEquals(a, b)
    }

    @Test
    fun `monto distinto no dedupea`() {
        val a = key(Institution.BANESCO, -2500, 1_000_000L, "TIENDA A")
        val b = key(Institution.BANESCO, -2501, 1_000_000L, "TIENDA A")
        assertNotEquals(a, b)
    }

    @Test
    fun `institucion distinta no dedupea`() {
        val a = key(Institution.BANESCO, -2500, 1_000_000L, "TIENDA A")
        val b = key(Institution.BDV, -2500, 1_000_000L, "TIENDA A")
        assertNotEquals(a, b)
    }

    @Test
    fun `signo no importa`() {
        val a = key(Institution.BANESCO, -2500, 1_000_000L, "TIENDA A")
        val b = key(Institution.BANESCO, 2500, 1_000_000L, "TIENDA A")
        assertEquals(a, b)
    }

    private fun key(inst: Institution, amountMinor: Long, occurredAt: Long, merchant: String) =
        DedupGate.companionDedupKey(inst, "VES", amountMinor, occurredAt, merchant)
}
