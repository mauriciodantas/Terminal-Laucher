package app.lawnchair.ui.preferences.components

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.colorpreference.ChipRow
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.navigation.ThemeGuide
import com.android.launcher3.R

/**
 * The themes the user kept by name (imported, written by "ia tema" or saved with "tema salvar"), as a
 * row of chips like the ready-made looks, with the one in use marked; a tap applies it. Sharing and
 * deleting live in a sheet opened by "Manage", so the page does not grow with every theme. Under the
 * chips, one row of actions: keep the current look, open a .tthm file, make a theme with an AI chat.
 * Meant to sit inside the Themes group.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedThemesPreference(modifier: Modifier = Modifier, onBeforeApply: () -> Unit = {}) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val prefs2 = preferenceManager2()
    // Read so the "in use" mark follows the colors as they change.
    val accent = prefs2.accentColor.getAdapter().state.value
    val background = prefs2.launcherBackgroundColor.getAdapter().state.value
    val secondary = prefs2.secondaryColor.getAdapter().state.value
    var themes by remember { mutableStateOf(ThemeStore.load(context)) }
    var saving by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
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
    fun inUse(theme: ThemeSpec) = (theme.accent != null || theme.background != null) &&
        theme.accent.matches(accent) && theme.background.matches(background) && theme.secondary.matches(secondary)

    Column(modifier = modifier.fillMaxWidth()) {
        if (themes.isEmpty()) {
            Text(
                stringResource(id = R.string.saved_themes_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(start = 16.dp, end = 8.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.saved_themes_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = { managing = true }) {
                    Text(stringResource(id = R.string.saved_themes_manage))
                }
            }
            ChipRow {
                themes.forEach { theme ->
                    SavedThemeChip(
                        theme = theme,
                        selected = inUse(theme),
                        onClick = {
                            onBeforeApply()
                            val applied = ThemeStore.apply(context, theme)
                            Toast.makeText(context, if (applied) R.string.theme_import_applied else R.string.theme_import_unreadable, Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Action(Icons.Rounded.Add, stringResource(id = R.string.saved_themes_save)) { saving = true }
            Action(Icons.Rounded.FileOpen, stringResource(id = R.string.saved_themes_import)) {
                runCatching { importTheme.launch(arrayOf("*/*")) }
            }
            Action(Icons.Rounded.AutoAwesome, stringResource(id = R.string.saved_themes_ai)) { navController.navigate(ThemeGuide) }
        }
    }

    if (managing) {
        ModalBottomSheet(onDismissRequest = { managing = false }) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 8.dp),
                ) {
                    Text(stringResource(id = R.string.saved_themes_title), style = MaterialTheme.typography.titleMedium)
                    Action(Icons.Rounded.Share, stringResource(id = R.string.saved_themes_share_current)) {
                        ThemeStore.shareIntent(context, ThemeStore.current(context))?.let(context::startActivity)
                    }
                }
                LazyColumn {
                    items(themes, key = { it.name }) { theme ->
                        PreferenceTemplate(
                            title = { Text(if (inUse(theme)) "${theme.name} · ${stringResource(id = R.string.saved_themes_in_use)}" else theme.name) },
                            description = {
                                Text(
                                    ThemeCommand.summary(theme),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            endWidget = {
                                Row {
                                    IconButton(onClick = { ThemeStore.shareIntent(context, theme)?.let(context::startActivity) }) {
                                        Icon(Icons.Rounded.Share, contentDescription = "${stringResource(id = R.string.theme_share)}: ${theme.name}")
                                    }
                                    IconButton(onClick = { deleting = theme }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "${stringResource(id = R.string.saved_themes_delete)}: ${theme.name}")
                                    }
                                }
                            },
                            onClick = {
                                onBeforeApply()
                                val applied = ThemeStore.apply(context, theme)
                                Toast.makeText(context, if (applied) R.string.theme_import_applied else R.string.theme_import_unreadable, Toast.LENGTH_SHORT).show()
                                managing = false
                            },
                        )
                    }
                }
            }
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
                        if (themes.isEmpty()) managing = false
                    },
                ) { Text(stringResource(id = R.string.saved_themes_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(id = android.R.string.cancel)) } },
        )
    }
}

/** True when the color a theme sets is the one chosen now; a color the theme leaves unset does not count against it. */
private fun ThemeColor?.matches(option: ColorOption): Boolean = when (this) {
    null -> true
    is ThemeColor.Custom -> option is ColorOption.CustomColor && (option.color or OPAQUE) == argb
    ThemeColor.System -> option == ColorOption.SystemAccent
    ThemeColor.Wallpaper -> option == ColorOption.WallpaperPrimary
    ThemeColor.Default -> option !is ColorOption.CustomColor && option != ColorOption.SystemAccent && option != ColorOption.WallpaperPrimary
}

private const val OPAQUE = 0xFF000000.toInt()

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 6.dp))
    }
}

/** One saved theme: its accent prompt over its background (and the secondary color beside it), with its name under. */
@Composable
private fun SavedThemeChip(theme: ThemeSpec, selected: Boolean, onClick: () -> Unit) {
    val accent = (theme.accent as? ThemeColor.Custom)?.argb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val ground = Color((theme.background as? ThemeColor.Custom)?.argb ?: ThemeSpecs.DEFAULT_BACKGROUND)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                // The ">_" is decoration: the name under it is what a screen reader says.
                .clearAndSetSemantics {}
                .size(width = 64.dp, height = 40.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    border = BorderStroke(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    shape = RoundedCornerShape(10.dp),
                )
                .background(ground),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = ">_", color = accent, style = MaterialTheme.typography.titleMedium)
                // A three-color theme shows its secondary color beside the prompt.
                (theme.secondary as? ThemeColor.Custom)?.let { Text(text = " ~", color = Color(it.argb), style = MaterialTheme.typography.titleMedium) }
            }
        }
        Text(
            text = theme.name,
            style = MaterialTheme.typography.labelSmall,
            // Set, not inherited: the sheet and the page do not share a surface color.
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 88.dp)
                .padding(top = 4.dp),
        )
    }
}
