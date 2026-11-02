package app.lawnchair.ui.preferences.destinations

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.lawnchair.command.AliasStore
import app.lawnchair.command.Analysis
import app.lawnchair.command.CommandActivity
import app.lawnchair.command.CommandExtras
import app.lawnchair.command.CommandLineRunner
import app.lawnchair.command.CommandScheduler
import app.lawnchair.command.CustomActionStore
import app.lawnchair.command.ImportRoutinesActivity
import app.lawnchair.command.JobStore
import app.lawnchair.command.Jobs
import app.lawnchair.command.ProviderStore
import app.lawnchair.command.RoutinePacks
import app.lawnchair.command.RoutineStore
import app.lawnchair.command.Routines
import app.lawnchair.command.ScheduleAssistant
import app.lawnchair.command.TimeParse
import app.lawnchair.command.Tone
import app.lawnchair.command.TriggerEvent
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.RoutineGuide
import com.android.launcher3.R
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Scheduled commands and rules in one place, with the last result of each (the same list "rotinas"
 * shows in the bar). One is cancelled here, after a confirmation, and a new one is added with the form.
 */
@Composable
fun RoutinesPreferences(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = LocalNavController.current

    // A routines file picked here goes through the same review screen as one received from a chat.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.startActivity(
                Intent(context, ImportRoutinesActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }
    }

    // What runs in the background changes the list while the screen is away: it is read again on return.
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) version++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val rows = remember(version) {
        val runs = RoutineStore.load(context)
        Routines.rows(CommandExtras(jobs = JobStore.load(context), routineRuns = runs), runs)
    }

    var adding by remember { mutableStateOf(false) }
    if (adding) {
        AddRoutineDialog(
            onDismiss = { adding = false },
            onSave = {
                adding = false
                version++
            },
        )
    }

    PreferenceLayout(
        label = stringResource(R.string.routines_title),
        modifier = modifier,
    ) {
        PreferenceGroup {
            ClickablePreference(
                label = stringResource(R.string.routines_add),
                subtitle = stringResource(R.string.routines_add_desc),
                onClick = { adding = true },
            )
        }
        // The same way in as the commands' and the theme's "Create with AI": the guide has the prompt and imports the answer.
        TextButton(
            onClick = { navController.navigate(RoutineGuide) },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.cmd_guide_ai_create))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.routine_guide_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        PreferenceGroup(heading = stringResource(R.string.routines_share_heading)) {
            ClickablePreference(
                label = stringResource(R.string.routines_export),
                subtitle = stringResource(R.string.routines_export_desc),
                onClick = {
                    val jobs = JobStore.load(context)
                    val pack = RoutinePacks.fromJobs(
                        name = context.getString(R.string.routines_title),
                        jobs = jobs,
                        extras = CommandExtras(jobs = jobs),
                        actions = CustomActionStore.load(context),
                        aliases = AliasStore.load(context),
                        providers = ProviderStore.load(context),
                    )
                    val send = pack.takeUnless { it.isEmpty }?.let { RoutinePacks.shareIntent(context, it) }
                    if (send == null) {
                        Toast.makeText(context, R.string.routines_export_empty, Toast.LENGTH_SHORT).show()
                    } else {
                        context.startActivity(send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
            )
            ClickablePreference(
                label = stringResource(R.string.routines_import_file),
                subtitle = stringResource(R.string.routines_import_file_desc),
                onClick = { picker.launch(arrayOf("*/*")) },
            )
            ClickablePreference(
                label = stringResource(R.string.routines_import_clipboard),
                subtitle = stringResource(R.string.routines_import_clipboard_desc),
                onClick = { importRoutinesFromClipboard(context) },
            )
        }
        if (rows.isEmpty()) {
            PreferenceGroup {
                ClickablePreference(
                    label = stringResource(R.string.routines_empty),
                    subtitle = stringResource(R.string.routines_empty_desc),
                    onClick = { CommandActivity.start(context) },
                )
            }
        }
        listOf(
            Routines.Kind.JOB to R.string.routines_group_scheduled,
            Routines.Kind.RULE to R.string.routines_group_rules,
            Routines.Kind.DONE to R.string.routines_group_done,
        ).forEach { (kind, heading) ->
            val group = rows.filter { it.kind == kind }
            if (group.isEmpty()) return@forEach
            PreferenceGroup(heading = stringResource(heading)) {
                group.forEach { row ->
                    val waiting = stringResource(R.string.routines_waiting)
                    when (row.kind) {
                        Routines.Kind.JOB, Routines.Kind.RULE -> ClickablePreference(
                            label = row.title,
                            subtitle = row.result.ifEmpty { waiting },
                            confirmationText = stringResource(R.string.routines_cancel_confirm),
                            onClick = {
                                JobStore.load(context).firstOrNull { it.id == row.jobId }?.let { CommandScheduler.cancel(context, it) }
                                version++
                            },
                        )

                        Routines.Kind.DONE -> ClickablePreference(
                            label = row.title,
                            subtitle = row.result,
                            onClick = { CommandActivity.start(context) },
                        )
                    }
                }
            }
        }
    }
}

private enum class RoutineType { SCHEDULED, EVENT }

private val ONCE_MOMENTS = listOf(
    ScheduleAssistant.Moment.IN to R.string.routines_when_in,
    ScheduleAssistant.Moment.AT to R.string.routines_when_at,
    ScheduleAssistant.Moment.TOMORROW to R.string.routines_when_tomorrow,
    ScheduleAssistant.Moment.DATE to R.string.routines_when_date,
)

private val REPEAT_MOMENTS = listOf(
    ScheduleAssistant.Moment.DAILY to R.string.routines_when_daily,
    ScheduleAssistant.Moment.WEEKLY to R.string.routines_when_weekly,
    ScheduleAssistant.Moment.MONTHLY to R.string.routines_when_monthly,
    ScheduleAssistant.Moment.YEARLY to R.string.routines_when_yearly,
    ScheduleAssistant.Moment.EVERY to R.string.routines_when_every,
)

/** What the field of a moment takes, so the user does not have to guess its format. */
private fun hintOf(moment: ScheduleAssistant.Moment): Int = when (moment) {
    ScheduleAssistant.Moment.IN -> R.string.routines_hint_in
    ScheduleAssistant.Moment.AT, ScheduleAssistant.Moment.TOMORROW, ScheduleAssistant.Moment.DAILY -> R.string.routines_hint_clock
    ScheduleAssistant.Moment.EVERY -> R.string.routines_hint_every
    ScheduleAssistant.Moment.WEEKLY -> R.string.routines_hint_weekly
    ScheduleAssistant.Moment.DATE -> R.string.routines_hint_date
    ScheduleAssistant.Moment.MONTHLY -> R.string.routines_hint_monthly
    ScheduleAssistant.Moment.YEARLY -> R.string.routines_hint_yearly
}

/** A chip that shows it is chosen without relying on a color: a check and a thicker border. */
@Composable
private fun ChoiceChip(selected: Boolean, label: String, radio: Boolean = false, check: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected && check) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.primary.copy(alpha = 0.15f),
            selectedLabelColor = colors.onSurface,
            selectedLeadingIconColor = colors.onSurface,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = colors.onSurface.copy(alpha = 0.7f),
            selectedBorderColor = colors.primary,
            borderWidth = 1.dp,
            selectedBorderWidth = 2.dp,
        ),
        modifier = if (radio) modifier.semantics { role = Role.RadioButton } else modifier,
    )
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp).semantics { heading() },
    )
}

/**
 * A form for the two kinds of routine. It writes the same line the bar takes ("em 30m: nota pão",
 * "quando carregar: wifi off") and runs it the way the bar does, so a routine saved here is exactly
 * one saved there. The chips, the clock and the calendar only write into the "When" field, which
 * stays editable; the sentence under the fields says what the bar understood, and Save waits for it.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AddRoutineDialog(onDismiss: () -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val today = remember { LocalDate.now() }
    val extras = remember { CommandExtras() }

    var type by remember { mutableStateOf(RoutineType.SCHEDULED) }
    var moment by remember { mutableStateOf(ScheduleAssistant.Moment.IN) }
    var time by remember { mutableStateOf(ScheduleAssistant.defaultFor(ScheduleAssistant.Moment.IN, null, null, today)) }
    var event by remember { mutableStateOf(TriggerEvent.CHARGING) }
    var command by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val scheduled = type == RoutineType.SCHEDULED
    val timeProblem = if (scheduled) ScheduleAssistant.problem(moment, time, extras) else null
    val job = if (scheduled && timeProblem == null) ScheduleAssistant.job(moment, time, extras) else null

    val line = when (type) {
        RoutineType.SCHEDULED -> ScheduleAssistant.timeLine(moment, time, command)
        RoutineType.EVENT -> ScheduleAssistant.eventLine(event, command)
    }
    var analysis by remember { mutableStateOf<Analysis?>(null) }
    LaunchedEffect(line) {
        analysis = if (command.isBlank()) null else CommandLineRunner.analyze(context, line)
    }
    val valid = timeProblem == null && analysis?.let { it.action != null && it.tone != Tone.ERROR } == true

    val saved = stringResource(R.string.routines_saved)
    val failed = stringResource(R.string.routines_save_failed)
    val save: () -> Unit = {
        scope.launch {
            val result = CommandLineRunner.run(context, line)
            Toast.makeText(context, if (result.ran) saved else failed, Toast.LENGTH_SHORT).show()
            if (result.ran) onSave()
        }
    }
    // A scheduled command shows its result in a notification: the bar asks for the permission before scheduling.
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { save() }
    val needsNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    if (showDatePicker) {
        val yearly = moment == ScheduleAssistant.Moment.YEARLY
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (
                (if (yearly) ScheduleAssistant.monthDayIn(time, today.year) else ScheduleAssistant.dateIn(time)) ?: today.plusDays(1)
                ).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val date = java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        time = if (yearly) ScheduleAssistant.withMonthDay(time, date) else ScheduleAssistant.withDate(time, date)
                    }
                    showDatePicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(android.R.string.cancel)) } },
        ) { DatePicker(state = state) }
    }

    if (showTimePicker) {
        val current = ScheduleAssistant.clockIn(time)?.let { TimeParse.clock(it) }
        val state = rememberTimePickerState(
            initialHour = current?.hour ?: 8,
            initialMinute = current?.minute ?: 0,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    time = ScheduleAssistant.withClock(time, "%02d:%02d".format(state.hour, state.minute))
                    showTimePicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }

    // Its own dialog, so the window can follow the keyboard: the form shrinks and scrolls, the sentence and the buttons stay in view.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        stringResource(R.string.routines_add),
                        style = MaterialTheme.typography.headlineSmall,
                        color = AlertDialogDefaults.titleContentColor,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                // The form scrolls; the sentence under it does not, so it stays above the buttons.
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            RoutineType.SCHEDULED to R.string.routines_type_scheduled,
                            RoutineType.EVENT to R.string.routines_type_event,
                        ).forEach { (kind, label) ->
                            ChoiceChip(selected = type == kind, label = stringResource(label), radio = true, onClick = { type = kind })
                        }
                    }
                    when (type) {
                        RoutineType.SCHEDULED -> {
                            val choose: (ScheduleAssistant.Moment) -> Unit = { kind ->
                                time = ScheduleAssistant.switchTo(moment, kind, time, today)
                                moment = kind
                            }
                            GroupTitle(stringResource(R.string.routines_group_once))
                            FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ONCE_MOMENTS.forEach { (kind, label) ->
                                    ChoiceChip(selected = moment == kind, label = stringResource(label), radio = true, onClick = { choose(kind) })
                                }
                            }
                            GroupTitle(stringResource(R.string.routines_group_repeats))
                            FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                REPEAT_MOMENTS.forEach { (kind, label) ->
                                    ChoiceChip(selected = moment == kind, label = stringResource(label), radio = true, onClick = { choose(kind) })
                                }
                            }

                            // The structured controls first, then the field they write into.
                            if (moment == ScheduleAssistant.Moment.WEEKLY) {
                                val picked = ScheduleAssistant.daysIn(time)
                                val workdays = DayOfWeek.entries.filter { it <= DayOfWeek.FRIDAY }.toSet()
                                val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
                                GroupTitle(stringResource(R.string.routines_group_days))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ChoiceChip(selected = picked == workdays, label = stringResource(R.string.routines_days_workdays)) {
                                        time = ScheduleAssistant.withDays(time, if (picked == workdays) emptySet() else workdays)
                                    }
                                    ChoiceChip(selected = picked == weekend, label = stringResource(R.string.routines_days_weekend)) {
                                        time = ScheduleAssistant.withDays(time, if (picked == weekend) emptySet() else weekend)
                                    }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DayOfWeek.entries.forEach { day ->
                                        ChoiceChip(
                                            selected = day in picked,
                                            label = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                        ) { time = ScheduleAssistant.withDays(time, if (day in picked) picked - day else picked + day) }
                                    }
                                }
                            }
                            if (moment == ScheduleAssistant.Moment.MONTHLY) {
                                val (days, last) = ScheduleAssistant.monthDaysIn(time)
                                GroupTitle(stringResource(R.string.routines_group_month))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    (1..31).forEach { day ->
                                        ChoiceChip(selected = day in days, label = day.toString(), check = false, modifier = Modifier.width(60.dp)) {
                                            time = ScheduleAssistant.withMonthDays(time, if (day in days) days - day else days + day, last)
                                        }
                                    }
                                    ChoiceChip(selected = last, label = stringResource(R.string.routines_day_last)) {
                                        time = ScheduleAssistant.withMonthDays(time, days, !last)
                                    }
                                }
                            }
                            if (moment == ScheduleAssistant.Moment.DATE || moment == ScheduleAssistant.Moment.YEARLY) {
                                OutlinedButton(onClick = { showDatePicker = true }) { Text(stringResource(R.string.routines_pick_date)) }
                            }
                            if (!ScheduleAssistant.isDuration(moment)) {
                                OutlinedButton(onClick = { showTimePicker = true }) { Text(stringResource(R.string.routines_pick_time)) }
                            }

                            OutlinedTextField(
                                value = time,
                                onValueChange = { time = it },
                                label = { Text(stringResource(R.string.routines_field_when)) },
                                supportingText = { Text(timeProblem ?: stringResource(hintOf(moment))) },
                                isError = timeProblem != null,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        RoutineType.EVENT -> FlowRow(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TriggerEvent.entries.forEach { e ->
                                ChoiceChip(selected = event == e, label = e.word, radio = true, onClick = { event = e })
                            }
                        }
                    }
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text(stringResource(R.string.routines_field_command)) },
                        placeholder = { Text(stringResource(R.string.routines_command_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // What the bar understood, in words and always in view; a screen reader announces it when it changes.
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    timeProblem?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    job?.let {
                        Text(Jobs.sentence(it, extras), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                    analysis?.let { a ->
                        val text = if (scheduled) a.preview else listOf(a.previewTitle, a.preview).filter { it.isNotBlank() }.joinToString("\n")
                        Text(
                            text,
                            color = if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        stringResource(if (command.isBlank()) R.string.routines_command_needed else R.string.routines_background),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                    }
                    Row(
                        modifier = Modifier.align(Alignment.End).padding(top = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                        TextButton(
                            enabled = valid,
                            onClick = { if (needsNotifications) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else save() },
                        ) { Text(stringResource(R.string.routines_save)) }
                    }
                }
            }
        }
    }
}
