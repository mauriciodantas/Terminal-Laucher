package app.lawnchair.ui.preferences.destinations

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.FontPreference
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.colorpreference.ComplementaryColorsPreference
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R

/** Everything that decides how the launcher looks: the colors and the font, in one place. */
@Composable
fun AppearancePreferences(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()

    PreferenceLayout(
        backArrowVisible = !LocalIsExpandedScreen.current,
        label = stringResource(id = R.string.appearance_label),
        modifier = modifier,
    ) {
        PreferenceGroup(heading = stringResource(id = R.string.appearance_group_presets)) {
            ComplementaryColorsPreference()
        }

        PreferenceGroup(
            heading = stringResource(id = R.string.appearance_group_colors),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            ColorPreference(preference = prefs2.accentColor)
            ColorPreference(preference = prefs2.launcherBackgroundColor)
        }

        PreferenceGroup(
            heading = stringResource(id = R.string.appearance_group_details),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            ColorPreference(preference = prefs2.strokeColorStyle)
            ColorPreference(preference = prefs2.hotseatBackgroundColor)
            ColorPreference(preference = prefs2.folderColor)
        }

        PreferenceGroup(
            heading = stringResource(id = R.string.appearance_group_wallpaper),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            SwitchPreference(
                adapter = prefs2.showSystemWallpaper.getAdapter(),
                label = stringResource(id = R.string.appearance_wallpaper_show),
                description = stringResource(id = R.string.appearance_wallpaper_show_desc),
            )
            SliderPreference(
                label = stringResource(id = R.string.appearance_wallpaper_dim),
                adapter = prefs2.wallpaperDim.getAdapter(),
                step = 0.05f,
                valueRange = 0f..1f,
                showAsPercentage = true,
                enabled = prefs2.showSystemWallpaper.getAdapter().state.value,
            )
            ClickablePreference(
                label = stringResource(id = R.string.appearance_wallpaper_choose),
                subtitle = stringResource(id = R.string.appearance_wallpaper_choose_desc),
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_SET_WALLPAPER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
        }

        PreferenceGroup(
            heading = stringResource(id = R.string.appearance_group_font),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            FontPreference(
                fontPref = prefs.fontWorkspace,
                label = stringResource(id = R.string.appearance_font_ui),
            )
            SwitchPreference(
                adapter = prefs2.terminalPixelClock.getAdapter(),
                label = stringResource(id = R.string.appearance_pixel_clock),
                description = stringResource(id = R.string.appearance_pixel_clock_desc),
            )
        }
    }
}
