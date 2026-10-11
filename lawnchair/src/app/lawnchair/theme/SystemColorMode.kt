package app.lawnchair.theme

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.theme.color.ColorOption
import com.patrykmichalik.opto.core.setBlocking

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

    /** How [pin] lays out the pair the screen shows now. */
    enum class Pin { INVERTED, LIGHT, DARK }

    /**
     * Keeps the pair the screen draws now, laid out as [pin] asks (traded, or with the lighter or the
     * darker color as the background), as the user's own colors, and stops following the system so
     * it stays that way.
     */
    fun pin(context: Context, pin: Pin) {
        val prefs2 = PreferenceManager2.getInstance(context)
        val accent = ThemeProvider.INSTANCE.get(context).phosphorColor
        val ground = LauncherGround.get(context)
        val trade = when (pin) {
            Pin.INVERTED -> true
            Pin.LIGHT -> swaps(accent, ground, night = false)
            Pin.DARK -> swaps(accent, ground, night = true)
        }
        val (newAccent, newGround) = if (trade) ground to accent else accent to ground
        prefs2.followSystemColorMode.setBlocking(false)
        // The background goes first, as when a ready-made pair is picked.
        prefs2.launcherBackgroundColor.setBlocking(ColorOption.CustomColor(newGround or OPAQUE))
        prefs2.accentColor.setBlocking(ColorOption.CustomColor(newAccent or OPAQUE))
    }

    /** Makes the colors follow the system mode from now on. */
    fun follow(context: Context) {
        PreferenceManager2.getInstance(context).followSystemColorMode.setBlocking(true)
    }

    private const val OPAQUE = 0xFF000000.toInt()
}
