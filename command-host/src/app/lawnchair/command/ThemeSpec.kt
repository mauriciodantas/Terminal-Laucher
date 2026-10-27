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
 * An image a theme carries as wallpaper: a link the phone downloads when the theme is applied, the
 * image itself in base64 inside the file, or [Art] the phone draws. [Stored] is the base64 image of
 * a saved theme, kept in a file of the app so the saved themes stay small; it is never read from a
 * theme file.
 */
sealed interface ThemeImage {
    /**
     * A wallpaper the phone draws: [symbols] (emoji or characters) scattered over [background], or
     * the theme's background when null. Characters that are not emoji take [color], or the accent.
     * [density] and [size] go from 0 to 1. The AI writes these, since it cannot make an image.
     */
    data class Art(
        val symbols: List<String>,
        val background: Int? = null,
        val color: Int? = null,
        val density: Float = 0.5f,
        val size: Float = 0.5f,
    ) : ThemeImage

    data class Url(val url: String) : ThemeImage

    /** The image in base64, already checked: it decodes, is a known image format and fits [ThemeSpecs.MAX_IMAGE_BYTES]. */
    data class Data(val base64: String) : ThemeImage {
        fun bytes(): ByteArray = java.util.Base64.getDecoder().decode(base64)

        /** The size of the image, for the import screen. */
        val size: Int get() = base64.length / 4 * 3 - base64.takeLast(2).count { it == '=' }

        override fun toString() = "Data(${size}B)"
    }

    data class Stored(val file: String) : ThemeImage

    /**
     * A vector drawing: the AI writes its wallpaper this way, in the same reply as the theme, and
     * the phone turns it into a PNG embedded in base64 before the theme is shown.
     */
    data class Svg(val markup: String) : ThemeImage {
        override fun toString() = "Svg(${markup.length} chars)"
    }
}

/** The wallpaper of a theme; [lockScreen] puts the same image on the lock screen too. */
data class ThemeWallpaper(val image: ThemeImage, val lockScreen: Boolean = false)

/**
 * Everything under Appearance that makes up how the launcher looks. A null field is left as it is
 * when the theme is applied, so a theme may change only a few things ("tema fundo #000000", a theme
 * the AI wrote that only picks colors). The lock screen only changes when the theme brings an image
 * for it ([ThemeWallpaper.lockScreen] or [lockWallpaper]), and the import screen says so first.
 */
data class ThemeSpec(
    val name: String = "",
    val accent: ThemeColor? = null,
    val background: ThemeColor? = null,
    /** Secondary text, hints and lines; [ThemeColor.Default] keeps the dimmed accent. */
    val secondary: ThemeColor? = null,
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
    /** An image as the wallpaper of the home screen, and of the lock screen when it says so. */
    val wallpaper: ThemeWallpaper? = null,
    /** An image of its own for the lock screen; it wins over [ThemeWallpaper.lockScreen]. */
    val lockWallpaper: ThemeImage? = null,
    /** The shape app icons are cut to, one of [ThemeSpecs.ICON_SHAPES] ("circle", "squircle", "hexagon"…). */
    val iconShape: String? = null,
    /** The shape of the folders, from the same list. */
    val folderShape: String? = null,
) {
    /** The image each screen gets: the home screen's and the lock screen's. */
    val lockImage: ThemeImage? get() = lockWallpaper ?: wallpaper?.takeIf { it.lockScreen }?.image

    /** True when the theme changes nothing at all. */
    val isEmpty: Boolean get() = copy(name = "") == ThemeSpec()

    /** [other]'s fields where it has them, this theme's elsewhere. */
    fun overlaidWith(other: ThemeSpec): ThemeSpec = ThemeSpec(
        name = other.name.ifEmpty { name },
        accent = other.accent ?: accent,
        background = other.background ?: background,
        secondary = other.secondary ?: secondary,
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
        wallpaper = other.wallpaper ?: wallpaper,
        lockWallpaper = other.lockWallpaper ?: lockWallpaper,
        iconShape = other.iconShape ?: iconShape,
        folderShape = other.folderShape ?: folderShape,
    )
}

/** The theme file (.tthm) and the rules every theme goes through, wherever it came from. Pure, so it can be unit tested. */
object ThemeSpecs {

    const val MIME = "application/x-terminal-theme"
    const val EXTENSION = "tthm"
    const val FORMAT = "terminal-theme"
    const val VERSION = 1
    const val MAX_NAME = 32

    /**
     * The biggest image a theme may carry in base64, decoded. A phone wallpaper in JPEG or WebP fits
     * well under it; more would only cost memory, since the whole file is read and parsed at once.
     */
    const val MAX_IMAGE_BYTES = 3 * 1024 * 1024

    /** The base64 text of the biggest image. */
    const val MAX_IMAGE_BASE64 = (MAX_IMAGE_BYTES + 2) / 3 * 4

    /** The settings take little; the rest is room for two images in base64 (home and lock screen). */
    const val MAX_BYTES = 64 * 1024 + 2 * MAX_IMAGE_BASE64

    const val MAX_URL = 2048

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
        spec.secondary?.let { o.put("secondary", it.encode()) }
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
        spec.wallpaper?.let { w -> o.put("wallpaper", image(w.image).apply { if (w.lockScreen) put("lockScreen", true) }) }
        spec.lockWallpaper?.let { o.put("lockWallpaper", image(it)) }
        spec.iconShape?.let { o.put("iconShape", it) }
        spec.folderShape?.let { o.put("folderShape", it) }
        return if (pretty) o.toString(2) else o.toString()
    }

    private fun image(image: ThemeImage): JSONObject = when (image) {
        is ThemeImage.Url -> JSONObject().put("url", image.url)

        is ThemeImage.Data -> JSONObject().put("base64", image.base64)

        is ThemeImage.Stored -> JSONObject().put("file", image.file)

        is ThemeImage.Svg -> JSONObject().put("svg", image.markup)

        is ThemeImage.Art -> JSONObject().put(
            "art",
            JSONObject().put("symbols", org.json.JSONArray(image.symbols)).apply {
                image.background?.let { put("background", hex(it)) }
                image.color?.let { put("color", hex(it)) }
                put("density", image.density.toDouble())
                put("size", image.size.toDouble())
            },
        )
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
     * Unknown or broken values are left out instead of failing the whole theme. [stored] lets an image
     * point at a file of the app, which only the saved themes may do.
     */
    fun fromJson(o: JSONObject, stored: Boolean = false): ThemeSpec = ThemeSpec(
        name = cleanName(o.optString("name")),
        accent = color(o, "accent"),
        background = color(o, "background"),
        secondary = color(o, "secondary"),
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
        wallpaper = o.opt("wallpaper")?.let { w ->
            image(w, stored)?.let { ThemeWallpaper(it, lockScreen = (w as? JSONObject)?.let { bool(it, "lockScreen") } == true) }
        },
        lockWallpaper = o.opt("lockWallpaper")?.let { image(it, stored) },
        iconShape = shape(o, "iconShape"),
        folderShape = shape(o, "folderShape"),
    )

    /** The shapes the launcher draws icons and folders in, by the key it stores ("system" follows the phone's own mask). */
    val ICON_SHAPES = listOf(
        "system", "circle", "square", "sharpSquare", "roundedSquare", "squircle", "sammy", "teardrop", "cylinder",
        "cupertino", "octagon", "hexagon", "diamond", "egg", "verysunny", "complexclover", "foursidedcookie",
        "sevensidedcookie", "arch", "cloudy", "flower", "heart", "leaf", "meow", "pebble", "roundedhexagon",
        "stretched", "vessel",
    )

    /** The key a shape [text] names, in any case and with or without spaces, dashes or underscores; null for an unknown one. */
    fun shapeKey(text: String): String? {
        val t = text.trim().lowercase().filter { it.isLetterOrDigit() }
        return ICON_SHAPES.firstOrNull { it.lowercase() == t }
    }

    private fun shape(o: JSONObject, key: String): String? = if (o.isNull(key)) null else shapeKey(o.optString(key))

    /**
     * The image of `{"url": …}`, `{"base64": …}` or of a bare string, a link or a data URI
     * ("data:image/png;base64,…"); null when it is neither or breaks the limits.
     */
    private fun image(value: Any, stored: Boolean): ThemeImage? {
        val o = value as? JSONObject
        val text = when {
            o == null -> value as? String ?: return null
            o.has("url") -> return urlImage(o.optString("url"))
            o.has("base64") -> return dataImage(o.optString("base64"))
            o.has("art") -> return art(o.optJSONObject("art"))
            o.has("svg") -> return svgImage(o.optString("svg"))
            o.has("file") && stored -> return o.optString("file").takeIf { STORED_FILE.matches(it) }?.let { ThemeImage.Stored(it) }
            else -> return null
        }
        val start = text.trimStart()
        return when {
            start.startsWith("data:", ignoreCase = true) -> dataImage(text)
            start.startsWith("<") -> svgImage(text)
            else -> urlImage(text)
        }
    }

    /** The most symbols an [ThemeImage.Art] scatters, and the longest one (an emoji with modifiers takes several chars). */
    const val MAX_ART_SYMBOLS = 12
    private const val MAX_SYMBOL_LENGTH = 16

    /** The art of `{"symbols": [...], …}`; null without a symbol that can be drawn. */
    fun art(o: JSONObject?): ThemeImage.Art? {
        o ?: return null
        val raw = o.optJSONArray("symbols")?.let { a -> (0 until a.length()).map { a.optString(it) } }
            ?: o.optString("symbols").takeIf { it.isNotBlank() }?.split(' ', ',')
            ?: return null
        val symbols = raw.map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= MAX_SYMBOL_LENGTH && it.none { c -> c.isISOControl() } }
            .distinct()
            .take(MAX_ART_SYMBOLS)
        if (symbols.isEmpty()) return null
        return ThemeImage.Art(
            symbols = symbols,
            background = o.optString("background").let(::hexColor),
            color = o.optString("color").let(::hexColor),
            density = fraction(o, "density") ?: 0.5f,
            size = fraction(o, "size") ?: 0.5f,
        )
    }

    /** The longest SVG a theme may carry; a wallpaper the AI draws stays well under it. */
    const val MAX_SVG = 100 * 1024

    /** An SVG document: an `<svg` element, within [MAX_SVG], without code fences around it. */
    fun svgImage(text: String): ThemeImage.Svg? {
        val markup = text.trim().removePrefix("```svg").removePrefix("```xml").removePrefix("```").removeSuffix("```").trim()
        if (markup.length > MAX_SVG || !markup.contains("<svg", ignoreCase = true) || !markup.contains("</svg>", ignoreCase = true)) return null
        return ThemeImage.Svg(markup)
    }

    /** A link the phone may download: http or https, with a host, one line and not too long. */
    fun urlImage(text: String): ThemeImage.Url? {
        val url = text.trim()
        if (url.length > MAX_URL || url.any { it.isWhitespace() || it.isISOControl() }) return null
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrEmpty()) return null
        return ThemeImage.Url(url)
    }

    /**
     * The image of a base64 text (a data URI prefix and line breaks are allowed); null when it is
     * bigger than [MAX_IMAGE_BYTES], does not decode or is not a PNG, JPEG, WebP or GIF. The length
     * is checked before decoding, so an oversized text is never decoded.
     */
    fun dataImage(text: String): ThemeImage.Data? {
        val body = text.trim().let { if (it.startsWith("data:", ignoreCase = true)) it.substringAfter(',', "") else it }
        // Room for a line break every 60 characters, as base64 tools write it.
        if (body.length > MAX_IMAGE_BASE64 + MAX_IMAGE_BASE64 / 60 + 4) return null
        val base64 = body.filterNot { it.isWhitespace() }
        if (base64.isEmpty() || base64.length > MAX_IMAGE_BASE64) return null
        val bytes = runCatching { java.util.Base64.getDecoder().decode(base64) }.getOrNull() ?: return null
        if (bytes.size > MAX_IMAGE_BYTES || imageType(bytes) == null) return null
        return ThemeImage.Data(base64)
    }

    /** "png", "jpeg", "webp" or "gif" from the first bytes of an image; null for anything else. */
    fun imageType(bytes: ByteArray): String? {
        fun at(i: Int, vararg b: Int) = bytes.size >= i + b.size && b.indices.all { bytes[i + it] == b[it].toByte() }
        return when {
            at(0, 0x89, 0x50, 0x4E, 0x47) -> "png"
            at(0, 0xFF, 0xD8, 0xFF) -> "jpeg"
            at(0, 0x52, 0x49, 0x46, 0x46) && at(8, 0x57, 0x45, 0x42, 0x50) -> "webp"
            at(0, 0x47, 0x49, 0x46, 0x38) -> "gif"
            else -> null
        }
    }

    /** The name of an image a saved theme keeps in the app: its hash and nothing that walks out of the folder. */
    private val STORED_FILE = Regex("[0-9a-f]{16,64}")

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
        (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { fromJson(it, stored = true) }?.takeIf { it.name.isNotEmpty() } }
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

    /** The contrast between the secondary color and the background, as [contrastOf]; null when it keeps the dimmed accent. */
    fun secondaryContrastOf(spec: ThemeSpec, current: ThemeSpec?): Double? {
        val secondary = (spec.secondary ?: current?.secondary) as? ThemeColor.Custom ?: return null
        val ground = fixed(spec.background ?: current?.background, DEFAULT_BACKGROUND) ?: return null
        return contrast(secondary.argb, ground)
    }

    /** True when the theme, over [current], would leave text that cannot be read: the main text or the secondary one. */
    fun unreadable(spec: ThemeSpec, current: ThemeSpec?): Boolean = listOfNotNull(contrastOf(spec, current), secondaryContrastOf(spec, current)).any { it < MIN_VISIBILITY }

    /** True when it reads, but not comfortably. */
    fun weak(spec: ThemeSpec, current: ThemeSpec?): Boolean = !unreadable(spec, current) &&
        listOfNotNull(contrastOf(spec, current), secondaryContrastOf(spec, current)).any { it < COMFORTABLE_VISIBILITY }

    private const val OPAQUE = 0xFF000000.toInt()
}
