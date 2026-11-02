package app.lawnchair.ui.preferences.destinations

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lawnchair.command.ImportRoutinesActivity
import app.lawnchair.command.RoutinePacks
import app.lawnchair.command.RoutinePrompts
import app.lawnchair.ui.preferences.components.layout.ClickableIcon
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.util.copyToClipboard
import com.android.launcher3.R

private const val PROMPT_LABEL = "Prompt Terminal Bar (rotinas)"

/**
 * The prompt that teaches any AI chat (ChatGPT, Claude, Gemini) to write routines the bar imports,
 * with the steps, the format and a button that imports the answer from the clipboard.
 */
@Composable
fun RoutineGuidePreferences(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // The commands the AI may use are the ones of this app, in the language of the app.
    val prompt = RoutinePrompts.master()
    val copied = stringResource(R.string.routine_guide_prompt_copied)
    val copyPrompt = { copyToClipboard(context = context, text = prompt, label = PROMPT_LABEL, toastMessage = copied) }

    val saved = stringResource(R.string.routine_guide_prompt_saved)
    val saveFailed = stringResource(R.string.routine_guide_prompt_save_failed)
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(prompt.toByteArray(Charsets.UTF_8)) } != null
            }.getOrDefault(false)
            Toast.makeText(context, if (ok) saved else saveFailed, Toast.LENGTH_SHORT).show()
        }
    }
    val sharePrompt = {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, PROMPT_LABEL)
            .putExtra(Intent.EXTRA_TEXT, prompt)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    PreferenceLayout(
        label = stringResource(R.string.routine_guide_title),
        modifier = modifier,
        actions = { ClickableIcon(imageVector = Icons.Rounded.ContentCopy, onClick = { copyPrompt() }) },
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.theme_guide_copy),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.routine_guide_copy_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { copyPrompt() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.cmd_guide_copy_prompt))
                }
                OutlinedButton(onClick = { importRoutinesFromClipboard(context) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Rounded.ContentPaste, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.theme_guide_import))
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { sharePrompt() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.theme_guide_share))
                    }
                    OutlinedButton(onClick = { saveFile.launch("terminal-rotinas-prompt.txt") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.theme_guide_save))
                    }
                }
            }
        }

        PreferenceGroup(heading = stringResource(R.string.routine_guide_steps_heading), modifier = Modifier.padding(top = 8.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    R.string.routine_guide_step1,
                    R.string.routine_guide_step2,
                    R.string.routine_guide_step3,
                    R.string.routine_guide_step4,
                ).forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            }
        }

        PreferenceGroup(heading = stringResource(R.string.routine_guide_import_heading), modifier = Modifier.padding(top = 8.dp)) {
            Text(
                stringResource(R.string.routine_guide_import_ways),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        PreferenceGroup(heading = stringResource(R.string.routine_guide_format_heading), modifier = Modifier.padding(top = 8.dp)) {
            Text(
                stringResource(R.string.routine_guide_format_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            JsonCodeCard(json = RoutinePrompts.EXAMPLE)
            Text(
                stringResource(R.string.routine_guide_format_rules),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Opens the import screen with the routines on the clipboard, even when they came with Markdown fences around them. */
internal fun importRoutinesFromClipboard(context: Context) {
    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
    val file = RoutinePacks.extract(clip)
    val uri = file?.let { RoutinePacks.stage(context, it) }
    when {
        clip.isBlank() -> Toast.makeText(context, R.string.routine_guide_clipboard_empty, Toast.LENGTH_LONG).show()

        uri == null -> Toast.makeText(context, R.string.routine_guide_clipboard_invalid, Toast.LENGTH_LONG).show()

        else -> context.startActivity(
            Intent(context, ImportRoutinesActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
