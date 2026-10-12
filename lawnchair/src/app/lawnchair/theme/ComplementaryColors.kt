package app.lawnchair.theme

import android.graphics.Color
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import com.android.launcher3.R

/**
 * The twelve-hue color wheel, in which every hue has its complement directly opposite (six steps
 * away). Picking a hue as the accent gives the background for free: the opposite hue, darkened so
 * the terminal look stays dark.
 */
object ComplementaryColors {

    /** One pair of the wheel: [accent] drawn over [background], both opaque; a triad also brings a [secondary]. */
    data class Pair(@get:StringRes val name: Int, val accent: Int, val background: Int, val secondary: Int? = null)

    private class Hue(@StringRes val name: Int, val color: Int)

    private val wheel = listOf(
        Hue(R.string.complementary_red, 0xFFC8003A.toInt()),
        Hue(R.string.complementary_vermilion, 0xFFFF5A32.toInt()),
        Hue(R.string.complementary_orange, 0xFFFF8C1A.toInt()),
        Hue(R.string.complementary_amber, 0xFFFFC300.toInt()),
        Hue(R.string.complementary_yellow, 0xFFEDDC50.toInt()),
        Hue(R.string.complementary_chartreuse, 0xFFAED45A.toInt()),
        Hue(R.string.complementary_green, 0xFF55C885.toInt()),
        Hue(R.string.complementary_teal, 0xFF00B9AD.toInt()),
        Hue(R.string.complementary_blue, 0xFF2A7A9B.toInt()),
        Hue(R.string.complementary_violet, 0xFF3D3D6B.toInt()),
        Hue(R.string.complementary_purple, 0xFF531745.toInt()),
        Hue(R.string.complementary_magenta, 0xFF8E0A3A.toInt()),
    )

    /**
     * The background is the opposite hue, at most this saturated and between these lightnesses, so it
     * stays dark enough for the accent to read over it. The user's intensity (0 to 1) slides between them.
     */
    private const val BACKGROUND_MIN_LIGHTNESS = 0.04f
    private const val BACKGROUND_MAX_LIGHTNESS = 0.30f
    private const val BACKGROUND_MAX_SATURATION = 0.55f

    /** The intensity of the ready-made pairs: a lightness of 10%. */
    const val DEFAULT_INTENSITY = (0.10f - BACKGROUND_MIN_LIGHTNESS) / (BACKGROUND_MAX_LIGHTNESS - BACKGROUND_MIN_LIGHTNESS)

    private fun lightnessOf(intensity: Float) = BACKGROUND_MIN_LIGHTNESS + intensity.coerceIn(0f, 1f) * (BACKGROUND_MAX_LIGHTNESS - BACKGROUND_MIN_LIGHTNESS)

    /** The intensity (0 to 1) of a background [color], for a slider to start from. */
    fun intensityOf(color: Int): Float = ((hsl(color)[2] - BACKGROUND_MIN_LIGHTNESS) / (BACKGROUND_MAX_LIGHTNESS - BACKGROUND_MIN_LIGHTNESS)).coerceIn(0f, 1f)

    /**
     * Ready-made looks outside the wheel, several of them on a light ground with dark text. They are
     * taken as they are: each pair was chosen to read well (at least 4.5 of contrast).
     */
    val themes: List<Pair> = listOf(
        Pair(R.string.theme_highlighter, accent = 0xFF000000.toInt(), background = 0xFFFFE14D.toInt()),
        Pair(R.string.theme_game_boy, accent = 0xFF0F380F.toInt(), background = 0xFF9BBC0F.toInt()),
        Pair(R.string.theme_cyberpunk, accent = 0xFF101820.toInt(), background = 0xFFFCEE0A.toInt()),
        Pair(R.string.theme_cyberpunk_night, accent = 0xFF55EAF7.toInt(), background = 0xFF0D0B1E.toInt()),
        Pair(R.string.theme_blade_runner, accent = 0xFFFF9A3C.toInt(), background = 0xFF07202A.toInt()),
        Pair(R.string.theme_blade_runner_2049, accent = 0xFF2A1205.toInt(), background = 0xFFF0954A.toInt()),
        Pair(R.string.theme_akira, accent = 0xFFF6F1E7.toInt(), background = 0xFFC8102E.toInt()),
    )

    /**
     * Soft looks: a pastel ground with a deep shade of the same family as the text, all well above
     * 4.5 of contrast. In the dark mode the pair swaps and the pastel becomes the text.
     */
    val pastels: List<Pair> = listOf(
        Pair(R.string.theme_pastel_rose, accent = 0xFF6B1F45.toInt(), background = 0xFFF8C8DC.toInt()),
        Pair(R.string.theme_pastel_bubblegum, accent = 0xFF4A0E2E.toInt(), background = 0xFFFF9FCB.toInt()),
        Pair(R.string.theme_pastel_sakura, accent = 0xFF8A2D4F.toInt(), background = 0xFFFBE0E6.toInt()),
        Pair(R.string.theme_pastel_cotton_candy, accent = 0xFF2B3A78.toInt(), background = 0xFFFFC8E8.toInt()),
        Pair(R.string.theme_pastel_lavender, accent = 0xFF3B2266.toInt(), background = 0xFFD9C8F0.toInt()),
        Pair(R.string.theme_pastel_lilac, accent = 0xFF5B2A73.toInt(), background = 0xFFE6D3F2.toInt()),
        Pair(R.string.theme_pastel_peach, accent = 0xFF6B2E14.toInt(), background = 0xFFFFD3B8.toInt()),
        Pair(R.string.theme_pastel_mint, accent = 0xFF1E4D3A.toInt(), background = 0xFFC4EBD9.toInt()),
    )

    /**
     * Ready-made looks in three colors: the accent, the background and a secondary color for hints,
     * labels and lines. Both read over the background with at least 4.5 of contrast.
     */
    val trios: List<Pair> = listOf(
        Pair(R.string.theme_trio_synthwave, accent = 0xFFFF2BD6.toInt(), background = 0xFF140326.toInt(), secondary = 0xFF2DE2E6.toInt()),
        Pair(R.string.theme_trio_outrun, accent = 0xFFFFD319.toInt(), background = 0xFF120A24.toInt(), secondary = 0xFFFF4D8D.toInt()),
        Pair(R.string.theme_trio_tron, accent = 0xFF6FC3DF.toInt(), background = 0xFF04090F.toInt(), secondary = 0xFFFFE64D.toInt()),
        Pair(R.string.theme_trio_matrix, accent = 0xFF39FF14.toInt(), background = 0xFF020A02.toInt(), secondary = 0xFF00C2B2.toInt()),
        Pair(R.string.theme_trio_deep_sea, accent = 0xFF4DEEEA.toInt(), background = 0xFF03132B.toInt(), secondary = 0xFFF0A35E.toInt()),
        Pair(R.string.theme_trio_hanami, accent = 0xFFFFB7C5.toInt(), background = 0xFF1A1020.toInt(), secondary = 0xFF9ED8B4.toInt()),
        Pair(R.string.theme_trio_dracula, accent = 0xFFBD93F9.toInt(), background = 0xFF282A36.toInt(), secondary = 0xFF50FA7B.toInt()),
        Pair(R.string.theme_trio_nord, accent = 0xFF88C0D0.toInt(), background = 0xFF2E3440.toInt(), secondary = 0xFFEBCB8B.toInt()),
        Pair(R.string.theme_trio_gruvbox, accent = 0xFFFABD2F.toInt(), background = 0xFF282828.toInt(), secondary = 0xFF8EC07C.toInt()),
        Pair(R.string.theme_trio_monokai, accent = 0xFFA6E22E.toInt(), background = 0xFF272822.toInt(), secondary = 0xFFFD971F.toInt()),
        Pair(R.string.theme_trio_solarized, accent = 0xFF2AA198.toInt(), background = 0xFF002B36.toInt(), secondary = 0xFFB58900.toInt()),
    )

    /** Every ready-made look, in the order they are offered: the three-color ones, the others, the pastels and the wheel. */
    val all: List<Pair> get() = trios + themes + pastels + pairs

    /** Every hue of the wheel as the accent, with its complement as the background. */
    val pairs: List<Pair> = wheel.indices.map { pairOf(it) }

    private const val ACCENT_SATURATION = 0.85f
    private const val ACCENT_LIGHTNESS = 0.55f

    /**
     * How the secondary color sits on the wheel from the accent: no secondary at all, a fixed angle
     * away (analogous, triad, split complementary, complementary), or anywhere the user drags it.
     */
    enum class Harmony(val angle: Float?) {
        MONO(null),
        ANALOGOUS(45f),
        TRIAD(120f),
        SPLIT(150f),
        COMPLEMENTARY(180f),
        FREE(null),
    }

    /**
     * Where the background takes its tint from: a near-gray barely leaning to the accent, the
     * accent's own hue, or the opposite one (the classic complementary pair). It is always darkened
     * by the intensity, so it stays a ground for text rather than a third vivid color.
     */
    enum class Tint { NEUTRAL, ACCENT, OPPOSITE }

    private const val NEUTRAL_SATURATION = 0.12f

    /** The background tinted from [accentHue] by [tint], at [intensity]. */
    fun backgroundOf(accentHue: Float, tint: Tint, intensity: Float): Int {
        val h = norm(accentHue)
        val hue = if (tint == Tint.OPPOSITE) (h + 180f) % 360f else h
        val saturation = if (tint == Tint.NEUTRAL) NEUTRAL_SATURATION else BACKGROUND_MAX_SATURATION
        return ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightnessOf(intensity)))
    }

    /**
     * The colors picked on the wheel: [accentHue] as the accent, [secondaryHue] (or none) as the
     * secondary color, and the background tinted by [tint] at [intensity]. The accent and the
     * secondary color are lightened until they read over the background.
     */
    fun compose(accentHue: Float, secondaryHue: Float?, tint: Tint, intensity: Float): Pair {
        val background = backgroundOf(accentHue, tint, intensity)
        val accent = readable(ColorUtils.HSLToColor(floatArrayOf(norm(accentHue), ACCENT_SATURATION, ACCENT_LIGHTNESS)), background)
        val secondary = secondaryHue?.let { readable(ColorUtils.HSLToColor(floatArrayOf(norm(it), ACCENT_SATURATION, ACCENT_LIGHTNESS)), background) }
        return Pair(R.string.complementary_custom, accent, background, secondary)
    }

    /** The harmony that the angle from [accentHue] to [secondaryHue] stands for, within a few degrees; [Harmony.FREE] otherwise. */
    fun harmonyOf(accentHue: Float, secondaryHue: Float?): Harmony {
        if (secondaryHue == null) return Harmony.MONO
        val d = norm(secondaryHue - accentHue)
        return Harmony.entries.firstOrNull { h -> h.angle?.let { kotlin.math.abs(d - it) < 8f } == true } ?: Harmony.FREE
    }

    /** Where the tint of [background] comes from, for [accent]: a gray one, the accent's hue or another. */
    fun tintOf(accent: Int, background: Int): Tint {
        val bg = hsl(background)
        if (bg[1] < 0.25f) return Tint.NEUTRAL
        val d = kotlin.math.abs(norm(bg[0] - hueOf(accent)))
        return if (minOf(d, 360f - d) < 45f) Tint.ACCENT else Tint.OPPOSITE
    }

    private fun norm(hue: Float) = ((hue % 360f) + 360f) % 360f

    /** The hue (degrees) of [color]. */
    fun hueOf(color: Int): Float = hsl(color)[0]

    private fun pairOf(index: Int): Pair {
        val hue = wheel[index]
        val opposite = wheel[(index + wheel.size / 2) % wheel.size]
        val background = tinted(opposite.color, lightnessOf(DEFAULT_INTENSITY))
        return Pair(hue.name, readable(hue.color, background), background)
    }

    private fun hsl(color: Int) = FloatArray(3).also { ColorUtils.colorToHSL(color, it) }

    private fun tinted(color: Int, lightness: Float): Int {
        val hsl = hsl(color)
        hsl[1] = hsl[1].coerceAtMost(BACKGROUND_MAX_SATURATION)
        hsl[2] = lightness
        return ColorUtils.HSLToColor(hsl)
    }

    /** [color], lightened in steps until it reads over [ground] as well as text must. */
    private fun readable(color: Int, ground: Int): Int {
        val hsl = hsl(color)
        while (hsl[2] < 0.9f && !LauncherGround.isVisible(ColorUtils.HSLToColor(hsl), ground, MIN_CONTRAST)) {
            hsl[2] += 0.02f
        }
        return ColorUtils.HSLToColor(hsl) or Color.BLACK
    }

    private const val MIN_CONTRAST = 4.5
}
