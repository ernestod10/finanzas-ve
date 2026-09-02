package dev.tubolsillo.app.domain.usecase

import dev.tubolsillo.app.core.db.entity.AccountEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TotalsCalculatorTest {

    private val calculator = TotalsCalculator()

    private fun account(
        id: Long,
        kind: String,
        currency: String,
        balanceMinor: Long?,
        creditLimitMinor: Long? = null,
    ) = AccountEntity(
        id = id,
        institution = "TEST",
        name = "cuenta-$id",
        currency = currency,
        kind = kind,
        creditLimitMinor = creditLimitMinor,
        lastBalanceMinor = balanceMinor,
        lastBalanceAt = System.currentTimeMillis(),
    )

    // usdRate = 1 unidad en USD. VES a 50 Bs/$ → 1 Bs = 0.02 USD.
    private val rates = mapOf("USD" to 1.0, "USDT" to 1.0, "VES" to 0.02, "BTC" to 60000.0)

    @Test
    fun `activos y deuda separados`() {
        val accounts = listOf(
            account(1, "CHECKING", "USD", 100_00),          // $100 activo
            account(2, "SAVINGS", "VES", 500_00),            // Bs 500 = $10 activo
            account(3, "CREDIT_CARD", "USD", -250_00, 500_00), // $250 deuda
        )
        val totals = calculator.totals(accounts, rates)
        assertEquals(110.0, totals.assetsUsd, 0.001)
        assertEquals(250.0, totals.debtUsd, 0.001)
        assertEquals(-140.0, totals.netWorthUsd, 0.001)
    }

    @Test
    fun `usdt al peg`() {
        val accounts = listOf(account(1, "CRYPTO", "USDT", 1000_00))
        val totals = calculator.totals(accounts, rates)
        assertEquals(1000.0, totals.assetsUsd, 0.001)
    }

    @Test
    fun `crypto 8 decimales`() {
        val accounts = listOf(account(1, "CRYPTO", "BTC", 5_000_000)) // 0.05 BTC
        val totals = calculator.totals(accounts, rates)
        assertEquals(3000.0, totals.assetsUsd, 0.001)
    }

    @Test
    fun `cambiar tasa VES cambia el total`() {
        val accounts = listOf(account(1, "CHECKING", "VES", 1000_00)) // Bs 1000
        val totalsAt50 = calculator.totals(accounts, mapOf("VES" to 0.02)) // 50 Bs/$
        assertEquals(20.0, totalsAt50.assetsUsd, 0.001)
        val totalsAt100 = calculator.totals(accounts, mapOf("VES" to 0.01)) // 100 Bs/$
        assertEquals(10.0, totalsAt100.assetsUsd, 0.001)
    }

    @Test
    fun `cuenta sin saldo no aporta`() {
        val accounts = listOf(account(1, "CHECKING", "USD", null))
        val totals = calculator.totals(accounts, rates)
        assertEquals(0.0, totals.assetsUsd, 0.001)
        assertEquals(0.0, totals.netWorthUsd, 0.001)
    }
}
