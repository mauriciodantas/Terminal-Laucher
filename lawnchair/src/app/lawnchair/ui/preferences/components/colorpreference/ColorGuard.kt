package app.lawnchair.ui.preferences.components.colorpreference

import android.content.Context
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.theme.LauncherGround
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.theme.color.ColorOption
import com.android.launcher3.R

/**
 * Keeps the accent (text and lines), and the secondary color, apart from the background. A pair with less than
 * [LauncherGround.MIN_VISIBILITY] of contrast would leave the settings unreadable and the user
 * locked out of changing it back, so picking one is refused. A pair that is allowed but under
 * [LauncherGround.COMFORTABLE_VISIBILITY] only draws a warning.
 */
object ColorGuard {

    enum class Role(val explanation: Int) {
        ACCENT(R.string.color_conflict_accent),
        BACKGROUND(R.string.color_conflict_background),
        SECONDARY(R.string.color_conflict_secondary),
    }

    /** What the preference named [prefKey] is: the accent, the background, or neither. */
    fun roleOf(prefKey: String, context: Context): Role? {
        val prefs2 = PreferenceManager2.getInstance(context)
        return when (prefKey) {
            prefs2.accentColor.key.name -> Role.ACCENT
            prefs2.launcherBackgroundColor.key.name -> Role.BACKGROUND
            prefs2.secondaryColor.key.name -> Role.SECONDARY
            else -> null
        }
    }

    /** True when [candidate] for the [role] is allowed but close enough to the other color to be hard on the eyes. */
    fun weak(context: Context, role: Role, candidate: ColorOption): Boolean {
        if (conflicts(context, role, candidate)) return false
        val prefs2 = PreferenceManager2.getInstance(context)
        val themes = ThemeProvider.INSTANCE.get(context)
        val (accent, ground) = when (role) {
            Role.ACCENT -> themes.phosphorOf(candidate) to
                (LauncherGround.resolve(prefs2.launcherBackgroundColor.firstCached(prefs2), context) ?: LauncherGround.DEFAULT)

            Role.BACKGROUND -> themes.chosenPhosphorColor to (LauncherGround.resolve(candidate, context) ?: LauncherGround.DEFAULT)

            // The secondary color is read over the background just like the accent.
            Role.SECONDARY -> (LauncherGround.resolve(candidate, context) ?: return false) to
                (LauncherGround.resolve(prefs2.launcherBackgroundColor.firstCached(prefs2), context) ?: LauncherGround.DEFAULT)
        }
        return !LauncherGround.isVisible(accent, ground, LauncherGround.COMFORTABLE_VISIBILITY)
    }

    /** True when [candidate] for the [role] would be too close to the other color to read. */
    fun conflicts(context: Context, role: Role, candidate: ColorOption): Boolean {
        val prefs2 = PreferenceManager2.getInstance(context)
        val themes = ThemeProvider.INSTANCE.get(context)
        return when (role) {
            Role.ACCENT -> {
                val ground = LauncherGround.resolve(prefs2.launcherBackgroundColor.firstCached(prefs2), context)
                    ?: LauncherGround.DEFAULT
                !LauncherGround.isVisible(themes.phosphorOf(candidate), ground)
            }

            Role.BACKGROUND -> {
                val ground = LauncherGround.resolve(candidate, context) ?: LauncherGround.DEFAULT
                !LauncherGround.isVisible(themes.chosenPhosphorColor, ground)
            }

            Role.SECONDARY -> {
                // Keeping the dimmed accent never conflicts.
                val color = LauncherGround.resolve(candidate, context) ?: return false
                val ground = LauncherGround.resolve(prefs2.launcherBackgroundColor.firstCached(prefs2), context)
                    ?: LauncherGround.DEFAULT
                !LauncherGround.isVisible(color, ground)
            }
        }
    }
}
