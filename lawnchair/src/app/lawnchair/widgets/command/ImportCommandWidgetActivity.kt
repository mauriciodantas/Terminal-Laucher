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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.lawnchair.command.AliasStore
import app.lawnchair.command.CommandWidgetPack
import app.lawnchair.command.CustomActionStore
import app.lawnchair.command.PackPreview
import app.lawnchair.command.PackRules
import app.lawnchair.command.ProviderStore
import app.lawnchair.command.WidgetPacks
import app.lawnchair.ui.theme.LawnchairTheme
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opens a command widget file the user received and, once confirmed, asks the home screen to add
 * it with everything set: the command, its schedule and the letters, aliases and API providers the
 * command needs, with a field for the key of each provider that comes in.
 * The command runs by itself on the schedule, so it is shown before anything is added. Reached
 * from the widget import screen, which tells the kinds of widget files apart.
 */
class ImportCommandWidgetActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = incomingUri(intent)
        lifecycleScope.launch {
            val pack = withContext(Dispatchers.IO) { uri?.let { WidgetPacks.read(applicationContext, it) } }
            if (pack == null) {
                Toast.makeText(applicationContext, R.string.lottie_widget_import_invalid, Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            val actions = CustomActionStore.load(applicationContext)
            val aliases = AliasStore.load(applicationContext)
            val commands = WidgetPacks.newCommands(pack.commands, actions, aliases)
            val preview = PackRules.preview(actions, aliases, commands, ProviderStore.load(applicationContext))
            setContent {
                LawnchairTheme {
                    ImportDialog(
                        pack = pack,
                        preview = preview,
                        onAdd = { secrets -> add(pack.copy(commands = commands), secrets) },
                        onDismiss = ::finish,
                    )
                }
            }
        }
    }

    private fun add(pack: CommandWidgetPack, secrets: Map<String, String>) {
        val installed = WidgetPacks.install(this, pack.commands, secrets)
        if (installed.merge.skipped > 0) {
            Toast.makeText(this, getString(R.string.lottie_widget_import_skipped, installed.merge.skipped), Toast.LENGTH_LONG).show()
        }
        if (installed.keysPending > 0) {
            Toast.makeText(this, getString(R.string.command_widget_import_keys_pending, installed.keysPending), Toast.LENGTH_LONG).show()
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
private fun ImportDialog(pack: CommandWidgetPack, preview: PackPreview, onAdd: (secrets: Map<String, String>) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var secrets by remember { mutableStateOf(emptyMap<String, String>()) }
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
                WidgetCommandsList(preview, secrets, onSecret = { id, secret -> secrets = secrets + (id to secret) })
                Text(
                    stringResource(R.string.command_widget_import_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(secrets) }) { Text(stringResource(R.string.lottie_widget_import_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cmd_pref_cancel)) } },
    )
}
