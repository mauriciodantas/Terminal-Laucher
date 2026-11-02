package app.lawnchair.ui.preferences.destinations

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.lawnchair.command.Analysis
import app.lawnchair.command.CommandActivity
import app.lawnchair.command.CommandExtras
import app.lawnchair.command.CommandLineRunner
import app.lawnchair.command.CommandScheduler
import app.lawnchair.command.JobStore
import app.lawnchair.command.RoutineStore
import app.lawnchair.command.Routines
import app.lawnchair.command.ScheduleAssistant
import app.lawnchair.command.Tone
import app.lawnchair.command.TriggerEvent
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R
import kotlinx.coroutines.launch

/**
 * Scheduled commands and rules in one place, with the last result of each (the same list "rotinas"
 * shows in the bar). One is cancelled here, after a confirmation, and a new one is added with the form.
 */
@Composable
fun RoutinesPreferences(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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

/**
 * A form for the two kinds of routine. It writes the same line the bar takes ("em 30m: nota pão",
 * "quando carregar: wifi off") and runs it the way the bar does, so a
 * routine saved here is exactly one saved there, and the preview under the fields is the bar's own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddRoutineDialog(onDismiss: () -> Unit, onSave: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var type by remember { mutableStateOf(RoutineType.SCHEDULED) }
    var moment by remember { mutableStateOf(ScheduleAssistant.Moment.IN) }
    var time by remember { mutableStateOf("30m") }
    var event by remember { mutableStateOf(TriggerEvent.CHARGING) }
    var command by remember { mutableStateOf("") }

    val line = when (type) {
        RoutineType.SCHEDULED -> ScheduleAssistant.timeLine(moment, time, command)
        RoutineType.EVENT -> ScheduleAssistant.eventLine(event, command)
    }
    var analysis by remember { mutableStateOf<Analysis?>(null) }
    LaunchedEffect(line) {
        analysis = if (command.isBlank()) null else CommandLineRunner.analyze(context, line)
    }
    val valid = analysis?.let { it.action != null && it.tone != Tone.ERROR } == true

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routines_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        RoutineType.SCHEDULED to R.string.routines_type_scheduled,
                        RoutineType.EVENT to R.string.routines_type_event,
                    ).forEach { (kind, label) ->
                        FilterChip(selected = type == kind, onClick = { type = kind }, label = { Text(stringResource(label)) })
                    }
                }
                when (type) {
                    RoutineType.SCHEDULED -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                ScheduleAssistant.Moment.IN to (R.string.routines_when_in to "30m"),
                                ScheduleAssistant.Moment.AT to (R.string.routines_when_at to "22h"),
                                ScheduleAssistant.Moment.TOMORROW to (R.string.routines_when_tomorrow to "7h"),
                            ).forEach { (kind, pair) ->
                                FilterChip(
                                    selected = moment == kind,
                                    onClick = {
                                        moment = kind
                                        time = pair.second
                                    },
                                    label = { Text(stringResource(pair.first)) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = time,
                            onValueChange = { time = it },
                            label = { Text(stringResource(R.string.routines_field_time)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    RoutineType.EVENT -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TriggerEvent.entries.forEach { e ->
                            FilterChip(selected = event == e, onClick = { event = e }, label = { Text(e.word) })
                        }
                    }
                }
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text(stringResource(R.string.routines_field_command)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                // The bar's own reading of the line, so a mistake shows before it is saved.
                analysis?.let { a ->
                    Text(
                        listOf(a.previewTitle, a.preview).filter { it.isNotBlank() }.joinToString("\n"),
                        color = if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { if (needsNotifications) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else save() },
            ) { Text(stringResource(R.string.routines_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
