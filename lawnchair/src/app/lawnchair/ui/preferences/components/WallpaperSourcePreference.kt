package app.lawnchair.ui.preferences.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import com.android.launcher3.R

/** What the home screen is painted with, and whether the system wallpapers follow the background color. */
private enum class WallpaperSource { SYSTEM, COLOR, COLOR_SYSTEM, COLOR_LOCK }

/**
 * One choice in place of three overlapping switches: the system wallpaper behind the icons, only the
 * background color, the color also written as the system wallpaper, or also on the lock screen. Each
 * choice sets the three stored switches together, so they can no longer contradict each other. When the
 * stored switches already disagree (a theme can set them one by one), the strongest one wins.
 */
@Composable
fun WallpaperSourcePreference(modifier: Modifier = Modifier) {
    val prefs2 = preferenceManager2()
    val showWallpaper = prefs2.showSystemWallpaper.getAdapter()
    val colorAsWallpaper = prefs2.backgroundColorAsWallpaper.getAdapter()
    val colorOnLockScreen = prefs2.backgroundColorOnLockScreen.getAdapter()
    var confirmLockScreen by remember { mutableStateOf(false) }

    val current = when {
        colorOnLockScreen.state.value -> WallpaperSource.COLOR_LOCK
        colorAsWallpaper.state.value -> WallpaperSource.COLOR_SYSTEM
        showWallpaper.state.value -> WallpaperSource.SYSTEM
        else -> WallpaperSource.COLOR
    }

    fun select(source: WallpaperSource) {
        showWallpaper.onChange(source == WallpaperSource.SYSTEM)
        colorAsWallpaper.onChange(source == WallpaperSource.COLOR_SYSTEM || source == WallpaperSource.COLOR_LOCK)
        colorOnLockScreen.onChange(source == WallpaperSource.COLOR_LOCK)
    }

    val description = stringResource(
        id = when (current) {
            WallpaperSource.SYSTEM -> R.string.appearance_wallpaper_show_desc
            WallpaperSource.COLOR -> R.string.appearance_source_color_desc
            WallpaperSource.COLOR_SYSTEM -> R.string.appearance_bg_as_wallpaper_desc
            WallpaperSource.COLOR_LOCK -> R.string.appearance_bg_on_lock_screen_desc
        },
    )
    ListPreference(
        entries = listOf(
            ListPreferenceEntry(WallpaperSource.SYSTEM) { stringResource(R.string.appearance_source_system) },
            ListPreferenceEntry(WallpaperSource.COLOR) { stringResource(R.string.appearance_source_color) },
            ListPreferenceEntry(WallpaperSource.COLOR_SYSTEM) { stringResource(R.string.appearance_bg_as_wallpaper) },
            ListPreferenceEntry(WallpaperSource.COLOR_LOCK) { stringResource(R.string.appearance_source_color_lock) },
        ),
        value = current,
        // Writing the color on the lock screen replaces the user's image for good, so it asks first.
        onValueChange = { if (it == WallpaperSource.COLOR_LOCK && current != it) confirmLockScreen = true else select(it) },
        label = stringResource(id = R.string.appearance_wallpaper_source),
        description = description,
        modifier = modifier,
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
                        select(WallpaperSource.COLOR_LOCK)
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
}
