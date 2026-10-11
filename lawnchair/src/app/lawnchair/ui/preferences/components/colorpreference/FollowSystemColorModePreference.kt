package app.lawnchair.ui.preferences.components.colorpreference

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.LauncherGround
import app.lawnchair.theme.SystemColorMode
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import com.android.launcher3.R

/**
 * The switch that makes the accent and the background follow the system's light and dark mode. Its
 * swatch shows the chosen pair both ways, the light version and the dark one, so what the switch does
 * is plain before it is turned on.
 */
@Composable
fun FollowSystemColorModePreference(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs2 = preferenceManager2()
    val follows = prefs2.followSystemColorMode.getAdapter()
    val accentOption = prefs2.accentColor.getAdapter().state.value
    val backgroundOption = prefs2.launcherBackgroundColor.getAdapter().state.value
    // The pair as picked, not as drawn: the swatch sorts it into its light and dark versions itself.
    val (accent, ground) = LauncherGround.drawn(ThemeProvider.INSTANCE.get(context).phosphorOf(accentOption), backgroundOption, context, follows = false)
    val groundIsLighter = ColorUtils.calculateLuminance(ground) > ColorUtils.calculateLuminance(accent)
    val light = if (groundIsLighter) accent to ground else ground to accent
    val dark = if (groundIsLighter) ground to accent else accent to ground

    val description = if (follows.state.value) {
        // Read so the line updates when the system switches; the mode itself comes from the system, not this screen's theme.
        LocalConfiguration.current.uiMode
        stringResource(
            id = if (SystemColorMode.isNight()) R.string.appearance_follow_system_mode_now_dark else R.string.appearance_follow_system_mode_now_light,
        )
    } else {
        stringResource(id = R.string.appearance_follow_system_mode_desc)
    }
    SwitchPreference(
        checked = follows.state.value,
        onCheckedChange = follows::onChange,
        label = stringResource(id = R.string.appearance_follow_system_mode),
        description = description,
        modifier = modifier,
        startWidget = { ModeSwatch(light = light, dark = dark) },
    )
}

/** The pair side by side: the [light] version (text over background) on the left, the [dark] one on the right. */
@Composable
private fun ModeSwatch(light: Pair<Int, Int>, dark: Pair<Int, Int>) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .size(width = 56.dp, height = 36.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Half(colors = light, icon = Icons.Rounded.LightMode)
        Half(colors = dark, icon = Icons.Rounded.DarkMode)
    }
}

@Composable
private fun RowScope.Half(colors: Pair<Int, Int>, icon: ImageVector) {
    val (text, background) = colors
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(Color(background)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = Color(text), modifier = Modifier.size(12.dp))
            Text(text = ">_", color = Color(text), style = MaterialTheme.typography.labelSmall)
        }
    }
}
