package app.lawnchair.command

import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents
import com.android.launcher3.R

/** Exchange rates against [base], as fetched from the rates service; [updated] is epoch millis. */
data class CurrencyRates(val base: String, val rates: Map<String, Double>, val updated: Long) {
    /** How many units of [code] one unit of [base] buys, or null when the code is unknown. */
    fun rate(code: String): Double? = if (code == base) 1.0 else rates[code]
}

/**
 * What calc understands beyond plain math: "5 km em mi", "30 c em f", "100 usd em brl" and
 * "15% de 230". Pure, so it can be unit tested.
 */
object Conversions {

    private enum class Kind { LENGTH, MASS, VOLUME, AREA, SPEED, DATA, TIME, TEMPERATURE }

    /** A unit: what it measures and how many base units one of it is (meters, grams, liters...). */
    private class Unit(val kind: Kind, val factor: Double, vararg val names: String)

    private val UNITS = listOf(
        Unit(Kind.LENGTH, 0.001, "mm", "milimetro", "milimetros", "millimeter", "millimeters"),
        Unit(Kind.LENGTH, 0.01, "cm", "centimetro", "centimetros", "centimeter", "centimeters"),
        Unit(Kind.LENGTH, 1.0, "m", "metro", "metros", "meter", "meters", "metre", "metres"),
        Unit(Kind.LENGTH, 1000.0, "km", "quilometro", "quilometros", "kilometer", "kilometers"),
        Unit(Kind.LENGTH, 0.0254, "in", "pol", "polegada", "polegadas", "inch", "inches", "\""),
        Unit(Kind.LENGTH, 0.3048, "ft", "pe", "pes", "foot", "feet", "'"),
        Unit(Kind.LENGTH, 0.9144, "yd", "jarda", "jardas", "yard", "yards"),
        Unit(Kind.LENGTH, 1609.344, "mi", "milha", "milhas", "mile", "miles"),
        Unit(Kind.LENGTH, 1852.0, "nmi", "milha nautica", "milhas nauticas", "nautical mile"),
        Unit(Kind.MASS, 0.001, "mg", "miligrama", "miligramas", "milligram"),
        Unit(Kind.MASS, 1.0, "g", "grama", "gramas", "gram", "grams"),
        Unit(Kind.MASS, 1000.0, "kg", "quilo", "quilos", "quilograma", "quilogramas", "kilogram", "kilograms", "kilo", "kilos"),
        Unit(Kind.MASS, 1_000_000.0, "t", "ton", "tonelada", "toneladas", "tonne", "tonnes"),
        Unit(Kind.MASS, 28.349523125, "oz", "onca", "oncas", "ounce", "ounces"),
        Unit(Kind.MASS, 453.59237, "lb", "lbs", "libra", "libras", "pound", "pounds"),
        Unit(Kind.VOLUME, 0.001, "ml", "mililitro", "mililitros", "milliliter", "milliliters"),
        Unit(Kind.VOLUME, 1.0, "l", "litro", "litros", "liter", "liters", "litre", "litres"),
        Unit(Kind.VOLUME, 1000.0, "m3", "m³", "metro cubico", "metros cubicos", "cubic meter"),
        Unit(Kind.VOLUME, 3.785411784, "gal", "galao", "galoes", "gallon", "gallons"),
        Unit(Kind.VOLUME, 0.0295735295625, "floz", "fl oz", "fluid ounce"),
        Unit(Kind.VOLUME, 0.24, "xicara", "xicaras", "cup", "cups"),
        Unit(Kind.AREA, 1.0, "m2", "m²", "metro quadrado", "metros quadrados", "square meter"),
        Unit(Kind.AREA, 1_000_000.0, "km2", "km²", "quilometro quadrado", "square kilometer"),
        Unit(Kind.AREA, 10_000.0, "ha", "hectare", "hectares"),
        Unit(Kind.AREA, 4046.8564224, "acre", "acres"),
        Unit(Kind.AREA, 0.09290304, "ft2", "ft²", "pe quadrado", "square foot", "square feet"),
        Unit(Kind.SPEED, 1 / 3.6, "km/h", "kmh", "kph"),
        Unit(Kind.SPEED, 1.0, "m/s"),
        Unit(Kind.SPEED, 0.44704, "mph"),
        Unit(Kind.SPEED, 0.514444, "kn", "no", "nos", "knot", "knots"),
        Unit(Kind.DATA, 1.0, "b", "byte", "bytes"),
        Unit(Kind.DATA, 1024.0, "kb", "kib"),
        Unit(Kind.DATA, 1024.0 * 1024, "mb", "mib"),
        Unit(Kind.DATA, 1024.0 * 1024 * 1024, "gb", "gib"),
        Unit(Kind.DATA, 1024.0 * 1024 * 1024 * 1024, "tb", "tib"),
        Unit(Kind.TIME, 1.0, "s", "seg", "segundo", "segundos", "sec", "second", "seconds"),
        Unit(Kind.TIME, 60.0, "min", "minuto", "minutos", "minute", "minutes"),
        Unit(Kind.TIME, 3600.0, "h", "hora", "horas", "hour", "hours"),
        Unit(Kind.TIME, 86400.0, "d", "dia", "dias", "day", "days"),
        Unit(Kind.TIME, 604800.0, "sem", "semana", "semanas", "week", "weeks"),
        Unit(Kind.TEMPERATURE, 0.0, "c", "°c", "celsius"),
        Unit(Kind.TEMPERATURE, 1.0, "f", "°f", "fahrenheit"),
        Unit(Kind.TEMPERATURE, 2.0, "k", "kelvin"),
    )

    private val BY_NAME: Map<String, Unit> by lazy { UNITS.flatMap { u -> u.names.map { it to u } }.toMap() }

    /** Currency signs typed instead of the code. */
    private val SIGNS = mapOf("$" to "USD", "us$" to "USD", "r$" to "BRL", "€" to "EUR", "£" to "GBP", "¥" to "JPY")

    private fun toWords(): Set<String> = TimeParse.words(R.string.cmd_word_convert_to) + setOf("em", "para", "in", "to", "->", "=", "→")

    private fun ofWords(): Set<String> = TimeParse.words(R.string.cmd_word_percent_of) + setOf("de", "of")

    /** The converted value of [expression] and how it reads, or null when it is not a conversion. */
    data class Result(val shown: String, val value: Double, val result: String)

    fun convert(expression: String, rates: CurrencyRates?): Result? = percentOf(expression) ?: units(expression, rates)

    /** "15% de 230" is 34,5. */
    private fun percentOf(expression: String): Result? {
        val t = expression.trim().lowercase().foldAccents()
        val m = Regex("^(.+?)\\s*%\\s+(\\S+)\\s+(.+)$").matchEntire(t) ?: return null
        if (m.groupValues[2] !in ofWords()) return null
        val percent = CalcEvaluator.evaluate(m.groupValues[1]) ?: return null
        val whole = CalcEvaluator.evaluate(m.groupValues[3]) ?: return null
        val value = percent / 100.0 * whole
        val result = CalcEvaluator.format(value)
        return Result("${expression.trim()} = $result", value, result)
    }

    private val number = Regex("^([-+]?[\\d.,()+\\-*/ ]*\\d[\\d.,)]*)\\s*(.+)$")

    /** "5 km em mi", "100usd em brl", "R$ 50 em usd". */
    private fun units(expression: String, rates: CurrencyRates?): Result? {
        val t = expression.trim().lowercase().foldAccents().replace(Regex("\\s+"), " ")
        val separators = toWords()
        // The last separator word splits what is converted from what it becomes.
        val words = t.split(' ')
        val cut = words.indices.lastOrNull { it > 0 && it < words.size - 1 && words[it] in separators } ?: return null
        var from = words.take(cut).joinToString(" ")
        val to = words.drop(cut + 1).joinToString(" ").trim()
        // A sign before the number: "r$ 50", "$10".
        var signCode: String? = null
        SIGNS.keys.sortedByDescending { it.length }.firstOrNull { from.startsWith(it) }?.let { sign ->
            signCode = SIGNS[sign]
            from = from.removePrefix(sign).trim()
        }
        val amountText: String
        val unitText: String
        if (signCode != null) {
            amountText = from
            unitText = signCode!!.lowercase()
        } else {
            val m = number.matchEntire(from) ?: return null
            amountText = m.groupValues[1]
            unitText = m.groupValues[2].trim()
        }
        val amount = CalcEvaluator.evaluate(amountText) ?: return null
        val shownFrom = CalcEvaluator.format(amount)

        currencyCode(unitText, rates)?.let { fromCode ->
            val toCode = currencyCode(to, rates) ?: return null
            val r = rates ?: return null
            val fromRate = r.rate(fromCode) ?: return null
            val toRate = r.rate(toCode) ?: return null
            val value = amount / fromRate * toRate
            val result = formatMoney(value)
            return Result("$shownFrom $fromCode = $result $toCode", value, result)
        }

        val fromUnit = BY_NAME[unitText] ?: return null
        val toUnit = BY_NAME[to] ?: return null
        if (fromUnit.kind != toUnit.kind) return null
        val value = if (fromUnit.kind == Kind.TEMPERATURE) {
            temperature(amount, fromUnit.factor, toUnit.factor)
        } else {
            amount * fromUnit.factor / toUnit.factor
        }
        val result = CalcEvaluator.format(round(value))
        return Result("$shownFrom ${fromUnit.names[0]} = $result ${toUnit.names[0]}", value, result)
    }

    /** Six significant digits are plenty for a unit; more only shows float noise. */
    private fun round(value: Double): Double {
        if (value == 0.0 || !value.isFinite()) return value
        return java.math.BigDecimal(value).round(java.math.MathContext(6)).toDouble()
    }

    private fun formatMoney(value: Double): String = CalcEvaluator.format(java.math.BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP).toDouble())

    private fun currencyCode(text: String, rates: CurrencyRates?): String? {
        val t = text.trim()
        SIGNS[t]?.let { return it }
        if (t.length != 3 || !t.all { it.isLetter() }) return null
        val code = t.uppercase()
        // Without rates a currency cannot be told from a unit, but "usd" still is not a unit.
        return if (rates == null) code.takeIf { BY_NAME[t] == null } else code.takeIf { rates.rate(it) != null }
    }

    /** True when [expression] looks like a currency conversion that needs rates to compute. */
    fun needsRates(expression: String): Boolean {
        val t = expression.trim().lowercase().foldAccents()
        val words = t.split(Regex("\\s+"))
        val cut = words.indices.lastOrNull { it > 0 && it < words.size - 1 && words[it] in toWords() } ?: return false
        val to = words.drop(cut + 1).joinToString(" ")
        val from = words.take(cut).joinToString(" ")
        return (to.length == 3 && to.all { it.isLetter() } && BY_NAME[to] == null) || SIGNS.keys.any { from.startsWith(it) }
    }

    /** [code] 0 is Celsius, 1 Fahrenheit and 2 Kelvin. */
    private fun temperature(value: Double, from: Double, to: Double): Double {
        val celsius = when (from) {
            1.0 -> (value - 32) * 5 / 9
            2.0 -> value - 273.15
            else -> value
        }
        return when (to) {
            1.0 -> celsius * 9 / 5 + 32
            2.0 -> celsius + 273.15
            else -> celsius
        }
    }

    /** The preview when a currency is asked for and the rates are not loaded yet. */
    fun ratesPending(): String = Texts.get(R.string.cmd_calc_rates_pending)
}
