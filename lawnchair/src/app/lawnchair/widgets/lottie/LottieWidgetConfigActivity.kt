package app.lawnchair.widgets.lottie

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.lifecycle.lifecycleScope
import app.lawnchair.command.Analysis
import app.lawnchair.command.CommandLine
import app.lawnchair.command.Tone
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.widgets.lottie.LottieWidgetStore.Playback
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Configuration of the Lottie widget, when it is added and from "Configurar" afterwards: the
 * animation to play, how it plays, whether it takes the theme colors and, optionally, a command line
 * to run on a tap. A new widget opens the file picker right away. Nothing changes until "Salvar"; canceling a new widget makes the launcher drop it.
 */
class LottieWidgetConfigActivity : ComponentActivity() {

    private val appWidgetId by lazy {
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    }

    /** Name of the animation the widget will play: the one picked here, or the current one. */
    private var animationName by mutableStateOf<String?>(null)
    private var showDialog by mutableStateOf(false)

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            stage(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        // A widget the home screen adds right after a widget file was imported comes ready.
        if (LottieWidgetStore.applyPending(this, appWidgetId)) {
            setResult(RESULT_OK, resultIntent())
            finish()
            return
        }
        val isCustom = LottieWidgetStore.hasCustomAnimation(this, appWidgetId)
        animationName = if (isCustom) LottieWidgetStore.animationName(this, appWidgetId) else null
        val initialCommand = LottieWidgetStore.command(this, appWidgetId)
        val initialPlayback = LottieWidgetStore.playback(this, appWidgetId)
        val initialThemeColors = LottieWidgetStore.themeColors(this, appWidgetId)
        val initialShowResult = LottieWidgetStore.showResult(this, appWidgetId)
        val initialResultDuration = LottieWidgetStore.resultDurationSeconds(this, appWidgetId)
        showDialog = true

        setContent {
            LawnchairTheme {
                if (showDialog) {
                    ConfigDialog(
                        animationName = animationName,
                        initialCommand = initialCommand,
                        initialPlayback = initialPlayback,
                        initialThemeColors = initialThemeColors,
                        initialShowResult = initialShowResult,
                        initialResultDuration = initialResultDuration,
                        onPick = ::pickAnimation,
                        onSave = ::save,
                        onShare = ::share,
                        onDismiss = ::cancel,
                    )
                }
            }
        }
    }

    // .lottie files usually come without a useful MIME type, so any file is offered and validated.
    private fun pickAnimation() = pickFile.launch(arrayOf("*/*"))

    private fun stage(uri: Uri) {
        lifecycleScope.launch {
            val name = withContext(Dispatchers.IO) { LottieWidgetStore.stage(applicationContext, appWidgetId, uri) }
            if (name != null) {
                animationName = name
            } else {
                Toast.makeText(applicationContext, R.string.lottie_widget_invalid, Toast.LENGTH_LONG).show()
            }
            showDialog = true
        }
    }

    private fun save(command: String, playback: Playback, themeColors: Boolean, showResult: Boolean, resultDuration: Int) {
        if (LottieWidgetStore.save(applicationContext, appWidgetId, animationName, command, playback, themeColors, showResult, resultDuration)) {
            setResult(RESULT_OK, resultIntent())
        }
        finish()
    }

    private fun share(command: String, playback: Playback, themeColors: Boolean, showResult: Boolean, resultDuration: Int) {
        val file = LottieWidgetStore.shownFile(this, appWidgetId)
        val name = animationName ?: file?.name ?: getString(R.string.lottie_widget_default_animation)
        lifecycleScope.launch {
            val intent = withContext(Dispatchers.IO) {
                LottieWidgetPacks.shareIntent(applicationContext, file, name, command, playback, themeColors, showResult, resultDuration)
            }
            if (intent != null) startActivity(intent) else Toast.makeText(applicationContext, R.string.lottie_widget_share_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun cancel() {
        LottieWidgetStore.discardStaged(applicationContext, appWidgetId)
        finish()
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@Composable
private fun ConfigDialog(
    animationName: String?,
    initialCommand: String,
    initialPlayback: Playback,
    initialThemeColors: Boolean,
    initialShowResult: Boolean,
    initialResultDuration: Int,
    onPick: () -> Unit,
    onSave: (String, Playback, Boolean, Boolean, Int) -> Unit,
    onShare: (String, Playback, Boolean, Boolean, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var command by remember { mutableStateOf(initialCommand) }
    var playback by remember { mutableStateOf(initialPlayback) }
    var themeColors by remember { mutableStateOf(initialThemeColors) }
    var showResult by remember { mutableStateOf(initialShowResult) }
    var resultDuration by remember { mutableStateOf(initialResultDuration) }
    var analysis by remember { mutableStateOf<Analysis?>(null) }
    LaunchedEffect(command) {
        analysis = null
        if (command.isBlank()) return@LaunchedEffect
        // Waits for a pause in typing; looking up apps and contacts on every key is wasteful.
        delay(250)
        analysis = CommandLine.analyze(context, command.trim())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lottie_widget)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.lottie_widget_animation), style = MaterialTheme.typography.titleSmall)
                        Text(
                            animationName ?: stringResource(R.string.lottie_widget_default_animation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onPick) {
                        Text(
                            stringResource(
                                if (animationName == null) R.string.lottie_widget_pick else R.string.lottie_widget_change,
                            ),
                        )
                    }
                }
                PlaybackChoice(selected = playback, onSelect = { playback = it })
                ThemeColorsSwitch(checked = themeColors, onChange = { themeColors = it })
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.lottie_widget_command)) },
                    placeholder = { Text(stringResource(R.string.lottie_widget_command_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
                CommandPreview(command = command, playback = playback, analysis = analysis)
                ShowResultSwitch(checked = showResult, onChange = { showResult = it })
                if (showResult) {
                    ResultDurationChoice(selected = resultDuration, onSelect = { resultDuration = it })
                }
                TextButton(
                    onClick = { onShare(command, playback, themeColors, showResult, resultDuration) },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.lottie_widget_share))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(command, playback, themeColors, showResult, resultDuration) }) {
                Text(stringResource(R.string.lottie_widget_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cmd_pref_cancel)) }
        },
    )
}

@Composable
private fun PlaybackChoice(selected: Playback, onSelect: (Playback) -> Unit) {
    Column(
        modifier = Modifier
            .padding(top = 16.dp)
            .selectableGroup(),
    ) {
        Text(stringResource(R.string.lottie_widget_playback), style = MaterialTheme.typography.titleSmall)
        Playback.entries.forEach { mode ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = mode == selected, role = Role.RadioButton, onClick = { onSelect(mode) }),
            ) {
                RadioButton(selected = mode == selected, onClick = null, modifier = Modifier.padding(8.dp))
                Text(
                    stringResource(
                        when (mode) {
                            Playback.LOOP -> R.string.lottie_widget_playback_loop
                            Playback.ONCE_ON_TAP -> R.string.lottie_widget_playback_once
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ThemeColorsSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.lottie_widget_theme_colors), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.lottie_widget_theme_colors_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 8.dp))
    }
}

/** What the tap will do, in the command bar's own words, colored like its preview box. */
@Composable
private fun CommandPreview(command: String, playback: Playback, analysis: Analysis?) {
    val (text, color) = when {
        command.isBlank() -> stringResource(
            when (playback) {
                Playback.LOOP -> R.string.lottie_widget_command_none
                Playback.ONCE_ON_TAP -> R.string.lottie_widget_command_none_once
            },
        ) to MaterialTheme.colorScheme.onSurfaceVariant

        analysis == null -> "…" to MaterialTheme.colorScheme.onSurfaceVariant

        else -> listOf(analysis.previewTitle, analysis.preview).filter { it.isNotBlank() }.joinToString(" · ") to
            toneColor(analysis.tone)
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun toneColor(tone: Tone): Color = when (tone) {
    Tone.OK -> MaterialTheme.colorScheme.primary
    Tone.WARN, Tone.ERROR -> MaterialTheme.colorScheme.error
    Tone.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun ShowResultSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.lottie_widget_show_result), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.lottie_widget_show_result_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ResultDurationChoice(selected: Int, onSelect: (Int) -> Unit) {
    val options = listOf(
        15 to R.string.lottie_widget_duration_15s,
        30 to R.string.lottie_widget_duration_30s,
        60 to R.string.lottie_widget_duration_1m,
        120 to R.string.lottie_widget_duration_2m,
        300 to R.string.lottie_widget_duration_5m,
    )
    Column(
        modifier = Modifier
            .padding(top = 16.dp)
            .selectableGroup(),
    ) {
        Text(stringResource(R.string.lottie_widget_result_duration), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.lottie_widget_result_duration_summary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        options.forEach { (seconds, labelRes) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = seconds == selected, role = Role.RadioButton, onClick = { onSelect(seconds) }),
            ) {
                RadioButton(selected = seconds == selected, onClick = null, modifier = Modifier.padding(8.dp))
                Text(
                    stringResource(labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

