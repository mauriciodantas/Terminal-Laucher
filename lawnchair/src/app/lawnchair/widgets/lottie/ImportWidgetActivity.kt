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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import app.lawnchair.command.AliasStore
import app.lawnchair.command.CommandPack
import app.lawnchair.command.CommandPacks
import app.lawnchair.command.CustomActionStore
import app.lawnchair.command.PackPreview
import app.lawnchair.command.PackRules
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.widgets.command.CommandWidgetPacks
import app.lawnchair.widgets.command.ImportCommandWidgetActivity
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
 * everything set: the animation, the command, how it plays, the theme colors and the letters and
 * aliases the command needs. The command runs on every tap, so it is shown before anything is added.
 */
class ImportWidgetActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = incomingUri(intent)
        lifecycleScope.launch {
            // Command widgets share this file type and this screen's entry points; they have their own.
            val kind = uri?.let { withContext(Dispatchers.IO) { LottieWidgetPacks.kindOf(applicationContext, it) } }
            if (kind == CommandWidgetPacks.KIND) {
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
            val commands = newCommands(pack.commands)
            val preview = PackRules.preview(CustomActionStore.load(applicationContext), AliasStore.load(applicationContext), commands)
            setContent {
                LawnchairTheme {
                    ImportDialog(
                        pack = pack,
                        composition = composition,
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

    private fun add(pack: LottieWidgetPack) {
        // The user's own letters and aliases win a clash; renaming would break the command.
        val merge = CommandPacks.merge(CustomActionStore.load(this), AliasStore.load(this), pack.commands)
        CustomActionStore.save(this, merge.actions)
        AliasStore.save(this, merge.aliases)
        if (merge.skipped > 0) {
            Toast.makeText(this, getString(R.string.lottie_widget_import_skipped, merge.skipped), Toast.LENGTH_LONG).show()
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
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
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
                if (preview.actions.isNotEmpty() || preview.aliases.isNotEmpty()) {
                    Text(
                        stringResource(R.string.lottie_widget_import_includes),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    preview.actions.forEach { CommandRow("${it.item.letter} · ${it.item.label}", it.detail, it.conflict) }
                    preview.aliases.forEach { CommandRow("${it.item.name} → ${it.item.expansion}", null, it.conflict) }
                }
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
