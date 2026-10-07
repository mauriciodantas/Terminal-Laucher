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

    /** The user's color, or null when they kept the default. */
    fun custom(context: Context): Int? {
        val prefs2 = PreferenceManager2.getInstance(context)
        return resolve(prefs2.launcherBackgroundColor.firstCached(prefs2), context)
    }

    fun get(context: Context): Int = custom(context) ?: DEFAULT

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
