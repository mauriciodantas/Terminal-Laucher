package app.lawnchair.command

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.LawnchairTheme
import com.android.launcher3.R

/**
 * Opens a command pack the user received (from a chat, a file manager, the share sheet) and asks
 * before adding it. A pack can start other apps once its letters are typed, so nothing is imported
 * without showing what is inside: where each action goes, what clashes with what the user has, and
 * a checkbox per entry.
 */
class ImportCommandsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pack = incomingUri(intent)?.let { CommandPacks.read(this, it) }
        if (pack == null) {
            Toast.makeText(this, R.string.cmd_imp_invalid, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val current = CustomActionStore.load(this) to AliasStore.load(this)
        setContent {
            LawnchairTheme {
                ImportDialog(
                    pack = pack,
                    preview = PackRules.preview(current.first, current.second, pack),
                    onImport = { chosen -> importPack(chosen) },
                    onDismiss = { finish() },
                )
            }
        }
    }

    /** Adds what was ticked. An entry that clashes comes in under its free suggested name. */
    private fun importPack(chosen: CommandPack) {
        val merge = CommandPacks.merge(CustomActionStore.load(this), AliasStore.load(this), chosen, rename = true)
        CustomActionStore.save(this, merge.actions)
        AliasStore.save(this, merge.aliases)
        val text = getString(R.string.cmd_imp_done, merge.addedActions, merge.addedAliases) +
            if (merge.skipped > 0) getString(R.string.cmd_imp_skipped, merge.skipped) else ""
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        finish()
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
    pack: CommandPack,
    preview: PackPreview,
    onImport: (CommandPack) -> Unit,
    onDismiss: () -> Unit,
) {
    // What fits starts ticked; a clash starts unticked and, when ticked, comes in under another name.
    var actionsOn by remember { mutableStateOf(preview.actions.indices.filter { preview.actions[it].conflict == null }.toSet()) }
    var aliasesOn by remember { mutableStateOf(preview.aliases.indices.filter { preview.aliases[it].conflict == null }.toSet()) }
    val count = actionsOn.size + aliasesOn.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cmd_imp_title)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.cmd_imp_warning),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (pack.blocked > 0) {
                    Text(
                        stringResource(R.string.cmd_imp_blocked, pack.blocked),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (preview.actions.isNotEmpty()) {
                    Text(
                        stringResource(R.string.cmd_pref_group_actions_short),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                preview.actions.forEachIndexed { index, entry ->
                    EntryRow(
                        title = "${entry.item.letter} · ${entry.item.label}",
                        entry = entry,
                        checked = index in actionsOn,
                        onChange = { actionsOn = if (it) actionsOn + index else actionsOn - index },
                    )
                }
                if (preview.aliases.isNotEmpty()) {
                    Text(
                        stringResource(R.string.cmd_pref_group_aliases),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                preview.aliases.forEachIndexed { index, entry ->
                    EntryRow(
                        title = "${entry.item.name} → ${entry.item.expansion}",
                        entry = entry,
                        checked = index in aliasesOn,
                        onChange = { aliasesOn = if (it) aliasesOn + index else aliasesOn - index },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = count > 0,
                onClick = {
                    onImport(
                        CommandPack(
                            pack.actions.filterIndexed { i, _ -> i in actionsOn },
                            pack.aliases.filterIndexed { i, _ -> i in aliasesOn },
                        ),
                    )
                },
            ) {
                Text(
                    if (count > 0) stringResource(R.string.cmd_imp_confirm_count, count) else stringResource(R.string.cmd_imp_confirm),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cmd_pref_cancel)) } },
    )
}

@Composable
private fun <T> EntryRow(title: String, entry: PackEntry<T>, checked: Boolean, onChange: (Boolean) -> Unit) {
    // A clash with no free name cannot be imported at all.
    val selectable = entry.conflict == null || entry.suggestion != null
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = selectable) { onChange(!checked) }
            .padding(top = 6.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = selectable,
            modifier = Modifier.padding(end = 12.dp, top = 2.dp),
        )
        Column {
            Text(title)
            Text(
                entry.detail,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.conflict?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                entry.suggestion?.let { name ->
                    Text(
                        stringResource(R.string.cmd_imp_rename, name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            entry.caution?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}
