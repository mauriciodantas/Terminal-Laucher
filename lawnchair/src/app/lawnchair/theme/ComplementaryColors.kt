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

    /** One pair of the wheel: [accent] drawn over [background], both opaque. */
    data class Pair(@get:StringRes val name: Int, val accent: Int, val background: Int)

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

    /** The background is the opposite hue at this lightness and at most this saturation, so it stays near-black. */
    private const val BACKGROUND_LIGHTNESS = 0.07f
    private const val BACKGROUND_MAX_SATURATION = 0.45f

    /** Every hue of the wheel as the accent, with its complement as the background. */
    val pairs: List<Pair> = wheel.indices.map { pairOf(it) }

    private const val ACCENT_SATURATION = 0.85f
    private const val ACCENT_LIGHTNESS = 0.55f

    /** The pair for any [hue] (degrees) picked on the wheel: that hue as the accent, the opposite one as the background. */
    fun pairForHue(hue: Float): Pair {
        val h = ((hue % 360f) + 360f) % 360f
        val background = darkened(ColorUtils.HSLToColor(floatArrayOf((h + 180f) % 360f, ACCENT_SATURATION, ACCENT_LIGHTNESS)))
        val accent = readable(ColorUtils.HSLToColor(floatArrayOf(h, ACCENT_SATURATION, ACCENT_LIGHTNESS)), background)
        return Pair(R.string.complementary_custom, accent, background)
    }

    /** The hue (degrees) of [color]. */
    fun hueOf(color: Int): Float = hsl(color)[0]

    private fun pairOf(index: Int): Pair {
        val hue = wheel[index]
        val opposite = wheel[(index + wheel.size / 2) % wheel.size]
        val background = darkened(opposite.color)
        return Pair(hue.name, readable(hue.color, background), background)
    }

    private fun hsl(color: Int) = FloatArray(3).also { ColorUtils.colorToHSL(color, it) }

    private fun darkened(color: Int): Int {
        val hsl = hsl(color)
        hsl[1] = hsl[1].coerceAtMost(BACKGROUND_MAX_SATURATION)
        hsl[2] = BACKGROUND_LIGHTNESS
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
