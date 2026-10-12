package app.lawnchair.theme

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.tokens.PhosphorColorToken

/**
 * The terminal "ground": the color everything else is drawn over. It is the near-black of the theme
 * until the user picks a background color; the home screen, the app drawer, the command bar and the
 * settings all take it from here, so a single choice reaches them all.
 */
object LauncherGround {

    const val DEFAULT = 0xFF07090A.toInt()

    /** The user's color made opaque, or null when [option] is the default. */
    fun resolve(option: ColorOption, context: Context): Int? = option.colorPreferenceEntry.lightColor(context)
        .takeIf { it != 0 }
        ?.let { it or OPAQUE }

    /** [option]'s color, or the user's phosphor accent when it is the default, so a choice left alone follows the accent. */
    fun orAccent(option: ColorOption, context: Context): Int = resolve(option, context) ?: PhosphorColorToken(1f).resolveColor(context)

    /**
     * The least contrast between the accent (the text, the lines) and the background. Below it the
     * interface cannot be read and the user could not come back to undo it, so such a pair is refused
     * when the user picks it and, should one exist anyway (a restored backup), the background falls
     * back to the default. Pairs between this and [COMFORTABLE_VISIBILITY] are allowed, with a warning.
     */
    const val MIN_VISIBILITY = 1.5

    /** The contrast from which text reads comfortably; under it (and over [MIN_VISIBILITY]) the picker only warns. */
    const val COMFORTABLE_VISIBILITY = 3.0

    /** True when text in [accent] reads over [ground] with at least [MIN_VISIBILITY]. */
    fun isVisible(accent: Int, ground: Int, minContrast: Double = MIN_VISIBILITY): Boolean = ColorUtils.calculateContrast(accent or OPAQUE, ground or OPAQUE) >= minContrast

    /** The background [option] stands for, before the system mode is applied: the default when it is unset or would hide [accent]. */
    private fun chosen(option: ColorOption, accent: Int, context: Context): Int = resolve(option, context)?.takeIf { isVisible(accent, it) } ?: DEFAULT

    /**
     * The accent and the background as the screen draws them, from the [accent] and [background] the
     * user picked: the same pair, traded when it should follow the system mode (see [SystemColorMode]).
     */
    fun drawn(accent: Int, background: ColorOption, context: Context, follows: Boolean = SystemColorMode.follows(context)): Pair<Int, Int> {
        val ground = chosen(background, accent, context)
        return if (follows && SystemColorMode.swaps(accent, ground, SystemColorMode.isNight())) (ground or OPAQUE) to (accent or OPAQUE) else accent to ground
    }

    /** The background [option] stands for, or null for the default; also null when it would hide the accent. */
    fun resolveBackground(option: ColorOption, context: Context): Int? {
        val accent = ThemeProvider.INSTANCE.get(context).chosenPhosphorColor
        val color = resolve(option, context)?.takeIf { isVisible(accent, it) }
        // Following the system mode, the accent can take the background's place.
        return if (SystemColorMode.swaps(context, accent, color ?: DEFAULT)) accent or OPAQUE else color
    }

    /** The user's color, or null when they kept the default or it would hide the accent. */
    fun custom(context: Context): Int? {
        val prefs2 = PreferenceManager2.getInstance(context)
        return resolveBackground(prefs2.launcherBackgroundColor.firstCached(prefs2), context)
    }

    fun get(context: Context): Int = custom(context) ?: DEFAULT

    /**
     * The user's secondary color as the screen draws it over [ground], or null when they keep the
     * dimmed accent. It is also null when it would not read over [ground]: following the system mode
     * can trade the background for the accent, and the dimmed accent then takes its place.
     */
    fun secondary(context: Context, ground: Int = get(context), option: ColorOption? = null): Int? {
        val prefs2 = PreferenceManager2.getInstance(context)
        val chosen = option ?: prefs2.secondaryColor.firstCached(prefs2)
        return (chosen as? ColorOption.CustomColor)?.color?.let { it or OPAQUE }?.takeIf { isVisible(it, ground) }
    }

    /** The mix at which the dimmed accent stands for secondary text; the user's secondary color is drawn there as it is. */
    const val SECONDARY_MIX = 0.62f

    /**
     * A tone between [ground] (0) and [accent] (1): the surfaces, the lines, the secondary text and
     * the text itself. With a [secondary] color, the middle tones (lines and secondary text) are
     * drawn from it instead, reaching it at [SECONDARY_MIX]; the surfaces and the text keep the accent.
     */
    fun shade(ground: Int, accent: Int, secondary: Int?, mix: Float): Int = if (secondary != null && mix >= 0.25f && mix < 0.9f) {
        ColorUtils.blendARGB(ground, secondary, (mix / SECONDARY_MIX).coerceAtMost(1f))
    } else {
        ColorUtils.blendARGB(ground, accent, mix)
    }

    /** The least contrast that still reads as text over a fill. */
    private const val READABLE = 4.5

    /**
     * The color for text and glyphs drawn over a solid [accent] fill. It is the [ground] (so the
     * default terminal look is unchanged) as long as that reads well; when the accent is dark, or
     * the ground is light, whichever of black and white contrasts best takes its place.
     */
    fun onAccent(accent: Int, ground: Int): Int {
        val fill = accent or OPAQUE
        val text = ground or OPAQUE
        if (ColorUtils.calculateContrast(text, fill) >= READABLE) return text
        return listOf(text, Color.BLACK, Color.WHITE).maxBy { ColorUtils.calculateContrast(it, fill) }
    }

    fun onAccent(context: Context, accent: Int): Int = onAccent(accent, get(context))

    private const val INK_LIGHT = 0xFFE8FBEE.toInt()
    private const val INK_DARK = 0xFF101312.toInt()

    /** The color of plain text laid straight over the ground: light on a dark ground, dark on a light one. */
    fun ink(ground: Int): Int {
        val base = ground or OPAQUE
        return if (ColorUtils.calculateContrast(INK_LIGHT, base) >= READABLE) INK_LIGHT else INK_DARK
    }

    fun ink(context: Context): Int = ink(get(context))

    private const val OPAQUE = 0xFF000000.toInt()
}
