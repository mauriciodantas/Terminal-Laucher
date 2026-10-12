package app.lawnchair.smartspace.provider

import app.lawnchair.util.Temperature
import java.util.Locale

/** Reads the temperature out of the text the Google app writes in its At a Glance widget. */
object WeatherText {

    /** Countries that write temperatures in Fahrenheit. */
    private val fahrenheitCountries = setOf("US", "BS", "BZ", "KY", "LR", "PW", "FM", "MH")

    // "24°C", "24 °C", "24°", "-3°F", "−3 °C" (Unicode minus) or "290K". A bare number is not
    // enough: it could be anything else in the widget.
    private val regex = Regex("""([-−]?\d{1,3})\s*(°\s*([CFK])?|([CFK])\b)""", RegexOption.IGNORE_CASE)

    /**
     * The temperature in [text], or null when it has none. The Google app leaves the unit out in
     * several languages ("24°"), so the unit then follows the country of [locale].
     */
    fun parse(text: String?, locale: Locale = Locale.getDefault()): Temperature? {
        val match = regex.find(text ?: return null) ?: return null
        val value = match.groupValues[1].replace('−', '-').toIntOrNull() ?: return null
        val letter = (match.groupValues[3].ifEmpty { match.groupValues[4] }).uppercase()
        val unit = when (letter) {
            "C" -> Temperature.Unit.Celsius
            "F" -> Temperature.Unit.Fahrenheit
            "K" -> Temperature.Unit.Kelvin
            else -> if (locale.country in fahrenheitCountries) Temperature.Unit.Fahrenheit else Temperature.Unit.Celsius
        }
        return Temperature(value, unit)
    }
}
