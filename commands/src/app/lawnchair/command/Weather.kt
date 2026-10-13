package app.lawnchair.command

import app.lawnchair.util.Texts
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * "clima": the weather from Open-Meteo, which needs no key. A city name is looked up with its
 * geocoding service first; "clima" alone uses the last known location. Only the city name or the
 * rounded coordinates leave the phone.
 */
object Weather {

    data class Place(val name: String, val latitude: Double, val longitude: Double)

    fun geocodeUrl(name: String, language: String): String = "https://geocoding-api.open-meteo.com/v1/search?count=1&format=json" +
        "&language=" + language + "&name=" + URLEncoder.encode(name.trim(), "UTF-8").replace("+", "%20")

    fun forecastUrl(latitude: Double, longitude: Double): String = "https://api.open-meteo.com/v1/forecast" +
        "?latitude=" + coordinate(latitude) + "&longitude=" + coordinate(longitude) +
        "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
        "&timezone=auto&forecast_days=3"

    /** Two decimals are about a kilometer: enough for the weather, and no more than that is sent. */
    private fun coordinate(value: Double): String = String.format(Locale.US, "%.2f", value)

    /** The first place of a geocoding answer, named "Recife, Pernambuco", or null. */
    fun place(body: String): Place? = runCatching {
        val first = JSONObject(body).optJSONArray("results")?.optJSONObject(0) ?: return null
        val name = listOf(first.optString("name"), first.optString("admin1"))
            .filter { it.isNotBlank() }.distinct().joinToString(", ")
        Place(name, first.getDouble("latitude"), first.getDouble("longitude"))
    }.getOrNull()

    /** "lat,long" as [CommandExtras.location] holds it. */
    fun here(location: String): Place? {
        val parts = location.split(',').mapNotNull { it.trim().toDoubleOrNull() }
        if (parts.size != 2) return null
        return Place(Texts.get(R.string.cmd_weather_here_name), parts[0], parts[1])
    }

    /** The lines the bar shows for a forecast answer, or null when it is not one. */
    fun text(body: String, place: String, locale: Locale = Locale.getDefault()): String? = runCatching {
        val o = JSONObject(body)
        val current = o.getJSONObject("current")
        val daily = o.getJSONObject("daily")
        buildList {
            add(place.uppercase())
            add(
                Texts.get(
                    R.string.cmd_weather_now,
                    degrees(current.getDouble("temperature_2m")),
                    degrees(current.getDouble("apparent_temperature")),
                    describe(current.getInt("weather_code")),
                ),
            )
            add(Texts.get(R.string.cmd_weather_air, current.getInt("relative_humidity_2m"), current.getDouble("wind_speed_10m").roundToInt()))
            val days = daily.getJSONArray("time")
            for (i in 0 until minOf(days.length(), 3)) {
                val day = when (i) {
                    0 -> Texts.get(R.string.cmd_weather_today)
                    1 -> Texts.get(R.string.cmd_weather_tomorrow)
                    else -> LocalDate.parse(days.getString(i)).dayOfWeek.getDisplayName(TextStyle.SHORT, locale).trimEnd('.')
                }
                val rain = daily.optJSONArray("precipitation_probability_max")?.takeUnless { it.isNull(i) }?.optInt(i)
                add(
                    listOfNotNull(
                        day + " " + degrees(daily.getJSONArray("temperature_2m_min").getDouble(i)) + "–" + degrees(daily.getJSONArray("temperature_2m_max").getDouble(i)),
                        describe(daily.getJSONArray("weather_code").getInt(i)),
                        rain?.let { Texts.get(R.string.cmd_weather_rain, it) },
                    ).joinToString(" · "),
                )
            }
        }.joinToString("\n")
    }.getOrNull()

    private fun degrees(value: Double): String = "${value.roundToInt()}°"

    /** A WMO weather code in words. */
    fun describe(code: Int): String = Texts.get(
        when (code) {
            0 -> R.string.cmd_wx_clear
            1 -> R.string.cmd_wx_mostly_clear
            2 -> R.string.cmd_wx_partly_cloudy
            3 -> R.string.cmd_wx_overcast
            45, 48 -> R.string.cmd_wx_fog
            51, 53, 55, 56, 57 -> R.string.cmd_wx_drizzle
            61, 66 -> R.string.cmd_wx_rain_light
            63 -> R.string.cmd_wx_rain
            65, 67 -> R.string.cmd_wx_rain_heavy
            71, 73, 75, 77, 85, 86 -> R.string.cmd_wx_snow
            80, 81 -> R.string.cmd_wx_showers
            82 -> R.string.cmd_wx_rain_heavy
            95 -> R.string.cmd_wx_storm
            96, 99 -> R.string.cmd_wx_storm_hail
            else -> R.string.cmd_wx_unknown
        },
    )

    /** Looks the place up when needed and fetches the forecast; what the bar shows either way. Blocking. */
    fun run(action: CommandAction.Weather, language: String = Locale.getDefault().language): String {
        val provider = Providers.OPEN_METEO
        val place = if (action.place.isNotEmpty()) {
            when (val found = HttpRunner.get(provider, geocodeUrl(action.place, language), null)) {
                is HttpResult.Answer -> place(found.body) ?: return Texts.get(R.string.cmd_weather_not_found, action.place.uppercase())
                else -> return HttpRunner.describe(found, provider)
            }
        } else {
            action.location?.let { here(it) } ?: return Texts.get(R.string.cmd_weather_no_location)
        }
        return when (val forecast = HttpRunner.get(provider, forecastUrl(place.latitude, place.longitude), null)) {
            is HttpResult.Answer -> text(forecast.body, place.name) ?: Texts.get(R.string.cmd_api_empty)
            else -> HttpRunner.describe(forecast, provider)
        }
    }
}
