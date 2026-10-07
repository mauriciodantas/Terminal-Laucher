package app.lawnchair.theme

import android.content.Context
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.theme.color.ColorOption

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

    /** The user's color, or null when they kept the default. */
    fun custom(context: Context): Int? {
        val prefs2 = PreferenceManager2.getInstance(context)
        return resolve(prefs2.launcherBackgroundColor.firstCached(prefs2), context)
    }

    fun get(context: Context): Int = custom(context) ?: DEFAULT

    private const val OPAQUE = 0xFF000000.toInt()
}
