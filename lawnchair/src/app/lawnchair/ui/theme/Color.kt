package app.lawnchair.ui.theme

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.annotation.ColorInt
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.core.graphics.ColorUtils
import app.lawnchair.theme.LauncherGround
import app.lawnchair.theme.UiColorMode
import app.lawnchair.theme.color.tokens.ColorTokens
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.Themes

@JvmOverloads
fun Context.getAccentColor(darkTheme: Boolean = Themes.getAttrBoolean(this, R.attr.isMainColorDark)): Int {
    return ColorTokens.ColorAccent.resolveColor(this, if (darkTheme) UiColorMode.Dark else UiColorMode.Light)
}

@ColorInt
fun lightenColor(@ColorInt color: Int): Int {
    var newColor = color
    val outHsl = floatArrayOf(0f, 0f, 0f)
    ColorUtils.colorToHSL(color, outHsl)

    while (ColorUtils.calculateContrast(newColor, 0xFF000000.toInt()) < 6.5) {
        outHsl[2] += 0.05F
        newColor = ColorUtils.HSLToColor(outHsl)
    }

    return newColor
}

@Suppress("DEPRECATION")
fun Context.getSystemAccent(darkTheme: Boolean): Int {
    val res = resources
    return if (Utilities.ATLEAST_S) {
        val colorId = if (darkTheme) R.color.system_accent1_100 else R.color.system_accent1_600
        res.getColor(colorId)
    } else {
        var propertyValue = Utilities.getSystemProperty("persist.sys.theme.accentcolor", "")
        if (!propertyValue.isNullOrEmpty()) {
            if (!propertyValue.startsWith('#')) {
                propertyValue = "#$propertyValue"
            }
            try {
                return Color.parseColor(propertyValue)
            } catch (_: IllegalArgumentException) {
            }
        }

        val typedValue = TypedValue()
        val theme = if (darkTheme) android.R.style.Theme_DeviceDefault else android.R.style.Theme_DeviceDefault_Light
        val contextWrapper = ContextThemeWrapper(this, theme)
        contextWrapper.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
        typedValue.data
    }
}

@Composable
fun preferenceGroupColor() = (if (isSelectedThemeDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceBright)

@Composable
fun dividerColor() = MaterialTheme.colorScheme.outlineVariant

/** Nostromo terminal palette: phosphor on near-black, red only for danger. */
object Nostromo {
    val Alert = androidx.compose.ui.graphics.Color(0xFFFF5A45)
}

/** Builds the terminal color scheme for the given phosphor [accent] color. */
fun nostromoColorScheme(accent: Int, groundColor: Int = LauncherGround.DEFAULT): androidx.compose.material3.ColorScheme {
    fun shade(mix: Float) = androidx.compose.ui.graphics.Color(ColorUtils.blendARGB(groundColor, accent, mix))
    val phosphor = shade(1f)
    val ground = shade(0f)
    val dim = shade(0.62f)
    val line = shade(0.32f)
    val panelHigh = shade(0.10f)
    val panel = shade(0.05f)
    return androidx.compose.material3.darkColorScheme(
        primary = phosphor,
        onPrimary = ground,
        primaryContainer = panelHigh,
        onPrimaryContainer = phosphor,
        secondary = phosphor,
        onSecondary = ground,
        secondaryContainer = panelHigh,
        onSecondaryContainer = phosphor,
        tertiary = phosphor,
        onTertiary = ground,
        tertiaryContainer = panelHigh,
        onTertiaryContainer = phosphor,
        background = ground,
        onBackground = phosphor,
        surface = ground,
        onSurface = phosphor,
        surfaceVariant = panelHigh,
        onSurfaceVariant = dim,
        surfaceTint = phosphor,
        inverseSurface = phosphor,
        inverseOnSurface = ground,
        error = Nostromo.Alert,
        onError = ground,
        errorContainer = androidx.compose.ui.graphics.Color(0xFF3A1410),
        onErrorContainer = Nostromo.Alert,
        outline = dim,
        outlineVariant = line,
        scrim = androidx.compose.ui.graphics.Color(0xFF000000),
        surfaceBright = panelHigh,
        surfaceDim = ground,
        surfaceContainerLowest = ground,
        surfaceContainerLow = panel,
        surfaceContainer = panel,
        surfaceContainerHigh = panelHigh,
        surfaceContainerHighest = panelHigh,
    )
}
