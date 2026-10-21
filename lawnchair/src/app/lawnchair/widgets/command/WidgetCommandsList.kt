package app.lawnchair.widgets.command

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.lawnchair.command.PackPreview
import com.android.launcher3.R

/**
 * What a widget file adds besides the widget, as both widget imports show it: the letters and
 * aliases its command runs and the API providers they call, each provider that comes in with a
 * field for its key. [secrets] holds what was typed, by provider id.
 */
@Composable
fun WidgetCommandsList(
    preview: PackPreview,
    secrets: Map<String, String>,
    onSecret: (id: String, secret: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (preview.actions.isEmpty() && preview.aliases.isEmpty() && preview.providers.isEmpty()) return
    Column(modifier = modifier) {
        WidgetCommandsRows(preview, secrets, onSecret)
    }
}

@Composable
private fun WidgetCommandsRows(preview: PackPreview, secrets: Map<String, String>, onSecret: (id: String, secret: String) -> Unit) {
    Text(
        stringResource(R.string.lottie_widget_import_includes),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 12.dp),
    )
    preview.actions.forEach { CommandRow("${it.item.letter} · ${it.item.label}", it.detail, it.conflict) }
    preview.aliases.forEach { CommandRow("${it.item.name} → ${it.item.expansion}", null, it.conflict) }
    preview.providers.forEach { entry ->
        val provider = entry.item
        Column(modifier = Modifier.padding(top = 6.dp)) {
            Text("${provider.id} · ${provider.name}", style = MaterialTheme.typography.bodyMedium)
            Text(entry.detail, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            entry.caution?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            // Only a provider that comes in asks for its key; the user's own keeps its own.
            if (provider.id !in preview.reused && provider.auth.needsSecret) {
                OutlinedTextField(
                    value = secrets[provider.id].orEmpty(),
                    onValueChange = { onSecret(provider.id, it) },
                    label = { Text(stringResource(R.string.cmd_imp_provider_key)) },
                    supportingText = { Text(stringResource(R.string.cmd_imp_provider_key_hint)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                if (provider.keyHelp.isNotEmpty()) {
                    val context = LocalContext.current
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.keyHelp)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) { Text(stringResource(R.string.cmd_providers_get_key)) }
                }
            }
        }
    }
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
