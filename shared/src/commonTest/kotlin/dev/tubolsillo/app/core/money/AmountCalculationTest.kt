package dev.tubolsillo.app.core.money

import dev.tubolsillo.app.core.money.AmountCalculation.Operator
import kotlin.test.Test
import kotlin.test.assertEquals

class AmountCalculationTest {

    private fun AmountCalculation.type(text: String): AmountCalculation {
        var state = this
        text.forEach { state = state.press(it) }
        return state
    }

    @Test
    fun `escribe un monto normal`() {
        val state = AmountCalculation().type("1200")
        assertEquals("1200", state.display)
    }

    @Test
    fun `la coma abre decimales sin repetirse`() {
        var state = AmountCalculation().type("1")
        state = state.press(',')
        state = state.press(',')
        state = state.press('5')
        assertEquals("1,5", state.display)
    }

    @Test
    fun `3 por 1200 da 3600`() {
        var state = AmountCalculation().type("3")
        state = state.withOperator(Operator.TIMES)
        state = state.type("1200")
        state = state.evaluate()
        assertEquals("3600", state.display)
    }

    @Test
    fun `resta puede dar negativo`() {
        var state = AmountCalculation().type("5")
        state = state.withOperator(Operator.MINUS)
        state = state.type("8")
        state = state.evaluate()
        assertEquals("-3", state.display)
    }

    @Test
    fun `division con decimales usa coma`() {
        var state = AmountCalculation().type("10")
        state = state.withOperator(Operator.DIVIDE)
        state = state.type("4")
        state = state.evaluate()
        assertEquals("2,5", state.display)
    }

    @Test
    fun `dividir entre cero no rompe la pantalla`() {
        var state = AmountCalculation().type("10")
        state = state.withOperator(Operator.DIVIDE)
        state = state.type("0")
        state = state.evaluate()
        assertEquals("10", state.display)
    }

    @Test
    fun `encadena operaciones usando el acumulado`() {
        var state = AmountCalculation().type("10")
        state = state.withOperator(Operator.PLUS)
        state = state.type("5")
        state = state.withOperator(Operator.PLUS)
        assertEquals("15", state.display)
        state = state.type("5")
        state = state.evaluate()
        assertEquals("20", state.display)
    }

    @Test
    fun `igual sin operador no toca el monto`() {
        val state = AmountCalculation().type("1200").evaluate()
        assertEquals("1200", state.display)
    }

    @Test
    fun `tras igual el siguiente digito reemplaza`() {
        var state = AmountCalculation().type("2")
        state = state.withOperator(Operator.PLUS)
        state = state.type("3")
        state = state.evaluate()
        state = state.press('9')
        assertEquals("9", state.display)
    }

    @Test
    fun `borrar quita el ultimo caracter`() {
        val state = AmountCalculation().type("123").backspace()
        assertEquals("12", state.display)
    }
}
