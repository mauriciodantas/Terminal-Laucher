package app.lawnchair.widgets.lottie

import android.animation.ValueAnimator
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import app.lawnchair.command.AliasStore
import app.lawnchair.command.CustomActionStore
import app.lawnchair.command.PackPreview
import app.lawnchair.command.PackRules
import app.lawnchair.command.ProviderStore
import app.lawnchair.command.WidgetPacks
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.widgets.command.ImportCommandWidgetActivity
import app.lawnchair.widgets.command.WidgetCommandsList
import app.lawnchair.widgets.lottie.LottieWidgetStore.Playback
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opens a widget file the user received and, once confirmed, asks the home screen to add it with
 * everything set: the animation, the command, how it plays, the theme colors and the letters,
 * aliases and API providers the command needs. The command runs on every tap, so it is shown before anything is added.
 */
class ImportWidgetActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = incomingUri(intent)
        lifecycleScope.launch {
            // Command widgets share this file type and this screen's entry points; they have their own.
            val kind = uri?.let { withContext(Dispatchers.IO) { LottieWidgetPacks.kindOf(applicationContext, it) } }
            if (kind == WidgetPacks.KIND_COMMAND) {
                startActivity(
                    Intent(this@ImportWidgetActivity, ImportCommandWidgetActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .setData(uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
                finish()
                return@launch
            }
            val loaded = withContext(Dispatchers.IO) {
                val pack = uri?.let { LottieWidgetPacks.read(applicationContext, it) } ?: return@withContext null
                // Parsed without a cache key, as on a normal import, so a bad file leaves nothing behind.
                val composition = runCatching {
                    LottieCompositionFactory.fromInputStreamSync(applicationContext, pack.animation.inputStream(), null).value
                }.getOrNull() ?: return@withContext null
                pack to composition
            }
            if (loaded == null) {
                Toast.makeText(applicationContext, R.string.lottie_widget_import_invalid, Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            val (pack, composition) = loaded
            val actions = CustomActionStore.load(applicationContext)
            val aliases = AliasStore.load(applicationContext)
            val commands = WidgetPacks.newCommands(pack.commands, actions, aliases)
            val preview = PackRules.preview(actions, aliases, commands, ProviderStore.load(applicationContext))
            setContent {
                LawnchairTheme {
                    ImportDialog(
                        pack = pack,
                        composition = composition,
                        preview = preview,
                        onAdd = { secrets -> add(pack.copy(commands = commands), secrets) },
                        onDismiss = ::finish,
                    )
                }
            }
        }
    }

    private fun add(pack: LottieWidgetPack, secrets: Map<String, String>) {
        val installed = WidgetPacks.install(this, pack.commands, secrets)
        if (installed.merge.skipped > 0) {
            Toast.makeText(this, getString(R.string.lottie_widget_import_skipped, installed.merge.skipped), Toast.LENGTH_LONG).show()
        }
        if (installed.keysPending > 0) {
            Toast.makeText(this, getString(R.string.command_widget_import_keys_pending, installed.keysPending), Toast.LENGTH_LONG).show()
        }
        LottieWidgetStore.setPending(this, pack)
        val manager = AppWidgetManager.getInstance(this)
        val pinned = manager.isRequestPinAppWidgetSupported &&
            manager.requestPinAppWidget(LottieAppWidgetProvider.componentName, null, pinCallback())
        if (!pinned) Toast.makeText(this, R.string.lottie_widget_import_place, Toast.LENGTH_LONG).show()
        finish()
    }

    /** Mutable, so the system can add the id of the widget it placed. */
    private fun pinCallback(): PendingIntent = PendingIntent.getBroadcast(
        this,
        0,
        Intent(this, LottieWidgetPinReceiver::class.java),
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
private fun ImportDialog(
    pack: LottieWidgetPack,
    composition: LottieComposition,
    preview: PackPreview,
    onAdd: (secrets: Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    var secrets by remember { mutableStateOf(emptyMap<String, String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lottie_widget_import_title)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                AndroidView(
                    factory = { context ->
                        LottieAnimationView(context).apply {
                            setComposition(composition)
                            repeatCount = ValueAnimator.INFINITE
                            if (pack.themeColors) {
                                setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = LottieWidgetView.themeFilter(context) })
                            }
                            playAnimation()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                )
                Text(pack.name, style = MaterialTheme.typography.bodySmall, color = dim, modifier = Modifier.padding(top = 8.dp))
                Text(
                    stringResource(R.string.lottie_widget_command),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    pack.command.ifEmpty { stringResource(R.string.lottie_widget_import_no_command) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (pack.command.isEmpty()) null else FontFamily.Monospace,
                )
                Text(
                    stringResource(R.string.lottie_widget_playback) + ": " + stringResource(
                        when (pack.playback) {
                            Playback.LOOP -> R.string.lottie_widget_playback_loop
                            Playback.ONCE_ON_TAP -> R.string.lottie_widget_playback_once
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = dim,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (pack.themeColors) {
                    Text(stringResource(R.string.lottie_widget_theme_colors), style = MaterialTheme.typography.bodySmall, color = dim)
                }
                WidgetCommandsList(preview, secrets, onSecret = { id, secret -> secrets = secrets + (id to secret) })
                if (pack.command.isNotEmpty()) {
                    Text(
                        stringResource(R.string.lottie_widget_import_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(secrets) }) { Text(stringResource(R.string.lottie_widget_import_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cmd_pref_cancel)) } },
    )
}
