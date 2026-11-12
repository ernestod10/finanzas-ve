package dev.tubolsillo.app.core.money

/**
 * Calculadora del teclado del monto: suma, resta, multiplica y divide dos
 * números antes de guardarlos ("3 × 1200 = 3600").
 *
 * Es una máquina de estados inmutable y sin Compose para poder probarla: el
 * teclado solo dibuja lo que devuelve [AmountCalculation.press]. El resultado
 * se escribe con coma decimal, que es lo que entiende `AmountNormalizer`.
 */
data class AmountCalculation(
    /** Lo que se muestra y, al final, lo que se guarda. */
    val display: String = "",
    /** Acumulado de la operación en curso; null cuando no hay nada pendiente. */
    val acc: Double? = null,
    val op: Operator? = null,
    /** true = el próximo dígito reemplaza la pantalla (se acaba de operar). */
    val fresh: Boolean = false,
) {

    enum class Operator(val symbol: String) {
        PLUS("+"), MINUS("−"), TIMES("×"), DIVIDE("÷"),
    }

    /** Solo se puede resolver cuando hay operador y un segundo número escrito. */
    val canEvaluate: Boolean get() = acc != null && op != null && display.isNotBlank()

    fun press(key: Char, decimals: Int = 2): AmountCalculation = when {
        key.isDigit() -> withDigit(key)
        key == ',' || key == '.' -> withComma(decimals)
        else -> this
    }

    fun withDigit(digit: Char): AmountCalculation {
        if (fresh) return copy(display = digit.toString(), fresh = false)
        if (display.length >= 12) return this
        // Sin ceros a la izquierda que no aporten ("0" + "5" → "5").
        if (display == "0") return copy(display = digit.toString())
        return copy(display = display + digit)
    }

    fun withComma(decimals: Int): AmountCalculation {
        if (decimals <= 0) return this
        if (fresh) return copy(display = "0,", fresh = false)
        if (display.contains(',')) return this
        return copy(display = if (display.isEmpty()) "0," else display + ",")
    }

    fun backspace(): AmountCalculation =
        if (fresh || display.isEmpty()) copy(fresh = false) else copy(display = display.dropLast(1))

    fun withOperator(operator: Operator, decimals: Int = 2): AmountCalculation {
        val current = value
        if (acc == null) {
            return copy(acc = current, op = operator, fresh = true)
        }
        if (display.isBlank()) {
            // Cambiar de operador sin escribir el segundo número.
            return copy(op = operator)
        }
        val folded = apply(acc, current, op ?: operator)
        return copy(acc = folded, op = operator, display = format(folded, decimals), fresh = true)
    }

    /** Resuelve la operación pendiente. Sin operador, deja la pantalla igual. */
    fun evaluate(decimals: Int = 2): AmountCalculation {
        if (!canEvaluate) return this
        val result = apply(acc ?: 0.0, value, op ?: return this)
        return copy(display = format(result, decimals), acc = null, op = null, fresh = true)
    }

    /** Valor numérico de lo escrito ("1.200,50" no: el display no lleva puntos). */
    val value: Double
        get() = display.replace(',', '.').toDoubleOrNull() ?: 0.0

    private fun apply(a: Double, b: Double, operator: Operator): Double = when (operator) {
        Operator.PLUS -> a + b
        Operator.MINUS -> a - b
        Operator.TIMES -> a * b
        Operator.DIVIDE -> if (b == 0.0) a else a / b
    }

    private fun format(n: Double, decimals: Int): String {
        if (n.isNaN() || n.isInfinite()) return "0"
        val factor = when (decimals) {
            0 -> 1.0
            1 -> 10.0
            2 -> 100.0
            3 -> 1_000.0
            4 -> 10_000.0
            6 -> 1_000_000.0
            else -> 100.0
        }
        val rounded = kotlin.math.round(n * factor) / factor
        val whole = rounded.toLong()
        val text = if (rounded == whole.toDouble()) whole.toString() else rounded.toString()
        return text.replace('.', ',')
    }
}
