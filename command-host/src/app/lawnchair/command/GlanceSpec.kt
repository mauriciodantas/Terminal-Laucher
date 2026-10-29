package app.lawnchair.command

import org.json.JSONObject

/** An animation of its own for the At a Glance panel: the Lottie file itself, or a link the phone downloads when the theme is applied. */
sealed interface GlanceLottie {
    data class Url(val url: String) : GlanceLottie

    /** [json] is the whole file. */
    data class Json(val json: String) : GlanceLottie {
        override fun toString() = "Json(${json.length} chars)"
    }
}

/**
 * What a theme says about the At a Glance panel (the readout at the top of the home screen): hide it,
 * the animation beside its big value (one of [GlanceSpecs.ANIMATIONS] or a Lottie of its own), the rows
 * under it and the kinds of information it may show. A null field is left as it is.
 */
data class GlanceSpec(
    /** False hides the whole panel, true shows it. */
    val show: Boolean? = null,
    /** One of [GlanceSpecs.ANIMATIONS]; "off" has no animation. */
    val animation: String? = null,
    /** An animation of its own; it wins over [animation]. */
    val lottie: GlanceLottie? = null,
    /** Paints the Lottie in the theme color instead of the colors it was drawn with. */
    val tint: Boolean? = null,
    /** The row of utility shortcuts (flashlight, calculator, camera, clock). */
    val shortcuts: Boolean? = null,
    /** The row of chat shortcuts. */
    val chats: Boolean? = null,
    val agenda: Boolean? = null,
    val weather: Boolean? = null,
    val alarm: Boolean? = null,
    val reminders: Boolean? = null,
    val bluetooth: Boolean? = null,
    /** The panel turns through its targets on its own. */
    val autoRotate: Boolean? = null,
) {
    val isEmpty: Boolean get() = this == GlanceSpec()

    /** [other]'s fields where it has them, this one's elsewhere. */
    fun overlaidWith(other: GlanceSpec) = GlanceSpec(
        show = other.show ?: show,
        animation = other.animation ?: animation,
        lottie = other.lottie ?: lottie,
        tint = other.tint ?: tint,
        shortcuts = other.shortcuts ?: shortcuts,
        chats = other.chats ?: chats,
        agenda = other.agenda ?: agenda,
        weather = other.weather ?: weather,
        alarm = other.alarm ?: alarm,
        reminders = other.reminders ?: reminders,
        bluetooth = other.bluetooth ?: bluetooth,
        autoRotate = other.autoRotate ?: autoRotate,
    )

    /** What it changes, as short items for the import screen. */
    val items: List<String>
        get() = buildList {
            show?.let { add(if (it) "shown" else "hidden") }
            lottie?.let {
                add(
                    when (it) {
                        is GlanceLottie.Url -> "lottie · " + (runCatching { java.net.URI(it.url).host }.getOrNull() ?: it.url)
                        is GlanceLottie.Json -> "lottie · ${it.json.length / 1024 + 1} KB"
                    },
                )
            } ?: animation?.let { add("animation $it") }
            tint?.let { add(if (it) "tinted" else "own colors") }
            shortcuts?.let { add(if (it) "shortcuts" else "no shortcuts") }
            chats?.let { add(if (it) "chats" else "no chats") }
            listOf("agenda" to agenda, "weather" to weather, "alarm" to alarm, "reminders" to reminders, "bluetooth" to bluetooth).forEach { (name, on) ->
                on?.let { add(if (it) name else "no $name") }
            }
            autoRotate?.let { add(if (it) "rotates" else "stays still") }
        }
}

object GlanceSpecs {

    /** The animations the panel has beside its big value, by the id it stores; "off" has none. */
    val ANIMATIONS = listOf("off", "radar", "sonar", "wave", "bars")

    /** The animation [text] names, in any case ("none" is "off"); null for an unknown one. */
    fun animationKey(text: String): String? {
        val t = text.trim().lowercase().let { if (it == "none") "off" else it }
        return ANIMATIONS.firstOrNull { it == t }
    }

    fun toJson(g: GlanceSpec): JSONObject = JSONObject().apply {
        g.show?.let { put("show", it) }
        g.animation?.let { put("animation", it) }
        g.lottie?.let {
            put(
                "lottie",
                when (it) {
                    is GlanceLottie.Url -> JSONObject().put("url", it.url)
                    is GlanceLottie.Json -> JSONObject().put("json", JSONObject(it.json))
                },
            )
        }
        g.tint?.let { put("tint", it) }
        g.shortcuts?.let { put("shortcuts", it) }
        g.chats?.let { put("chats", it) }
        g.autoRotate?.let { put("autoRotate", it) }
        val targets = JSONObject().apply {
            g.agenda?.let { put("agenda", it) }
            g.weather?.let { put("weather", it) }
            g.alarm?.let { put("alarm", it) }
            g.reminders?.let { put("reminders", it) }
            g.bluetooth?.let { put("bluetooth", it) }
        }
        if (targets.length() > 0) put("targets", targets)
    }

    /** The panel [value] describes, with unknown or broken values left out; null when it says nothing. */
    fun fromJson(value: Any?): GlanceSpec? {
        val o = value as? JSONObject ?: return null
        val t = o.optJSONObject("targets")
        return GlanceSpec(
            show = bool(o, "show"),
            animation = (o.opt("animation") as? String)?.let(::animationKey),
            lottie = lottie(o.opt("lottie")),
            tint = bool(o, "tint"),
            shortcuts = bool(o, "shortcuts"),
            chats = bool(o, "chats"),
            agenda = t?.let { bool(it, "agenda") },
            weather = t?.let { bool(it, "weather") },
            alarm = t?.let { bool(it, "alarm") },
            reminders = t?.let { bool(it, "reminders") },
            bluetooth = t?.let { bool(it, "bluetooth") },
            autoRotate = bool(o, "autoRotate"),
        ).takeUnless { it.isEmpty }
    }

    /** `{"url": …}`, `{"json": {…}}` (or the file itself under "lottie") or a bare link; null when it is none or breaks the limits. */
    private fun lottie(value: Any?): GlanceLottie? {
        if (value is String) return ThemeSpecs.urlImage(value)?.let { GlanceLottie.Url(it.url) }
        val o = value as? JSONObject ?: return null
        if (o.has("url")) return ThemeSpecs.urlImage(o.optString("url"))?.let { GlanceLottie.Url(it.url) }
        val file = o.optJSONObject("json") ?: o.optJSONObject("lottie") ?: return null
        if (file.optJSONArray("layers") == null) return null
        val json = file.toString()
        return GlanceLottie.Json(json).takeIf { json.length <= ThemeSpecs.MAX_LOTTIE_CHARS }
    }

    private fun bool(o: JSONObject, key: String): Boolean? = when (val v = o.opt(key)) {
        is Boolean -> v
        is String -> v.toBooleanStrictOrNull()
        else -> null
    }
}
