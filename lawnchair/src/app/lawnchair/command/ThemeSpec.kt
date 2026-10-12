package app.lawnchair.command

import app.lawnchair.util.foldAccents
import org.json.JSONObject

/** A color of a theme: managed by the app (follows the accent), the system's, the wallpaper's or a fixed one. */
sealed interface ThemeColor {
    data object Default : ThemeColor
    data object System : ThemeColor
    data object Wallpaper : ThemeColor
    data class Custom(val argb: Int) : ThemeColor

    /** "default", "system", "wallpaper" or "#rrggbb", as in the theme file. */
    fun encode(): String = when (this) {
        Default -> "default"
        System -> "system"
        Wallpaper -> "wallpaper"
        is Custom -> ThemeSpecs.hex(argb)
    }
}

/**
 * The interface font of a theme: the terminal default, a Google Fonts family or one of the
 * system's. Fonts imported from a .ttf file stay on the phone that has them and are not carried.
 */
sealed interface ThemeFont {
    data object Default : ThemeFont
    data class Google(val family: String) : ThemeFont
    data class System(val family: String) : ThemeFont

    val label: String
        get() = when (this) {
            Default -> "IBM Plex Mono"
            is Google -> family
            is System -> family
        }
}

/**
 * Everything under Appearance that makes up how the launcher looks. A null field is left as it is
 * when the theme is applied, so a theme may change only a few things ("tema fundo #000000", a theme
 * the AI wrote that only picks colors). The lock screen is never part of it: changing it replaces
 * the user's own image for good, and only the settings screen does that, after asking.
 */
data class ThemeSpec(
    val name: String = "",
    val accent: ThemeColor? = null,
    val background: ThemeColor? = null,
    /** The outline of the search bar. */
    val outline: ThemeColor? = null,
    val dock: ThemeColor? = null,
    val folder: ThemeColor? = null,
    val followSystem: Boolean? = null,
    val font: ThemeFont? = null,
    val pixelClock: Boolean? = null,
    val iconEffect: Boolean? = null,
    val widgetEffect: Boolean? = null,
    val widgetIntensity: Float? = null,
    val crtEffect: Boolean? = null,
    val crtIntensity: Float? = null,
    val showWallpaper: Boolean? = null,
    val wallpaperDim: Float? = null,
    /** The background color also set as the system wallpaper of the home screen. */
    val colorAsWallpaper: Boolean? = null,
) {
    /** True when the theme changes nothing at all. */
    val isEmpty: Boolean get() = copy(name = "") == ThemeSpec()

    /** [other]'s fields where it has them, this theme's elsewhere. */
    fun overlaidWith(other: ThemeSpec): ThemeSpec = ThemeSpec(
        name = other.name.ifEmpty { name },
        accent = other.accent ?: accent,
        background = other.background ?: background,
        outline = other.outline ?: outline,
        dock = other.dock ?: dock,
        folder = other.folder ?: folder,
        followSystem = other.followSystem ?: followSystem,
        font = other.font ?: font,
        pixelClock = other.pixelClock ?: pixelClock,
        iconEffect = other.iconEffect ?: iconEffect,
        widgetEffect = other.widgetEffect ?: widgetEffect,
        widgetIntensity = other.widgetIntensity ?: widgetIntensity,
        crtEffect = other.crtEffect ?: crtEffect,
        crtIntensity = other.crtIntensity ?: crtIntensity,
        showWallpaper = other.showWallpaper ?: showWallpaper,
        wallpaperDim = other.wallpaperDim ?: wallpaperDim,
        colorAsWallpaper = other.colorAsWallpaper ?: colorAsWallpaper,
    )
}

/** The theme file (.tthm) and the rules every theme goes through, wherever it came from. Pure, so it can be unit tested. */
object ThemeSpecs {

    const val MIME = "application/x-terminal-theme"
    const val EXTENSION = "tthm"
    const val FORMAT = "terminal-theme"
    const val VERSION = 1
    const val MAX_NAME = 32
    const val MAX_BYTES = 64 * 1024

    /** The terminal near-black, the background of a theme that keeps the default. */
    const val DEFAULT_BACKGROUND = 0xFF07090A.toInt()

    /** Below this contrast between the accent and the background nothing can be read (see LauncherGround). */
    const val MIN_VISIBILITY = 1.5

    /** From this contrast text reads comfortably; a theme under it is applied with a warning. */
    const val COMFORTABLE_VISIBILITY = 3.0

    /** Intensities of the effects go from barely there to full, as their sliders do. */
    private const val MIN_INTENSITY = 0.05f

    fun encode(spec: ThemeSpec, pretty: Boolean = true): String {
        val o = JSONObject().put("format", FORMAT).put("version", VERSION)
        if (spec.name.isNotEmpty()) o.put("name", spec.name)
        spec.accent?.let { o.put("accent", it.encode()) }
        spec.background?.let { o.put("background", it.encode()) }
        spec.outline?.let { o.put("outline", it.encode()) }
        spec.dock?.let { o.put("dock", it.encode()) }
        spec.folder?.let { o.put("folder", it.encode()) }
        spec.followSystem?.let { o.put("followSystem", it) }
        spec.font?.let { font ->
            o.put(
                "font",
                when (font) {
                    ThemeFont.Default -> JSONObject().put("kind", "default")
                    is ThemeFont.Google -> JSONObject().put("kind", "google").put("family", font.family)
                    is ThemeFont.System -> JSONObject().put("kind", "system").put("family", font.family)
                },
            )
        }
        spec.pixelClock?.let { o.put("pixelClock", it) }
        spec.iconEffect?.let { o.put("iconEffect", it) }
        spec.widgetEffect?.let { o.put("widgetEffect", it) }
        spec.widgetIntensity?.let { o.put("widgetIntensity", it.toDouble()) }
        spec.crtEffect?.let { o.put("crtEffect", it) }
        spec.crtIntensity?.let { o.put("crtIntensity", it.toDouble()) }
        spec.showWallpaper?.let { o.put("showWallpaper", it) }
        spec.wallpaperDim?.let { o.put("wallpaperDim", it.toDouble()) }
        spec.colorAsWallpaper?.let { o.put("colorAsWallpaper", it) }
        return if (pretty) o.toString(2) else o.toString()
    }

    /** Reads a theme file. Throws when [raw] is not one, so a stray JSON file is never taken for a theme. */
    fun decode(raw: String): ThemeSpec {
        val o = JSONObject(raw.trim())
        require(o.optString("format") == FORMAT) { "not a theme" }
        require(o.optInt("version", VERSION) <= VERSION) { "newer theme version" }
        return fromJson(o)
    }

    /**
     * The fields of [o] as a theme, without asking for the format: the AI's reply is read this way.
     * Unknown or broken values are left out instead of failing the whole theme.
     */
    fun fromJson(o: JSONObject): ThemeSpec = ThemeSpec(
        name = cleanName(o.optString("name")),
        accent = color(o, "accent"),
        background = color(o, "background"),
        outline = color(o, "outline"),
        dock = color(o, "dock"),
        folder = color(o, "folder"),
        followSystem = bool(o, "followSystem"),
        font = font(o.opt("font")),
        pixelClock = bool(o, "pixelClock"),
        iconEffect = bool(o, "iconEffect"),
        widgetEffect = bool(o, "widgetEffect"),
        widgetIntensity = fraction(o, "widgetIntensity")?.coerceAtLeast(MIN_INTENSITY),
        crtEffect = bool(o, "crtEffect"),
        crtIntensity = fraction(o, "crtIntensity")?.coerceAtLeast(MIN_INTENSITY),
        showWallpaper = bool(o, "showWallpaper"),
        wallpaperDim = fraction(o, "wallpaperDim"),
        colorAsWallpaper = bool(o, "colorAsWallpaper"),
    )

    /** A name fit for a file and a command: one line, no control characters, not too long. */
    fun cleanName(name: String): String = name.lineSequence().firstOrNull().orEmpty()
        .filter { !it.isISOControl() }
        .trim()
        .take(MAX_NAME)
        .trim()

    /** "theme-name" from "Theme name", for the file shared. */
    fun fileName(spec: ThemeSpec): String = spec.name.ifBlank { "tema" }.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").trim('_').ifEmpty { "tema" } + ".$EXTENSION"

    private fun color(o: JSONObject, key: String): ThemeColor? = if (o.isNull(key)) null else parseColor(o.optString(key))

    /** "#rgb", "#rrggbb", "#aarrggbb" (made opaque), "default", "system" or "wallpaper"; null for anything else. */
    fun parseColor(text: String): ThemeColor? {
        val t = text.trim().lowercase()
        when (t) {
            "default", "padrao", "padrão" -> return ThemeColor.Default
            "system", "system_accent", "sistema" -> return ThemeColor.System
            "wallpaper", "wallpaper_primary" -> return ThemeColor.Wallpaper
        }
        return hexColor(t)?.let { ThemeColor.Custom(it) }
    }

    /** The opaque color of "#rgb", "#rrggbb" or "#aarrggbb" (the "#" may be left out); null otherwise. */
    fun hexColor(text: String): Int? {
        val h = text.trim().removePrefix("#")
        if (h.isEmpty() || h.any { it !in HEX_DIGITS }) return null
        val rgb = when (h.length) {
            3 -> h.map { "$it$it" }.joinToString("")
            6 -> h
            8 -> h.substring(2)
            else -> return null
        }
        return (rgb.toLong(16).toInt()) or OPAQUE
    }

    private const val HEX_DIGITS = "0123456789abcdefABCDEF"

    /** "#33ff66". */
    fun hex(argb: Int): String = "#" + String.format("%06x", argb and 0xFFFFFF)

    private fun bool(o: JSONObject, key: String): Boolean? = when (val v = o.opt(key)) {
        is Boolean -> v
        is String -> v.toBooleanStrictOrNull()
        else -> null
    }

    /** A number from 0 to 1; a percentage (over 1) is taken as one. */
    private fun fraction(o: JSONObject, key: String): Float? {
        val v = (o.opt(key) as? Number)?.toFloat() ?: return null
        if (v.isNaN()) return null
        return (if (v > 1f) v / 100f else v).coerceIn(0f, 1f)
    }

    private fun font(value: Any?): ThemeFont? {
        if (value is String) return if (value.isBlank()) null else fontNamed(value)
        val o = value as? JSONObject ?: return null
        val family = cleanFamily(o.optString("family"))
        return when (o.optString("kind")) {
            "default" -> ThemeFont.Default
            "google" -> family?.let { ThemeFont.Google(it) }
            "system" -> family?.let { ThemeFont.System(it) }
            else -> family?.let { fontNamed(it) }
        }
    }

    /** The font a bare name stands for: the default words, or a Google Fonts family. */
    fun fontNamed(name: String): ThemeFont? {
        val t = name.trim()
        if (t.lowercase() in setOf("default", "padrao", "padrão") || t.equals(ThemeFont.Default.label, ignoreCase = true)) return ThemeFont.Default
        return cleanFamily(t)?.let { ThemeFont.Google(it) }
    }

    /** Family names are letters, digits, spaces and a few marks; anything else is not a font. */
    private fun cleanFamily(family: String): String? = family.trim().takeIf { f ->
        f.isNotEmpty() && f.length <= 64 && f.all { it.isLetterOrDigit() || it in " -_." }
    }

    // ---- the saved themes ----

    const val MAX_SAVED = 50

    fun encodeList(themes: List<ThemeSpec>): String = org.json.JSONArray().also { array ->
        themes.forEach { array.put(JSONObject(encode(it, pretty = false))) }
    }.toString()

    fun decodeList(raw: String): List<ThemeSpec> = runCatching {
        val array = org.json.JSONArray(raw)
        (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { fromJson(it) }?.takeIf { it.name.isNotEmpty() } }
    }.getOrDefault(emptyList())

    /** [themes] with [theme] in place of the one of the same name, or first when it is new. */
    fun upsert(themes: List<ThemeSpec>, theme: ThemeSpec): List<ThemeSpec> {
        val key = theme.name.foldAccents()
        return (listOf(theme) + themes.filter { it.name.foldAccents() != key }).take(MAX_SAVED)
    }

    fun remove(themes: List<ThemeSpec>, name: String): List<ThemeSpec> = themes.filter { it.name.foldAccents() != name.foldAccents() }

    // ---- contrast ----

    /** The WCAG contrast between two opaque colors, from 1 to 21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    fun luminance(color: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel((color shr 16) and 0xFF) + 0.7152 * channel((color shr 8) and 0xFF) + 0.0722 * channel(color and 0xFF)
    }

    /**
     * The contrast between the accent and the background the theme leaves on screen, with
     * [current] filling what the theme does not set; null when one of them is not a fixed color
     * (the system's or the wallpaper's), which cannot be judged in advance.
     */
    fun contrastOf(spec: ThemeSpec, current: ThemeSpec?): Double? {
        val accent = fixed(spec.accent ?: current?.accent, null) ?: return null
        val ground = fixed(spec.background ?: current?.background, DEFAULT_BACKGROUND) ?: return null
        return contrast(accent, ground)
    }

    private fun fixed(color: ThemeColor?, default: Int?): Int? = when (color) {
        is ThemeColor.Custom -> color.argb
        ThemeColor.Default -> default
        else -> null
    }

    /** True when the theme, over [current], would leave text that cannot be read. */
    fun unreadable(spec: ThemeSpec, current: ThemeSpec?): Boolean = contrastOf(spec, current)?.let { it < MIN_VISIBILITY } == true

    /** True when it reads, but not comfortably. */
    fun weak(spec: ThemeSpec, current: ThemeSpec?): Boolean = contrastOf(spec, current)?.let { it >= MIN_VISIBILITY && it < COMFORTABLE_VISIBILITY } == true

    private const val OPAQUE = 0xFF000000.toInt()
}
