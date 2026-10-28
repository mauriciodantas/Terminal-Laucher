package app.lawnchair.ui.preferences.destinations

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchair.command.LauncherAiPrompts
import app.lawnchair.command.ThemeStore
import app.lawnchair.ui.preferences.components.layout.ClickableIcon
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.util.copyToClipboard
import app.lawnchair.util.getClipboardContent
import com.android.launcher3.R

/** A small, complete theme the guide shows and copies. */
private const val THEME_EXAMPLE_JSON = """{
  "name": "Fireflies",
  "accent": "#ffcc33",
  "background": "#0a0f08",
  "secondary": "#9aa56b",
  "font": { "kind": "google", "family": "Share Tech Mono" },
  "crtEffect": true,
  "crtIntensity": 0.7,
  "customAnimation": {
    "kind": "particles",
    "shape": "circle",
    "color": "#ffee66",
    "count": 40,
    "size": 5,
    "speed": 0.03,
    "angle": 180,
    "sway": 0.05,
    "twinkle": 0.8
  },
  "iconShape": "squircle"
}"""

/**
 * How to make a theme with an AI chat and bring it in: the prompt to copy, share or save, the steps,
 * the ways to import, the format of a theme and the commands that change the look one thing at a time.
 */
@Composable
fun ThemeGuidePreferences(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prompt = LauncherAiPrompts.THEME_MASTER_PROMPT
    val copied = stringResource(R.string.theme_guide_prompt_copied)
    val copyPrompt = { copyToClipboard(context = context, text = prompt, label = "Prompt Terminal Launcher (tema)", toastMessage = copied) }

    val saved = stringResource(R.string.theme_guide_prompt_saved)
    val saveFailed = stringResource(R.string.theme_guide_prompt_save_failed)
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
            .putExtra(Intent.EXTRA_SUBJECT, "Prompt Terminal Launcher (tema)")
            .putExtra(Intent.EXTRA_TEXT, prompt)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    PreferenceLayout(
        label = stringResource(R.string.theme_guide_title),
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
                    text = stringResource(R.string.theme_guide_copy_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { copyPrompt() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.cmd_guide_copy_prompt))
                }
                OutlinedButton(onClick = { importFromClipboard(context) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
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
                    OutlinedButton(onClick = { saveFile.launch("terminal-launcher-tema-prompt.txt") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.theme_guide_save))
                    }
                }
            }
        }

        PreferenceGroup(heading = stringResource(R.string.theme_guide_steps_heading), modifier = Modifier.padding(top = 8.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    R.string.theme_guide_step1,
                    R.string.theme_guide_step2,
                    R.string.theme_guide_step3,
                    R.string.theme_guide_step4,
                ).forEach { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            }
        }

        PreferenceGroup(heading = stringResource(R.string.theme_guide_import_heading), modifier = Modifier.padding(top = 8.dp)) {
            GuideTextCard(text = stringResource(R.string.theme_guide_import_ways))
        }

        PreferenceGroup(heading = stringResource(R.string.theme_guide_format_heading), modifier = Modifier.padding(top = 8.dp)) {
            GuideTextCard(text = stringResource(R.string.theme_guide_format_text))
            JsonCodeCard(json = THEME_EXAMPLE_JSON)
            GuideTextCard(text = stringResource(R.string.theme_guide_format_rules))
        }

        PreferenceGroup(heading = stringResource(R.string.theme_guide_commands_heading), modifier = Modifier.padding(top = 8.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.theme_guide_commands_intro), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.theme_guide_commands),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Opens the import screen with the theme on the clipboard, even when it came with Markdown fences or a sentence around it. */
private fun importFromClipboard(context: Context) {
    val clip = getClipboardContent(context).orEmpty()
    val spec = LauncherAiPrompts.theme(clip)?.first
    val message = when {
        clip.isBlank() -> R.string.theme_guide_clipboard_empty
        spec == null -> R.string.theme_guide_clipboard_invalid
        ThemeStore.review(context, spec) -> return
        else -> R.string.theme_import_invalid
    }
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
