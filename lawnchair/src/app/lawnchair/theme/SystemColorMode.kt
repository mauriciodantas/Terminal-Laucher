package app.lawnchair.theme

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached

/**
 * Following the system's light and dark mode. The accent and the background the user picked stay
 * saved as they are; while this is on they only trade places on screen, so that the darker of the
 * two is the background in dark mode and the lighter one in light mode. Contrast is the same both
 * ways, so a pair that reads well keeps reading well.
 */
object SystemColorMode {

    /** True while the user asked the colors to follow the system mode. */
    fun follows(context: Context): Boolean {
        val prefs2 = PreferenceManager2.getInstance(context)
        return prefs2.followSystemColorMode.firstCached(prefs2)
    }

    /** True when the system is in dark mode. Read from the system itself, not from a context a theme may have overridden. */
    fun isNight(): Boolean = (Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** True when [accent] over [ground] is the wrong way round for a system in [night] mode. */
    fun swaps(accent: Int, ground: Int, night: Boolean): Boolean {
        val groundIsLighter = ColorUtils.calculateLuminance(ground or OPAQUE) > ColorUtils.calculateLuminance(accent or OPAQUE)
        return groundIsLighter == night
    }

    /** True when the screen should draw [accent] as the background and [ground] as the text right now. */
    fun swaps(context: Context, accent: Int, ground: Int): Boolean = follows(context) && swaps(accent, ground, isNight())

    private const val OPAQUE = 0xFF000000.toInt()
}
