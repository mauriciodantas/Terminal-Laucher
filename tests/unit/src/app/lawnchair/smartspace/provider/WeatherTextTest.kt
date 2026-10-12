package app.lawnchair.smartspace.provider

import app.lawnchair.util.Temperature
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherTextTest {

    private val brazil = Locale("pt", "BR")
    private val us = Locale.US

    private fun assertTemp(value: Int, unit: Temperature.Unit, text: String, locale: Locale = brazil) {
        val parsed = WeatherText.parse(text, locale)!!
        assertEquals(value, parsed.value)
        assertEquals(unit, parsed.unit)
    }

    @Test
    fun parse_readsTheUnitWhenPresent() {
        assertTemp(24, Temperature.Unit.Celsius, "24°C")
        assertTemp(24, Temperature.Unit.Celsius, "24 °C")
        assertTemp(75, Temperature.Unit.Fahrenheit, "75°F", brazil)
        assertTemp(290, Temperature.Unit.Kelvin, "290K")
    }

    @Test
    fun parse_withoutUnitFollowsTheCountry() {
        assertTemp(24, Temperature.Unit.Celsius, "24°", brazil)
        assertTemp(75, Temperature.Unit.Fahrenheit, "75°", us)
    }

    @Test
    fun parse_readsNegativeValues() {
        assertTemp(-3, Temperature.Unit.Celsius, "-3°C")
        assertTemp(-3, Temperature.Unit.Celsius, "−3 °C")
    }

    @Test
    fun parse_findsTheTemperatureInsideLongerText() {
        assertTemp(18, Temperature.Unit.Celsius, "Nublado · 18°")
    }

    @Test
    fun parse_isNullWithoutATemperature() {
        assertNull(WeatherText.parse(null))
        assertNull(WeatherText.parse("dom., 12 de out."))
        assertNull(WeatherText.parse("12 km"))
    }
}
