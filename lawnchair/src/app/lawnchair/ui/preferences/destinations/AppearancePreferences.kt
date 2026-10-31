package app.lawnchair.ui.preferences.destinations

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.lawnchair.command.ThemeSpec
import app.lawnchair.command.ThemeStore
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppearancePreview
import app.lawnchair.ui.preferences.components.FontPreference
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.SavedThemesPreference
import app.lawnchair.ui.preferences.components.WallpaperSourcePreference
import app.lawnchair.ui.preferences.components.colorpreference.ColorModeShortcuts
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreference
import app.lawnchair.ui.preferences.components.colorpreference.ComplementaryColorsPreference
import app.lawnchair.ui.preferences.components.colorpreference.FollowSystemColorModePreference
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.BottomSpacer
import app.lawnchair.ui.preferences.components.layout.DividerColumn
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.GeneralIconShape
import app.lawnchair.views.ScreenAnimationView
import com.android.launcher3.R

/** What the last change a tap made was, and how things were just before it, so it can be put back. */
private class UndoState(val before: ThemeSpec, @StringRes val message: Int)

/**
 * Everything that decides how the launcher looks, in one place. What most people reach for sits on top
 * (themes, the main colors, the wallpaper, shapes and font) and the rest (colors of the dock, folders and
 * search bar, the terminal effects) is under "Advanced". A small home screen stays at the bottom, so every
 * change can be seen as it is made, and a look that was just applied can be undone.
 */
@Composable
fun AppearancePreferences(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    // The look before the last change a tap made. It stays until the next change or until it is dismissed,
    // so nobody is rushed: a timer would take it away from whoever needs longer.
    var undoTo by remember { mutableStateOf<UndoState?>(null) }
    val rememberLook = { message: Int -> { undoTo = UndoState(ThemeStore.current(context), message) } }
    var advancedOpen by rememberSaveable { mutableStateOf(false) }

    PreferenceLayout(
        backArrowVisible = !LocalIsExpandedScreen.current,
        label = stringResource(id = R.string.appearance_label),
        modifier = modifier,
        bottomBar = {
            Column {
                AppearancePreview()
                BottomSpacer()
            }
        },
    ) {
        // Whole looks first: the quick choice of mode, the ready-made ones, the user's own and what can be done with them.
        PreferenceGroup(heading = stringResource(id = R.string.appearance_group_themes)) {
            ExpandAndShrink(visible = undoTo != null) {
                undoTo?.let { state ->
                    UndoBanner(
                        message = state.message,
                        onUndo = {
                            ThemeStore.apply(context, state.before)
                            undoTo = null
                        },
                        onDismiss = { undoTo = null },
                    )
                }
            }
            ColorModeShortcuts(onBeforeChange = rememberLook(R.string.appearance_undo_mode))
            ComplementaryColorsPreference(onBeforeApply = rememberLook(R.string.appearance_undo_message))
            SavedThemesPreference(onBeforeApply = rememberLook(R.string.appearance_undo_theme))
        }

        // Then each color on its own, with how the pair follows the system mode.
        PreferenceGroup(
            heading = stringResource(id = R.string.appearance_group_colors),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            ColorPreference(preference = prefs2.accentColor)
            ColorPreference(preference = prefs2.launcherBackgroundColor)
            ColorPreference(preference = prefs2.secondaryColor)
        }

        PreferenceGroup(
            heading = stringResource(id = R.string.appearance_group_wallpaper),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            val wallpaperShown = prefs2.showSystemWallpaper.getAdapter().state.value
            WallpaperSourcePreference()
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

        // The shape app icons and folders are cut to, the font and the clock: how things are drawn, which a theme carries too.
        PreferenceGroup(
            heading = stringResource(id = R.string.appearance_group_style),
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

        // What few people change: the colors of single parts, following the system mode and the terminal effects, out of the way until asked for.
        val advancedState = stringResource(id = if (advancedOpen) R.string.appearance_expanded else R.string.appearance_collapsed)
        PreferenceGroup(modifier = Modifier.padding(top = 8.dp)) {
            ClickablePreference(
                label = stringResource(id = R.string.appearance_group_advanced),
                subtitle = stringResource(id = R.string.appearance_advanced_desc),
                endWidget = {
                    Icon(
                        imageVector = if (advancedOpen) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                    )
                },
                modifier = Modifier.semantics {
                    stateDescription = advancedState
                },
                onClick = { advancedOpen = !advancedOpen },
            )
        }
        ExpandAndShrink(visible = advancedOpen) {
            Column {
                PreferenceGroup(modifier = Modifier.padding(top = 8.dp)) {
                    FollowSystemColorModePreference()
                    ColorPreference(preference = prefs2.strokeColorStyle)
                    ColorPreference(preference = prefs2.hotseatBackgroundColor)
                    ColorPreference(preference = prefs2.folderColor)
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
                            // The animation a theme brought is a choice only while there is one stored.
                            val customAnimation = prefs2.screenAnimationCustom.getAdapter().state.value
                            ListPreference(
                                adapter = animationKind,
                                entries = listOf(
                                    ListPreferenceEntry("monitor") { stringResource(R.string.screen_animation_monitor) },
                                    ListPreferenceEntry("snow") { stringResource(R.string.screen_animation_snow) },
                                    ListPreferenceEntry("petals") { stringResource(R.string.screen_animation_petals) },
                                    ListPreferenceEntry("stars") { stringResource(R.string.screen_animation_stars) },
                                    ListPreferenceEntry("rain") { stringResource(R.string.screen_animation_rain) },
                                ) + listOfNotNull(
                                    if (customAnimation.isNotBlank()) ListPreferenceEntry("custom") { stringResource(R.string.screen_animation_custom) } else null,
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
                            ScreenAnimationPreview(kind = animationKind.state.value, custom = customAnimation, intensity = animationIntensity.state.value)
                        }
                    }
                }
            }
        }
    }
}

/** The chosen animation playing over a stand-in wallpaper, so it can be judged before leaving Settings. */
@Composable
private fun ScreenAnimationPreview(kind: String, custom: String, intensity: Float) {
    AndroidView(
        factory = { ScreenAnimationView(it) },
        update = {
            it.play(kind, custom)
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

/** Tells what just changed and offers to put the previous look back; read out by a screen reader as it appears. */
@Composable
private fun UndoBanner(@StringRes message: Int, onUndo: () -> Unit, onDismiss: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = 16.dp, end = 4.dp),
    ) {
        Text(
            text = stringResource(id = message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onUndo) {
            Text(text = stringResource(id = R.string.appearance_undo))
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(id = R.string.appearance_dismiss),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
