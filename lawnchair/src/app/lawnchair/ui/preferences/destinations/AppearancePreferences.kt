package app.lawnchair.ui.preferences.destinations

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.FontPreference
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.SavedThemesPreference
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.colorpreference.ComplementaryColorsPreference
import app.lawnchair.ui.preferences.components.colorpreference.FollowSystemColorModePreference
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.DividerColumn
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.GeneralIconShape
import app.lawnchair.views.ScreenAnimationView
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
        // Whole looks first: the ready-made ones, the user's own and what can be done with them.
        PreferenceGroup(heading = stringResource(id = R.string.appearance_group_themes)) {
            ComplementaryColorsPreference()
            SavedThemesPreference()
        }

        // Then each color on its own, with how the pair follows the system mode.
        PreferenceGroup(
            heading = stringResource(id = R.string.appearance_group_colors),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            ColorPreference(preference = prefs2.accentColor)
            ColorPreference(preference = prefs2.launcherBackgroundColor)
            ColorPreference(preference = prefs2.secondaryColor)
            FollowSystemColorModePreference()
            ColorPreference(preference = prefs2.strokeColorStyle)
            ColorPreference(preference = prefs2.hotseatBackgroundColor)
            ColorPreference(preference = prefs2.folderColor)
        }

        PreferenceGroup(
            heading = stringResource(id = R.string.appearance_group_wallpaper),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            val wallpaperShown = prefs2.showSystemWallpaper.getAdapter().state.value
            val colorAsWallpaper = prefs2.backgroundColorAsWallpaper.getAdapter()
            val colorOnLockScreen = prefs2.backgroundColorOnLockScreen.getAdapter()
            var confirmLockScreen by remember { mutableStateOf(false) }
            SwitchPreference(
                adapter = colorAsWallpaper,
                label = stringResource(id = R.string.appearance_bg_as_wallpaper),
                description = stringResource(id = R.string.appearance_bg_as_wallpaper_desc),
            )
            SwitchPreference(
                checked = colorOnLockScreen.state.value,
                // Turning it on replaces the user's lock screen image for good, so it asks first.
                onCheckedChange = { on -> if (on) confirmLockScreen = true else colorOnLockScreen.onChange(false) },
                label = stringResource(id = R.string.appearance_bg_on_lock_screen),
                description = stringResource(id = R.string.appearance_bg_on_lock_screen_desc),
            )
            if (confirmLockScreen) {
                AlertDialog(
                    onDismissRequest = { confirmLockScreen = false },
                    title = { Text(text = stringResource(id = R.string.appearance_bg_on_lock_screen_confirm_title)) },
                    text = { Text(text = stringResource(id = R.string.appearance_bg_on_lock_screen_confirm_text)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                confirmLockScreen = false
                                colorOnLockScreen.onChange(true)
                            },
                        ) {
                            Text(text = stringResource(id = R.string.appearance_bg_on_lock_screen_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmLockScreen = false }) {
                            Text(text = stringResource(id = android.R.string.cancel))
                        }
                    },
                )
            }
            SwitchPreference(
                adapter = prefs2.showSystemWallpaper.getAdapter(),
                label = stringResource(id = R.string.appearance_wallpaper_show),
                description = stringResource(id = R.string.appearance_wallpaper_show_desc),
            )
            // Dimming and picking only matter while the wallpaper is on screen.
            ExpandAndShrink(visible = wallpaperShown) {
                DividerColumn {
                    SliderPreference(
                        label = stringResource(id = R.string.appearance_wallpaper_dim),
                        adapter = prefs2.wallpaperDim.getAdapter(),
                        step = 0.05f,
                        valueRange = 0f..1f,
                        showAsPercentage = true,
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
            }
        }

        // The shape app icons and folders are cut to, which a theme carries too.
        PreferenceGroup(
            heading = stringResource(id = R.string.appearance_group_shapes),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            val appShape = prefs2.iconShape.getAdapter().state.value
            val folderShape = prefs2.folderShape.getAdapter().state.value
            val shapeEntries = iconShapeEntries(context)
            NavigationActionPreference(
                label = stringResource(id = R.string.icon_shape_label),
                destination = GeneralIconShape(ShapeRoute.APP_SHAPE),
                subtitle = shapeEntries.firstOrNull { it.value == appShape }?.label?.invoke() ?: stringResource(id = R.string.custom),
                endWidget = { IconShapePreview(iconShape = appShape) },
            )
            NavigationActionPreference(
                label = stringResource(id = R.string.folder_shape_label),
                destination = GeneralIconShape(ShapeRoute.FOLDER_SHAPE),
                subtitle = shapeEntries.firstOrNull { it.value == folderShape }?.label?.invoke() ?: stringResource(id = R.string.custom),
                endWidget = { IconShapePreview(iconShape = folderShape) },
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

        PreferenceGroup(
            heading = stringResource(id = R.string.effects_group),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            SwitchPreference(
                adapter = prefs2.terminalIconEffect.getAdapter(),
                label = stringResource(id = R.string.terminal_icon_effect),
                description = stringResource(id = R.string.terminal_icon_effect_desc),
            )
            val widgetEffect = prefs2.widgetEffect.getAdapter()
            SwitchPreference(
                adapter = widgetEffect,
                label = stringResource(id = R.string.widget_effect),
                description = stringResource(id = R.string.widget_effect_desc),
            )
            ExpandAndShrink(visible = widgetEffect.state.value) {
                SliderPreference(
                    label = stringResource(id = R.string.effect_intensity),
                    adapter = prefs2.widgetEffectIntensity.getAdapter(),
                    step = 0.05f,
                    valueRange = 0.05f..1f,
                    showAsPercentage = true,
                )
            }
            val crtEffect = prefs2.crtEffect.getAdapter()
            SwitchPreference(
                adapter = crtEffect,
                label = stringResource(id = R.string.screen_animation),
                description = stringResource(id = R.string.screen_animation_desc),
            )
            ExpandAndShrink(visible = crtEffect.state.value) {
                Column {
                    val animationKind = prefs2.screenAnimation.getAdapter()
                    val animationIntensity = prefs2.crtEffectIntensity.getAdapter()
                    ListPreference(
                        adapter = animationKind,
                        entries = listOf(
                            ListPreferenceEntry("monitor") { stringResource(R.string.screen_animation_monitor) },
                            ListPreferenceEntry("snow") { stringResource(R.string.screen_animation_snow) },
                            ListPreferenceEntry("petals") { stringResource(R.string.screen_animation_petals) },
                            ListPreferenceEntry("stars") { stringResource(R.string.screen_animation_stars) },
                            ListPreferenceEntry("rain") { stringResource(R.string.screen_animation_rain) },
                        ),
                        label = stringResource(id = R.string.screen_animation_kind),
                    )
                    SliderPreference(
                        label = stringResource(id = R.string.effect_intensity),
                        adapter = animationIntensity,
                        step = 0.05f,
                        valueRange = 0.05f..1f,
                        showAsPercentage = true,
                    )
                    ScreenAnimationPreview(kind = animationKind.state.value, intensity = animationIntensity.state.value)
                }
            }
        }
    }
}

/** The chosen animation playing over a stand-in wallpaper, so it can be judged before leaving Settings. */
@Composable
private fun ScreenAnimationPreview(kind: String, intensity: Float) {
    AndroidView(
        factory = { ScreenAnimationView(it) },
        update = {
            it.play(kind)
            it.intensity = intensity
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF14102B), Color(0xFF3D2547), Color(0xFF8A4B5C)))),
    )
}
