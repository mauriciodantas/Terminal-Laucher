package app.lawnchair.ui.preferences.destinations

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.FontPreference
import app.lawnchair.ui.preferences.components.colorpreference.ColorGuard
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.colorpreference.ComplementaryColorsPreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
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
        PreferenceGroup(heading = stringResource(id = R.string.appearance_group_colors)) {
            // A pair of colors that would hide the interface is refused, with the reason.
            val accentAdapter = prefs2.accentColor.getAdapter()
            val guardedAccent = remember(accentAdapter) {
                object : PreferenceAdapter<ColorOption> {
                    override val state = accentAdapter.state
                    override fun onChange(newValue: ColorOption) {
                        if (ColorGuard.conflicts(context, ColorGuard.Role.ACCENT, newValue)) {
                            Toast.makeText(context, R.string.color_conflict_accent, Toast.LENGTH_LONG).show()
                        } else {
                            accentAdapter.onChange(newValue)
                        }
                    }
                }
            }
            ListPreference(
                adapter = guardedAccent,
                entries = phosphorEntries,
                label = stringResource(id = R.string.phosphor_color_label),
            )
            ComplementaryColorsPreference()
            ColorPreference(preference = prefs2.accentColor)
            ColorPreference(preference = prefs2.launcherBackgroundColor)
            ColorPreference(preference = prefs2.strokeColorStyle)
            ColorPreference(preference = prefs2.hotseatBackgroundColor)
            ColorPreference(preference = prefs2.folderColor)
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
