package app.lawnchair.command

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.LawnchairTheme
import com.android.launcher3.R

/**
 * Opens a theme file (received from somebody, or written by "ia tema") and shows what it changes
 * before anything does: the colors as they will look, each element it sets and whether the text
 * will read. Applying it also keeps it under its name, so "tema [nome]" brings it back.
 */
class ImportThemeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val spec = incomingUri(intent)?.let { ThemeStore.read(this, it) }
        if (spec == null) {
            Toast.makeText(this, R.string.theme_import_invalid, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val current = ThemeStore.current(this)
        setContent {
            LawnchairTheme {
                ImportDialog(
                    spec = spec,
                    current = current,
                    onApply = { named ->
                        ThemeStore.keep(this, named)
                        val applied = ThemeStore.apply(this, named)
                        Toast.makeText(this, if (applied) R.string.theme_import_applied else R.string.theme_import_unreadable, Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onKeep = { named ->
                        ThemeStore.keep(this, named)
                        Toast.makeText(this, getString(R.string.theme_import_kept, named.name), Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onDismiss = ::finish,
                )
            }
        }
    }

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
    spec: ThemeSpec,
    current: ThemeSpec,
    onApply: (ThemeSpec) -> Unit,
    onKeep: (ThemeSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    val fallbackName = stringResource(R.string.theme_default_name)
    var name by remember { mutableStateOf(spec.name.ifBlank { fallbackName }) }
    val named = spec.copy(name = ThemeSpecs.cleanName(name).ifEmpty { fallbackName })
    val unreadable = ThemeSpecs.unreadable(spec, current)
    val weak = ThemeSpecs.weak(spec, current)
    val dim = MaterialTheme.colorScheme.onSurfaceVariant

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme_import_title)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                ThemeSample(spec, current)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(ThemeSpecs.MAX_NAME) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.theme_import_name)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )
                Text(
                    stringResource(R.string.theme_import_changes),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
                ThemeElement.entries.forEach { element ->
                    element.valueIn(spec)?.let { value ->
                        Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            swatchOf(element, spec)?.let { Swatch(it) }
                            Text(element.desc.lowercase() + ": ", style = MaterialTheme.typography.bodySmall, color = dim)
                            Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                if (spec.followSystem == true) {
                    Text(ThemeChange.SYSTEM.preview.lowercase(), style = MaterialTheme.typography.bodySmall, color = dim, modifier = Modifier.padding(top = 4.dp))
                }
                if (spec.font is ThemeFont.Google) {
                    Text(stringResource(R.string.theme_import_font_download), style = MaterialTheme.typography.bodySmall, color = dim, modifier = Modifier.padding(top = 8.dp))
                }
                when {
                    unreadable -> Warning(stringResource(R.string.theme_import_unreadable), MaterialTheme.colorScheme.error)
                    weak -> Warning(stringResource(R.string.theme_import_weak), MaterialTheme.colorScheme.tertiary)
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onKeep(named) }) { Text(stringResource(R.string.theme_import_keep)) }
                TextButton(onClick = { onApply(named) }, enabled = !unreadable) { Text(stringResource(R.string.theme_import_apply)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cmd_pref_cancel)) } },
    )
}

/** The accent over the background, as the home screen will draw them, with the dock and folder colors under it. */
@Composable
private fun ThemeSample(spec: ThemeSpec, current: ThemeSpec) {
    val accent = argbOf(spec.accent ?: current.accent) ?: MaterialTheme.colorScheme.primary.toArgb()
    val ground = argbOf(spec.background ?: current.background, ThemeSpecs.DEFAULT_BACKGROUND) ?: ThemeSpecs.DEFAULT_BACKGROUND
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(ground), RoundedCornerShape(8.dp))
            .border(1.dp, Color(accent), RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text("> " + spec.name.ifBlank { "terminal" }.lowercase() + "_", color = Color(accent), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
        Text(listOf(BuiltInCommand.OPEN, BuiltInCommand.ALARM, BuiltInCommand.CALC, BuiltInCommand.THEME).joinToString(" · ") { it.word }, color = Color(accent).copy(alpha = 0.7f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
            listOf(spec.dock, spec.folder, spec.outline).mapNotNull { argbOf(it) }.forEach { Swatch(it) }
        }
    }
}

@Composable
private fun Swatch(argb: Int) {
    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .size(14.dp)
            .background(Color(argb), RoundedCornerShape(3.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(3.dp)),
    )
}

@Composable
private fun Warning(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.padding(top = 12.dp))
}

private fun swatchOf(element: ThemeElement, spec: ThemeSpec): Int? = when (element) {
    ThemeElement.ACCENT -> argbOf(spec.accent)
    ThemeElement.BACKGROUND -> argbOf(spec.background)
    ThemeElement.OUTLINE -> argbOf(spec.outline)
    ThemeElement.DOCK -> argbOf(spec.dock)
    ThemeElement.FOLDER -> argbOf(spec.folder)
    else -> null
}

private fun argbOf(color: ThemeColor?, default: Int? = null): Int? = when (color) {
    is ThemeColor.Custom -> color.argb
    ThemeColor.Default -> default
    else -> null
}
