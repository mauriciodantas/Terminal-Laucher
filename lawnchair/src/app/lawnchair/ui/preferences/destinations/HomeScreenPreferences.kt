/*
 * Copyright 2022, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.preferences.destinations

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.color.ColorMode
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.NotificationDotsPreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.notificationDotsEnabled
import app.lawnchair.ui.preferences.components.notificationServiceEnabled
import app.lawnchair.ui.preferences.navigation.HomeScreenGrid
import com.android.launcher3.R

object HomeScreenRoutes {
    const val GRID = "grid"
    const val POPUP_EDITOR = "popup_editor"
}

@Composable
fun HomeScreenPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()
    val context = LocalContext.current
    val notificationEnabled by remember { notificationDotsEnabled(context) }.collectAsStateWithLifecycle(initialValue = false)
    val serviceEnabled = notificationServiceEnabled()
    PreferenceLayout(
        label = stringResource(id = R.string.home_screen_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        val columns by prefs.workspaceColumns.getAdapter()
        val rows by prefs.workspaceRows.getAdapter()
        PreferenceGroup(heading = stringResource(id = R.string.home_group_layout)) {
            NavigationActionPreference(
                label = stringResource(id = R.string.home_screen_grid),
                destination = HomeScreenGrid,
                subtitle = stringResource(id = R.string.x_by_y, columns, rows),
            )
            // The favorites row is the hotseat: the same pref the grid screen edits, applied on release.
            SliderPreference(
                label = stringResource(id = R.string.home_favorites_count),
                adapter = prefs.hotseatColumns.getAdapter(),
                step = 1,
                valueRange = 3..10,
            )
            SwitchPreference(
                adapter = prefs2.lockHomeScreen.getAdapter(),
                label = stringResource(id = R.string.home_screen_lock),
                description = stringResource(id = R.string.home_screen_lock_description),
            )
            SwitchPreference(
                adapter = prefs.allowRotation.getAdapter(),
                label = stringResource(id = R.string.home_screen_rotation_label),
                description = stringResource(id = R.string.home_screen_rotation_description),
            )
        }
        PreferenceGroup(
            heading = stringResource(id = R.string.icons),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            SliderPreference(
                label = stringResource(id = R.string.home_icon_size),
                adapter = prefs2.homeIconSizeFactor.getAdapter(),
                valueRange = 0.5f..1.5f,
                step = 0.05f,
                showAsPercentage = true,
            )
            SwitchPreference(
                adapter = prefs2.showIconLabelsOnHomeScreen.getAdapter(),
                label = stringResource(id = R.string.show_labels),
            )
            NotificationDotsPreference(enabled = notificationEnabled, serviceEnabled = serviceEnabled)
        }
        ChatShortcutsPreferences(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun HomeScreenTextColorPreference(
    modifier: Modifier = Modifier,
) {
    ListPreference(
        adapter = preferenceManager2().workspaceTextColor.getAdapter(),
        entries = ColorMode.entries(),
        label = stringResource(id = R.string.home_screen_text_color),
        modifier = modifier,
    )
}
