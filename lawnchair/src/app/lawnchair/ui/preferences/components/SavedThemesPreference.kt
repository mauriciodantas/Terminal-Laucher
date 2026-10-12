package app.lawnchair.ui.preferences.components

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchair.command.ImportThemeActivity
import app.lawnchair.command.ThemeColor
import app.lawnchair.command.ThemeCommand
import app.lawnchair.command.ThemeSpec
import app.lawnchair.command.ThemeSpecs
import app.lawnchair.command.ThemeStore
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import com.android.launcher3.R

/**
 * The themes the user kept by name (imported, written by "ia tema" or saved with "tema salvar"),
 * each applied with a tap and shared or deleted from its row, then one row of actions: keep the
 * current look, open a .tthm file, send the current look. Meant to sit inside the Themes group.
 */
@Composable
fun SavedThemesPreference(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var themes by remember { mutableStateOf(ThemeStore.load(context)) }
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ThemeSpec?>(null) }
    val importTheme = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.startActivity(
                Intent(context, ImportThemeActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }
    }

    themes.forEach { theme ->
        PreferenceTemplate(
            title = { Text(theme.name) },
            description = {
                Text(
                    ThemeCommand.summary(theme),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            startWidget = { ThemeChip(theme) },
            endWidget = {
                Row {
                    IconButton(onClick = { ThemeStore.shareIntent(context, theme)?.let(context::startActivity) }) {
                        Icon(Icons.Rounded.Share, contentDescription = stringResource(id = R.string.theme_share))
                    }
                    IconButton(onClick = { deleting = theme }) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(id = R.string.saved_themes_delete))
                    }
                }
            },
            onClick = {
                val applied = ThemeStore.apply(context, theme)
                Toast.makeText(context, if (applied) R.string.theme_import_applied else R.string.theme_import_unreadable, Toast.LENGTH_SHORT).show()
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            Action(Icons.Rounded.Add, stringResource(id = R.string.saved_themes_save)) { saving = true }
            Action(Icons.Rounded.FileOpen, stringResource(id = R.string.saved_themes_import)) {
                runCatching { importTheme.launch(arrayOf("*/*")) }
            }
            Action(Icons.Rounded.Share, stringResource(id = R.string.saved_themes_share)) {
                ThemeStore.shareIntent(context, ThemeStore.current(context))?.let(context::startActivity)
            }
        }
        if (themes.isEmpty()) {
            Text(
                stringResource(id = R.string.saved_themes_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }

    if (saving) {
        val fallback = stringResource(id = R.string.theme_default_name)
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saving = false },
            title = { Text(stringResource(id = R.string.saved_themes_save_current)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(ThemeSpecs.MAX_NAME) },
                    singleLine = true,
                    placeholder = { Text(fallback) },
                    label = { Text(stringResource(id = R.string.theme_import_name)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val chosen = ThemeSpecs.cleanName(name).ifEmpty { fallback }
                        ThemeStore.saveCurrent(context, chosen)
                        themes = ThemeStore.load(context)
                        saving = false
                        Toast.makeText(context, context.getString(R.string.theme_import_kept, chosen), Toast.LENGTH_SHORT).show()
                    },
                ) { Text(stringResource(id = R.string.theme_import_keep)) }
            },
            dismissButton = { TextButton(onClick = { saving = false }) { Text(stringResource(id = android.R.string.cancel)) } },
        )
    }

    deleting?.let { theme ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(id = R.string.saved_themes_delete_title, theme.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        ThemeStore.remove(context, theme.name)
                        themes = ThemeStore.load(context)
                        deleting = null
                    },
                ) { Text(stringResource(id = R.string.saved_themes_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(id = android.R.string.cancel)) } },
        )
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 6.dp))
    }
}

/** The theme's accent drawn over its background, as a small ">_" on a tile. */
@Composable
private fun ThemeChip(theme: ThemeSpec) {
    val accent = (theme.accent as? ThemeColor.Custom)?.argb
    val ground = (theme.background as? ThemeColor.Custom)?.argb ?: ThemeSpecs.DEFAULT_BACKGROUND
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(end = 4.dp)
            .size(36.dp)
            .background(Color(ground), RoundedCornerShape(6.dp))
            .border(1.dp, accent?.let { Color(it) } ?: MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
    ) {
        Text(">_", color = accent?.let { Color(it) } ?: MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}
