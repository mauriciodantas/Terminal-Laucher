package app.lawnchair.widgets.command

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.lawnchair.command.AliasStore
import app.lawnchair.command.CommandPack
import app.lawnchair.command.CommandPacks
import app.lawnchair.command.CustomActionStore
import app.lawnchair.command.PackPreview
import app.lawnchair.command.PackRules
import app.lawnchair.ui.theme.LawnchairTheme
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opens a command widget file the user received and, once confirmed, asks the home screen to add
 * it with everything set: the command, its schedule and the letters and aliases the command needs.
 * The command runs by itself on the schedule, so it is shown before anything is added. Reached
 * from the widget import screen, which tells the kinds of widget files apart.
 */
class ImportCommandWidgetActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = incomingUri(intent)
        lifecycleScope.launch {
            val pack = withContext(Dispatchers.IO) { uri?.let { CommandWidgetPacks.read(applicationContext, it) } }
            if (pack == null) {
                Toast.makeText(applicationContext, R.string.lottie_widget_import_invalid, Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            val commands = newCommands(pack.commands)
            val preview = PackRules.preview(CustomActionStore.load(applicationContext), AliasStore.load(applicationContext), commands)
            setContent {
                LawnchairTheme {
                    ImportDialog(
                        pack = pack,
                        preview = preview,
                        onAdd = { add(pack.copy(commands = commands)) },
                        onDismiss = ::finish,
                    )
                }
            }
        }
    }

    /** The letters and aliases the user does not have yet; those already the same are left out. */
    private fun newCommands(pack: CommandPack): CommandPack {
        val actions = CustomActionStore.load(this)
        val aliases = AliasStore.load(this)
        return CommandPack(
            pack.actions.filter { it !in actions },
            pack.aliases.filter { it !in aliases },
        )
    }

    private fun add(pack: CommandWidgetPack) {
        // The user's own letters and aliases win a clash; renaming would break the command.
        val merge = CommandPacks.merge(CustomActionStore.load(this), AliasStore.load(this), pack.commands)
        CustomActionStore.save(this, merge.actions)
        AliasStore.save(this, merge.aliases)
        if (merge.skipped > 0) {
            Toast.makeText(this, getString(R.string.lottie_widget_import_skipped, merge.skipped), Toast.LENGTH_LONG).show()
        }
        CommandWidgetStore.setPending(this, pack)
        val manager = AppWidgetManager.getInstance(this)
        val pinned = manager.isRequestPinAppWidgetSupported &&
            manager.requestPinAppWidget(CommandAppWidgetProvider.componentName, null, pinCallback())
        if (!pinned) Toast.makeText(this, R.string.command_widget_import_place, Toast.LENGTH_LONG).show()
        finish()
    }

    /** Mutable, so the system can add the id of the widget it placed. */
    private fun pinCallback(): PendingIntent = PendingIntent.getBroadcast(
        this,
        0,
        Intent(this, CommandWidgetPinReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private fun incomingUri(intent: Intent): Uri? = when (intent.action) {
        Intent.ACTION_VIEW -> intent.data

        Intent.ACTION_SEND ->
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)

        else -> null
    }
}

@Composable
private fun ImportDialog(pack: CommandWidgetPack, preview: PackPreview, onAdd: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lottie_widget_import_title)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.command_widget_command), style = MaterialTheme.typography.titleSmall)
                Text(pack.command, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                Text(
                    stringResource(R.string.command_widget_schedule) + ": " + CommandWidgetView.describe(context, pack.schedule),
                    style = MaterialTheme.typography.bodySmall,
                    color = dim,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (preview.actions.isNotEmpty() || preview.aliases.isNotEmpty()) {
                    Text(
                        stringResource(R.string.lottie_widget_import_includes),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    preview.actions.forEach { CommandRow("${it.item.letter} · ${it.item.label}", it.detail, it.conflict) }
                    preview.aliases.forEach { CommandRow("${it.item.name} → ${it.item.expansion}", null, it.conflict) }
                }
                Text(
                    stringResource(R.string.command_widget_import_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onAdd) { Text(stringResource(R.string.lottie_widget_import_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cmd_pref_cancel)) } },
    )
}

@Composable
private fun CommandRow(title: String, detail: String?, conflict: String?) {
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        detail?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // A clash keeps the user's own: the widget's command then runs theirs.
        conflict?.let {
            Text(
                it + " · " + stringResource(R.string.lottie_widget_import_kept),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
