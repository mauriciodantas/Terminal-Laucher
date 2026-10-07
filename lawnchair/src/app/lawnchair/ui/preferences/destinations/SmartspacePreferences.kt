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

import android.Manifest
import android.app.Activity
import android.content.pm.LauncherApps
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.smartspace.SmartspaceViewContainer
import app.lawnchair.smartspace.glance.ChatCandidate
import app.lawnchair.smartspace.glance.ChatShortcuts
import app.lawnchair.smartspace.glance.GlanceEngine
import app.lawnchair.smartspace.glance.GlanceSetup
import app.lawnchair.smartspace.glance.GlanceSetupIntents
import app.lawnchair.smartspace.glance.MediaSetupStep
import app.lawnchair.smartspace.glance.WhatsAppChatSource
import app.lawnchair.smartspace.model.SmartspaceCalendar
import app.lawnchair.smartspace.model.SmartspaceMode
import app.lawnchair.smartspace.model.SmartspaceTimeFormat
import app.lawnchair.smartspace.provider.BluetoothBatteryProvider
import app.lawnchair.smartspace.provider.SmartspaceProvider
import app.lawnchair.smartspace.provider.SmartspaceWidgetReader
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.MainSwitchPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.notificationDotsEnabled
import app.lawnchair.ui.preferences.components.notificationServiceEnabled
import app.lawnchair.ui.theme.isSelectedThemeDark
import app.lawnchair.ui.theme.preferenceGroupColor
import com.android.launcher3.R
import com.kieronquinn.app.smartspacer.sdk.SmartspacerConstants
import kotlinx.coroutines.launch

@Composable
fun SmartspacePreferences(
    fromWidget: Boolean,
    modifier: Modifier = Modifier,
) {
    val preferenceManager2 = preferenceManager2()
    val smartspaceAdapter = preferenceManager2.enableSmartspace.getAdapter()

    PreferenceLayout(
        label = stringResource(id = R.string.smartspace_widget),
        backArrowVisible = !LocalIsExpandedScreen.current && !fromWidget,
        modifier = modifier,
    ) {
        // The terminal readout replaces the stock At a Glance cards, so only the pieces that still
        // apply are exposed here: the on/off switch and a live preview.
        if (fromWidget) {
            SmartspacePreview()
        } else {
            MainSwitchPreference(
                adapter = smartspaceAdapter,
                label = stringResource(R.string.smartspace_widget_toggle_label),
                description = stringResource(id = R.string.smartspace_widget_terminal_description),
            ) {
                SmartspacePreview()
                GlanceTargetsPreferences()
            }
        }
    }
}

/** Which kinds of information the At a Glance panel may show, and how it prioritizes them. */
@Composable
private fun GlanceTargetsPreferences(modifier: Modifier = Modifier) {
    val prefs2 = preferenceManager2()

    GlanceSetupCards(modifier = modifier.padding(top = 8.dp))

    PreferenceGroup(
        heading = stringResource(id = R.string.glance_group_targets),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        SwitchPreference(
            adapter = prefs2.glanceAgenda.getAdapter(),
            label = stringResource(id = R.string.glance_agenda),
            description = stringResource(id = R.string.glance_agenda_desc),
        )
        SwitchPreference(
            adapter = prefs2.glanceWeather.getAdapter(),
            label = stringResource(id = R.string.glance_weather),
            description = stringResource(id = R.string.glance_weather_desc),
        )
        SwitchPreference(
            adapter = prefs2.smartspaceNowPlaying.getAdapter(),
            label = stringResource(id = R.string.glance_media),
            description = stringResource(id = R.string.glance_media_desc),
        )
        SwitchPreference(
            adapter = prefs2.glanceAlarm.getAdapter(),
            label = stringResource(id = R.string.glance_alarm),
            description = stringResource(id = R.string.glance_alarm_desc),
        )
        SwitchPreference(
            adapter = prefs2.glanceBluetooth.getAdapter(),
            label = stringResource(id = R.string.glance_bluetooth),
            description = stringResource(id = R.string.glance_bluetooth_desc),
        )
        SwitchPreference(
            adapter = prefs2.glanceReminders.getAdapter(),
            label = stringResource(id = R.string.glance_reminders),
            description = stringResource(id = R.string.glance_reminders_desc),
        )
        SliderPreference(
            label = stringResource(id = R.string.glance_max_targets),
            adapter = prefs2.smartspacerMaxCount.getAdapter(),
            step = 1,
            valueRange = 1..5,
        )
    }
    PreferenceGroup(
        heading = stringResource(id = R.string.glance_priority_group),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        SwitchPreference(
            adapter = prefs2.glanceStatusLine.getAdapter(),
            label = stringResource(id = R.string.glance_status_line),
            description = stringResource(id = R.string.glance_status_line_desc),
        )
        SwitchPreference(
            adapter = prefs2.glanceAutoPriority.getAdapter(),
            label = stringResource(id = R.string.glance_auto_priority),
            description = stringResource(id = R.string.glance_auto_priority_desc),
        )
        ListPreference(
            adapter = prefs2.glanceLeadMinutes.getAdapter(),
            entries = remember {
                GlanceEngine.LEAD_TIME_OPTIONS.map { minutes ->
                    ListPreferenceEntry(value = minutes, label = { stringResource(R.string.glance_lead_time_value, minutes) })
                }
            },
            label = stringResource(id = R.string.glance_lead_time),
        )
    }
    PreferenceGroup(
        heading = stringResource(id = R.string.glance_group_shortcuts),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        SwitchPreference(
            adapter = prefs2.glanceShortcuts.getAdapter(),
            label = stringResource(id = R.string.glance_shortcuts),
            description = stringResource(id = R.string.glance_shortcuts_desc),
        )
    }
}

/** "Conversas rápidas": which WhatsApp chat shortcuts appear above the dock icons. */
@Composable
fun ChatShortcutsPreferences(modifier: Modifier = Modifier) {
    val prefs2 = preferenceManager2()
    val context = LocalContext.current
    val includeBusiness = prefs2.glanceChatBusiness.getAdapter()
    val chosenAdapter = prefs2.glanceChatKeys.getAdapter()
    var available by remember { mutableStateOf<List<ChatCandidate>>(emptyList()) }
    var hasPermission by remember { mutableStateOf(true) }
    // The list is read again when the screen comes back, so a shortcut just added in WhatsApp shows up.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = context.getSystemService(LauncherApps::class.java)?.hasShortcutHostPermission() == true
        available = WhatsAppChatSource.load(context, includeBusiness.state.value)
    }
    val picked = ChatShortcuts.parseKeys(chosenAdapter.state.value)

    PreferenceGroup(
        heading = stringResource(id = R.string.glance_group_chats),
        modifier = modifier,
    ) {
        SwitchPreference(
            adapter = prefs2.glanceChats.getAdapter(),
            label = stringResource(id = R.string.glance_chats),
            description = stringResource(id = R.string.glance_chats_desc),
        )
        SwitchPreference(
            adapter = prefs2.glanceChatBadge.getAdapter(),
            label = stringResource(id = R.string.glance_chats_badge),
            description = stringResource(id = R.string.glance_chats_badge_desc),
        )
        SwitchPreference(
            checked = includeBusiness.state.value,
            onCheckedChange = {
                includeBusiness.onChange(it)
                available = WhatsAppChatSource.load(context, it)
            },
            label = stringResource(id = R.string.glance_chats_business),
            description = stringResource(id = R.string.glance_chats_business_desc),
        )
    }
    PreferenceGroup(
        heading = stringResource(id = R.string.glance_chats_limit, picked.size, ChatShortcuts.MAX),
        description = stringResource(
            id = if (hasPermission) R.string.glance_chats_howto else R.string.glance_chats_need_default,
        ),
    ) {
        if (available.isEmpty()) {
            Text(
                text = stringResource(id = R.string.glance_chats_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        available.forEach { chat ->
            SwitchPreference(
                checked = chat.key in picked,
                onCheckedChange = {
                    chosenAdapter.onChange(ChatShortcuts.serializeKeys(ChatShortcuts.toggle(picked, chat.key)))
                },
                label = chat.label,
                description = stringResource(
                    id = if (chat.pinned) R.string.glance_chats_pinned else R.string.glance_chats_recent,
                ),
            )
        }
    }
}

@Composable
fun SmartspaceProviderPreference(
    adapter: PreferenceAdapter<SmartspaceMode>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val entries = remember {
        SmartspaceMode.values().map { mode ->
            ListPreferenceEntry(
                value = mode,
                label = { stringResource(id = mode.nameResourceId) },
                enabled = mode.isAvailable(context = context),
            )
        }.toList()
    }

    ListPreference(
        adapter = adapter,
        entries = entries,
        label = stringResource(id = R.string.smartspace_mode_label),
        modifier = modifier,
    )
}

@Composable
fun SmartspacePreview(
    modifier: Modifier = Modifier,
) {
    val themeRes = if (isSelectedThemeDark) R.style.AppTheme_Dark else R.style.AppTheme_DarkText
    val context = LocalContext.current
    val themedContext = remember(themeRes) { ContextThemeWrapper(context, themeRes) }

    PreferenceGroup(
        heading = stringResource(id = R.string.preview_label),
        modifier = modifier,
    ) {
        Surface(
            color = preferenceGroupColor(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            CompositionLocalProvider(LocalContext provides themedContext) {
                AndroidView(
                    factory = {
                        val view = SmartspaceViewContainer(it, previewMode = true)
                        view.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                        view
                    },
                    modifier = Modifier.padding(
                        start = 8.dp,
                        end = 8.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                    ),
                )
            }
        }
    }
}

@Composable
fun SmartspaceDateAndTimePreferences(
    modifier: Modifier = Modifier,
) {
    val preferenceManager2 = preferenceManager2()

    val calendarAdapter = preferenceManager2.smartspaceCalendar.getAdapter()
    val showDateAdapter = preferenceManager2.smartspaceShowDate.getAdapter()
    val showTimeAdapter = preferenceManager2.smartspaceShowTime.getAdapter()

    val calendarHasMinimumContent = !showDateAdapter.state.value || !showTimeAdapter.state.value
    val calendar = calendarAdapter.state.value

    PreferenceGroup(
        heading = stringResource(id = R.string.smartspace_date_and_time),
        modifier = modifier.padding(top = 8.dp),
    ) {
        val supportCustomizationFormat = calendar.formatCustomizationSupport
        ExpandAndShrink(visible = supportCustomizationFormat) {
            SwitchPreference(
                adapter = showDateAdapter,
                label = stringResource(id = R.string.smartspace_date),
                enabled = if (showDateAdapter.state.value) !calendarHasMinimumContent else true,
            )
        }
        ExpandAndShrink(visible = supportCustomizationFormat && showDateAdapter.state.value) {
            SmartspaceCalendarPreference()
        }
        ExpandAndShrink(visible = supportCustomizationFormat) {
            SwitchPreference(
                adapter = showTimeAdapter,
                label = stringResource(id = R.string.smartspace_time),
                enabled = if (showTimeAdapter.state.value) !calendarHasMinimumContent else true,
            )
        }
        ExpandAndShrink(visible = supportCustomizationFormat && showTimeAdapter.state.value) {
            SmartspaceTimeFormatPreference()
        }
    }
}

@Composable
fun SmartspaceTimeFormatPreference(
    modifier: Modifier = Modifier,
) {
    val entries = remember {
        SmartspaceTimeFormat.values().map { format ->
            ListPreferenceEntry(format) { stringResource(id = format.nameResourceId) }
        }
    }

    val adapter = preferenceManager2().smartspaceTimeFormat.getAdapter()

    ListPreference(
        adapter = adapter,
        entries = entries,
        label = stringResource(id = R.string.smartspace_time_format),
        modifier = modifier,
    )
}

@Composable
fun SmartspaceCalendarPreference(
    modifier: Modifier = Modifier,
) {
    val entries = remember {
        SmartspaceCalendar.values().map { calendar ->
            ListPreferenceEntry(calendar) { stringResource(id = calendar.nameResourceId) }
        }
    }

    val adapter = preferenceManager2().smartspaceCalendar.getAdapter()

    ListPreference(
        adapter = adapter,
        entries = entries,
        label = stringResource(id = R.string.smartspace_calendar),
        modifier = modifier,
    )
}

@Composable
fun SmartspacerSettings(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs2 = preferenceManager2()

    Column(modifier) {
        PreferenceGroup(
            heading = stringResource(id = R.string.smartspacer_settings),
        ) {
            SliderPreference(
                label = stringResource(R.string.maximum_number_of_targets),
                adapter = prefs2.smartspacerMaxCount.getAdapter(),
                valueRange = 5..15,
                step = 1,
            )
            ClickablePreference(label = stringResource(R.string.open_smartspacer_settings)) {
                val intent = context.packageManager.getLaunchIntentForPackage(
                    SmartspacerConstants.SMARTSPACER_PACKAGE_NAME,
                )
                context.startActivity(intent)
            }
        }
    }
}

/**
 * Explains, on the screen itself, what is missing for each source and takes the user straight to
 * the system screen that fixes it. Nothing here pops up on its own or turns a source off.
 */
@Composable
private fun GlanceSetupCards(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs2 = preferenceManager2()
    val scope = rememberCoroutineScope()

    val mediaEnabled by prefs2.smartspaceNowPlaying.getAdapter().state
    val serviceEnabled = notificationServiceEnabled()
    val dotsEnabled by remember { notificationDotsEnabled(context) }
        .collectAsStateWithLifecycle(initialValue = true)
    val mediaStep = GlanceSetup.mediaStep(serviceEnabled, dotsEnabled)

    val provider = remember { SmartspaceProvider.INSTANCE.get(context) }

    val bluetoothEnabled by prefs2.glanceBluetooth.getAdapter().state
    var bluetoothGranted by remember { mutableStateOf(BluetoothBatteryProvider.hasPermission(context)) }
    val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        bluetoothGranted = BluetoothBatteryProvider.hasPermission(context)
        provider.dataSources.forEach { source -> source.restart() }
    }

    // The system dialog lives in another activity, so the screen may have been recreated while it
    // was open. Re-read the state every time the user comes back instead of trusting the result.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        bluetoothGranted = BluetoothBatteryProvider.hasPermission(context)
        provider.dataSources.forEach { it.restart() }
    }
    val targets by provider.targets.collectAsStateWithLifecycle(initialValue = emptyList())
    val widgetNeedsSetup = targets.any { it.id == "smartspaceSetup" }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (mediaEnabled && mediaStep != MediaSetupStep.NONE) {
            GlanceSetupCard(
                title = stringResource(R.string.glance_media_setup_title),
                message = stringResource(
                    if (mediaStep == MediaSetupStep.GRANT_ACCESS) {
                        R.string.glance_media_access_message
                    } else {
                        R.string.glance_media_dots_message
                    },
                ),
                action = stringResource(R.string.glance_open_settings),
                onClick = {
                    GlanceSetupIntents.forStep(mediaStep)?.let { context.startActivity(it) }
                },
            )
        }
        if (bluetoothEnabled && !bluetoothGranted) {
            GlanceSetupCard(
                title = stringResource(R.string.glance_bluetooth_setup_title),
                message = stringResource(R.string.glance_bluetooth_setup_message),
                action = stringResource(R.string.glance_allow),
                onClick = { bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
            )
        }
        if (widgetNeedsSetup) {
            GlanceSetupCard(
                title = stringResource(R.string.glance_widget_setup_title),
                message = stringResource(R.string.glance_widget_setup_message),
                action = stringResource(R.string.glance_allow),
                onClick = {
                    val activity = context as? Activity ?: return@GlanceSetupCard
                    scope.launch {
                        provider.dataSources.filterIsInstance<SmartspaceWidgetReader>().forEach {
                            it.startSetup(activity)
                            it.restart()
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun GlanceSetupCard(
    title: String,
    message: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(text = action) }
        }
    }
}
