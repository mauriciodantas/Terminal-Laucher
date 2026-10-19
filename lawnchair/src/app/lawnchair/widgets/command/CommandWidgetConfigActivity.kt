package app.lawnchair.widgets.command

import android.app.TimePickerDialog
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.lawnchair.command.Analysis
import app.lawnchair.command.CommandLineRunner
import app.lawnchair.command.Tone
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.widgets.command.CommandWidgetSchedule.Kind
import com.android.launcher3.R
import java.text.DateFormatSymbols
import java.util.Calendar
import kotlinx.coroutines.delay

/**
 * Configuration of the command widget, when it is added and from "Configurar" afterwards: the
 * command line to run and, optionally, how often to run it again. Nothing changes until "Salvar";
 * canceling a new widget makes the launcher drop it.
 */
class CommandWidgetConfigActivity : ComponentActivity() {

    private val appWidgetId by lazy {
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val command = CommandWidgetStore.command(this, appWidgetId)
        val schedule = CommandWidgetStore.schedule(this, appWidgetId)
        setContent {
            LawnchairTheme {
                ConfigDialog(
                    initialCommand = command,
                    initialSchedule = schedule,
                    onSave = { line, chosen ->
                        CommandWidgetStore.save(applicationContext, appWidgetId, line, chosen)
                        setResult(RESULT_OK, resultIntent())
                        finish()
                    },
                    onDismiss = ::finish,
                )
            }
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@Composable
private fun ConfigDialog(
    initialCommand: String,
    initialSchedule: CommandWidgetSchedule,
    onSave: (String, CommandWidgetSchedule) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var command by remember { mutableStateOf(initialCommand) }
    var schedule by remember { mutableStateOf(initialSchedule) }
    var analysis by remember { mutableStateOf<Analysis?>(null) }
    LaunchedEffect(command) {
        analysis = null
        if (command.isBlank()) return@LaunchedEffect
        // Waits for a pause in typing; looking up apps and contacts on every key is wasteful.
        delay(250)
        analysis = CommandLineRunner.analyze(context, command.trim())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.command_widget)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.command_widget_command)) },
                    placeholder = { Text(stringResource(R.string.command_widget_command_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Preview(command = command, analysis = analysis)
                ScheduleChoice(schedule = schedule, onChange = { schedule = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(command, schedule) }) {
                Text(stringResource(R.string.lottie_widget_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cmd_pref_cancel)) }
        },
    )
}

/** What the command will show, in the command bar's own words, colored like its preview box. */
@Composable
private fun Preview(command: String, analysis: Analysis?) {
    val (text, color) = when {
        command.isBlank() -> stringResource(R.string.command_widget_command_none) to MaterialTheme.colorScheme.onSurfaceVariant
        analysis == null -> "…" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> listOf(analysis.previewTitle, analysis.preview).filter { it.isNotBlank() }.joinToString(" · ") to toneColor(analysis.tone)
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun toneColor(tone: Tone): Color = when (tone) {
    Tone.OK -> MaterialTheme.colorScheme.primary
    Tone.WARN, Tone.ERROR -> MaterialTheme.colorScheme.error
    Tone.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleChoice(schedule: CommandWidgetSchedule, onChange: (CommandWidgetSchedule) -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(stringResource(R.string.command_widget_schedule), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.command_widget_schedule_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Column(modifier = Modifier.selectableGroup()) {
            Kind.entries.forEach { kind ->
                Choice(
                    label = stringResource(
                        when (kind) {
                            Kind.MANUAL -> R.string.command_widget_kind_manual
                            Kind.INTERVAL -> R.string.command_widget_kind_interval
                            Kind.DAILY -> R.string.command_widget_kind_daily
                            Kind.WEEKLY -> R.string.command_widget_kind_weekly
                            Kind.MONTHLY -> R.string.command_widget_kind_monthly
                        },
                    ),
                    selected = kind == schedule.kind,
                    onClick = { onChange(schedule.copy(kind = kind)) },
                )
            }
        }
        if (schedule.kind == Kind.INTERVAL) {
            Column(modifier = Modifier.selectableGroup().padding(start = 24.dp, top = 4.dp)) {
                CommandWidgetStore.INTERVALS.forEach { minutes ->
                    Choice(
                        label = stringResource(R.string.command_widget_every, CommandWidgetView.intervalLabel(context, minutes)),
                        selected = minutes == schedule.intervalMinutes,
                        onClick = { onChange(schedule.copy(intervalMinutes = minutes)) },
                    )
                }
            }
        }
        if (schedule.kind == Kind.WEEKLY) {
            Text(stringResource(R.string.command_widget_weekdays), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            val names = DateFormatSymbols.getInstance().shortWeekdays
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (Calendar.SUNDAY..Calendar.SATURDAY).forEach { day ->
                    val on = day in schedule.weekdays
                    FilterChip(
                        selected = on,
                        // At least one day stays chosen.
                        onClick = { onChange(schedule.copy(weekdays = if (on && schedule.weekdays.size > 1) schedule.weekdays - day else schedule.weekdays + day)) },
                        label = { Text(names[day]) },
                    )
                }
            }
        }
        if (schedule.kind == Kind.MONTHLY) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.command_widget_day_of_month), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.command_widget_day_of_month_short),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onChange(schedule.copy(dayOfMonth = if (schedule.dayOfMonth <= 1) 31 else schedule.dayOfMonth - 1)) }) { Text("−") }
                Text(schedule.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { onChange(schedule.copy(dayOfMonth = if (schedule.dayOfMonth >= 31) 1 else schedule.dayOfMonth + 1)) }) { Text("+") }
            }
        }
        if (schedule.isCalendar) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.command_widget_time), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> onChange(schedule.copy(hour = hour, minute = minute)) },
                            schedule.hour,
                            schedule.minute,
                            DateFormat.is24HourFormat(context),
                        ).show()
                    },
                ) { Text("%02d:%02d".format(schedule.hour, schedule.minute)) }
            }
            Text(
                stringResource(R.string.command_widget_missed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
