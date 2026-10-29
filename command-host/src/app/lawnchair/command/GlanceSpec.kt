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
 * How the At a Glance panel is drawn: a ready-made [preset] and, over it, each setting on its own.
 * Ids and ranges are the ones in [GlanceSpecs]; a null field is left as it is.
 */
data class GlanceLook(
    /** One of [GlanceSpecs.PRESETS]; it is applied first, so the fields below refine it. */
    val preset: String? = null,
    val header: Boolean? = null,
    val date: Boolean? = null,
    val status: Boolean? = null,
    val clockFormat: String? = null,
    val dateFormat: String? = null,
    val seconds: Boolean? = null,
    val dateCaps: Boolean? = null,
    /** Percent of the default size, [GlanceSpecs.CLOCK_SIZE]. */
    val clockSize: Int? = null,
    /** Percent of the default size of the target text, [GlanceSpecs.TEXT_SIZE]. */
    val textSize: Int? = null,
    val align: String? = null,
    val density: String? = null,
    val border: Boolean? = null,
    val borderWidth: Int? = null,
    val radius: Int? = null,
    val titleBar: String? = null,
    val glow: Boolean? = null,
    /** Strength of the secondary text, in percent of the accent, [GlanceSpecs.DIM]. */
    val dim: Int? = null,
    val urgent: String? = null,
    val statusSound: Boolean? = null,
    val statusStorage: Boolean? = null,
    val statusRam: Boolean? = null,
    /** Names of the utility shortcuts to hide, from [GlanceSpecs.SHORTCUTS]; empty shows all of them. */
    val hiddenShortcuts: List<String>? = null,
) {
    val isEmpty: Boolean get() = this == GlanceLook()

    /** [other]'s fields where it has them, this one's elsewhere. */
    fun overlaidWith(other: GlanceLook) = GlanceLook(
        preset = other.preset ?: preset,
        header = other.header ?: header,
        date = other.date ?: date,
        status = other.status ?: status,
        clockFormat = other.clockFormat ?: clockFormat,
        dateFormat = other.dateFormat ?: dateFormat,
        seconds = other.seconds ?: seconds,
        dateCaps = other.dateCaps ?: dateCaps,
        clockSize = other.clockSize ?: clockSize,
        textSize = other.textSize ?: textSize,
        align = other.align ?: align,
        density = other.density ?: density,
        border = other.border ?: border,
        borderWidth = other.borderWidth ?: borderWidth,
        radius = other.radius ?: radius,
        titleBar = other.titleBar ?: titleBar,
        glow = other.glow ?: glow,
        dim = other.dim ?: dim,
        urgent = other.urgent ?: urgent,
        statusSound = other.statusSound ?: statusSound,
        statusStorage = other.statusStorage ?: statusStorage,
        statusRam = other.statusRam ?: statusRam,
        hiddenShortcuts = other.hiddenShortcuts ?: hiddenShortcuts,
    )

    /** What it changes, as one short item for the import screen. */
    val label: String
        get() = buildList {
            preset?.let { add("style $it") }
            add(
                "${listOfNotNull(
                    header, date, status, seconds, dateCaps, border, glow, statusSound, statusStorage, statusRam,
                ).size + listOfNotNull(
                    clockFormat, dateFormat, clockSize, textSize, align, density, borderWidth, radius, titleBar, dim, urgent, hiddenShortcuts,
                ).size} settings",
            )
        }.joinToString(" · ")
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
    /** How the panel is drawn: a ready-made style and each of its settings. */
    val look: GlanceLook? = null,
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
        look = look?.let { l -> other.look?.let(l::overlaidWith) ?: l } ?: other.look,
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
            look?.let { add(it.label) }
        }
}

object GlanceSpecs {

    /** The animations the panel has beside its big value, by the id it stores; "off" has none. */
    val ANIMATIONS = listOf("off", "radar", "sonar", "wave", "bars")

    val PRESETS = listOf("terminal", "outline", "open", "centered", "minimal")
    val CLOCK_FORMATS = listOf("system", "24h", "12h")
    val DATE_FORMATS = listOf("full", "short", "numeric", "weekday")
    val ALIGNS = listOf("start", "center", "end")
    val DENSITIES = listOf("comfortable", "compact")
    val TITLE_BARS = listOf("filled", "outline")
    val URGENT_COLORS = listOf("red", "amber", "magenta")
    val SHORTCUTS = listOf("torch", "calculator", "camera", "clock")
    val CLOCK_SIZE = 60..140
    val TEXT_SIZE = 80..140
    val BORDER_WIDTH = 1..3
    val RADIUS = 0..16
    val DIM = 40..90

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
        g.look?.let { put("look", lookJson(it)) }
    }

    private fun lookJson(l: GlanceLook): JSONObject = JSONObject().apply {
        l.preset?.let { put("preset", it) }
        l.header?.let { put("header", it) }
        l.date?.let { put("date", it) }
        l.status?.let { put("status", it) }
        l.clockFormat?.let { put("clockFormat", it) }
        l.dateFormat?.let { put("dateFormat", it) }
        l.seconds?.let { put("seconds", it) }
        l.dateCaps?.let { put("dateCaps", it) }
        l.clockSize?.let { put("clockSize", it) }
        l.textSize?.let { put("textSize", it) }
        l.align?.let { put("align", it) }
        l.density?.let { put("density", it) }
        l.border?.let { put("border", it) }
        l.borderWidth?.let { put("borderWidth", it) }
        l.radius?.let { put("radius", it) }
        l.titleBar?.let { put("titleBar", it) }
        l.glow?.let { put("glow", it) }
        l.dim?.let { put("dim", it) }
        l.urgent?.let { put("urgent", it) }
        val items = JSONObject().apply {
            l.statusSound?.let { put("sound", it) }
            l.statusStorage?.let { put("storage", it) }
            l.statusRam?.let { put("ram", it) }
        }
        if (items.length() > 0) put("statusItems", items)
        l.hiddenShortcuts?.let { put("hiddenShortcuts", org.json.JSONArray(it)) }
    }

    /** The look [value] describes, with unknown ids dropped and numbers held to their range; null when it says nothing. */
    fun lookFromJson(value: Any?): GlanceLook? {
        val o = value as? JSONObject ?: return null
        val items = o.optJSONObject("statusItems")
        return GlanceLook(
            preset = choice(o, "preset", PRESETS),
            header = bool(o, "header"),
            date = bool(o, "date"),
            status = bool(o, "status"),
            clockFormat = choice(o, "clockFormat", CLOCK_FORMATS),
            dateFormat = choice(o, "dateFormat", DATE_FORMATS),
            seconds = bool(o, "seconds"),
            dateCaps = bool(o, "dateCaps"),
            clockSize = number(o, "clockSize", CLOCK_SIZE),
            textSize = number(o, "textSize", TEXT_SIZE),
            align = choice(o, "align", ALIGNS),
            density = choice(o, "density", DENSITIES),
            border = bool(o, "border"),
            borderWidth = number(o, "borderWidth", BORDER_WIDTH),
            radius = number(o, "radius", RADIUS),
            titleBar = choice(o, "titleBar", TITLE_BARS),
            glow = bool(o, "glow"),
            dim = number(o, "dim", DIM),
            urgent = choice(o, "urgent", URGENT_COLORS),
            statusSound = items?.let { bool(it, "sound") },
            statusStorage = items?.let { bool(it, "storage") },
            statusRam = items?.let { bool(it, "ram") },
            hiddenShortcuts = o.optJSONArray("hiddenShortcuts")?.let { array ->
                (0 until array.length()).mapNotNull { (array.opt(it) as? String)?.trim()?.lowercase() }
                    .filter { it in SHORTCUTS }.distinct()
            },
        ).takeUnless { it.isEmpty }
    }

    private fun choice(o: JSONObject, key: String, options: List<String>): String? = (o.opt(key) as? String)?.trim()?.lowercase()?.takeIf { it in options }

    private fun number(o: JSONObject, key: String, range: IntRange): Int? = when (val v = o.opt(key)) {
        is Number -> v.toInt()
        is String -> v.trim().toIntOrNull()
        else -> null
    }?.coerceIn(range)

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
            look = lookFromJson(o.opt("look")),
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
