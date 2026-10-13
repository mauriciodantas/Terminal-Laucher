package app.lawnchair.command

/**
 * A small calculator for the command bar: + - * / ( ), unary minus, a postfix % (divides by 100)
 * and both "." and "," as the decimal mark. It never throws: an expression that is incomplete or
 * cannot be computed gives null.
 */
object CalcEvaluator {

    fun evaluate(expression: String): Double? {
        val parser = Parser(expression.replace(',', '.').filterNot { it.isWhitespace() })
        val value = runCatching { parser.parseAll() }.getOrNull() ?: return null
        return value.takeIf { it.isFinite() }
    }

    /** The result as the bar shows it: up to 8 decimals, no trailing zeros, comma as the mark. */
    fun format(value: Double): String {
        val rounded = java.math.BigDecimal(value).setScale(8, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
        val text = if (rounded.scale() < 0) rounded.setScale(0).toPlainString() else rounded.toPlainString()
        return (if (text == "-0") "0" else text).replace('.', ',')
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parseAll(): Double? {
            if (s.isEmpty()) return null
            val v = expression() ?: return null
            return if (i == s.length) v else null
        }

        private fun expression(): Double? {
            var v = term() ?: return null
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val r = term() ?: return null
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        private fun term(): Double? {
            var v = unary() ?: return null
            while (i < s.length && (s[i] == '*' || s[i] == '/')) {
                val op = s[i++]
                val r = unary() ?: return null
                v = if (op == '*') v * r else v / r
            }
            return v
        }

        private fun unary(): Double? {
            if (i < s.length && s[i] == '-') {
                i++
                return unary()?.let { -it }
            }
            if (i < s.length && s[i] == '+') {
                i++
                return unary()
            }
            return postfix()
        }

        private fun postfix(): Double? {
            var v = primary() ?: return null
            while (i < s.length && s[i] == '%') {
                i++
                v /= 100.0
            }
            return v
        }

        private fun primary(): Double? {
            if (i >= s.length) return null
            if (s[i] == '(') {
                i++
                val v = expression() ?: return null
                if (i >= s.length || s[i] != ')') return null
                i++
                return v
            }
            val start = i
            var dots = 0
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) {
                if (s[i] == '.') dots++
                i++
            }
            if (i == start || dots > 1) return null
            return s.substring(start, i).toDoubleOrNull()
        }
    }
}
