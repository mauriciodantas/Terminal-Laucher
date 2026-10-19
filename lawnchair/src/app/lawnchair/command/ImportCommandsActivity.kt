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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.widgets.lottie.ImportWidgetActivity
import app.lawnchair.widgets.lottie.LottieWidgetPacks
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
        val uri = incomingUri(intent)
        val pack = uri?.let { CommandPacks.read(this, it) }
        // Chats hand any file over as octet-stream, so a theme or a widget file may land here first.
        val other = when {
            pack != null || uri == null -> null
            ThemeStore.read(this, uri) != null -> ImportThemeActivity::class.java
            LottieWidgetPacks.looksLikePack(this, uri) -> ImportWidgetActivity::class.java
            else -> null
        }
        if (other != null) {
            startActivity(
                Intent(this, other)
                    .setAction(Intent.ACTION_VIEW)
                    .setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
            finish()
            return
        }
        if (pack == null) {
            Toast.makeText(this, R.string.cmd_imp_invalid, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val current = CustomActionStore.load(this) to AliasStore.load(this)
        val providers = ProviderStore.load(this)
        setContent {
            LawnchairTheme {
                ImportDialog(
                    pack = pack,
                    preview = PackRules.preview(current.first, current.second, pack, providers),
                    onImport = { chosen, secrets -> importPack(chosen, secrets) },
                    onDismiss = { finish() },
                )
            }
        }
    }

    /**
     * Adds what was ticked. An entry that clashes comes in under its free suggested name; a provider
     * the user already has stays theirs. [secrets] are the keys typed for the providers that came in.
     */
    private fun importPack(chosen: CommandPack, secrets: Map<String, String>) {
        val merge = CommandPacks.merge(CustomActionStore.load(this), AliasStore.load(this), chosen, rename = true, providers = ProviderStore.load(this))
        CustomActionStore.save(this, merge.actions)
        AliasStore.save(this, merge.aliases)
        ProviderStore.save(this, merge.providers)
        var pending = 0
        merge.addedProviders.forEach { id ->
            val provider = merge.providers.first { it.id == id }
            val secret = secrets[id]?.trim().orEmpty()
            when {
                !provider.auth.needsSecret -> {}
                secret.isNotEmpty() -> SecretStore.put(this, id, secret)
                else -> pending++
            }
        }
        val text = getString(R.string.cmd_imp_done, merge.addedActions, merge.addedAliases) +
            (if (merge.skipped > 0) getString(R.string.cmd_imp_skipped, merge.skipped) else "") +
            (if (pending > 0) getString(R.string.cmd_imp_keys_pending, pending) else "")
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
    onImport: (CommandPack, Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    // What fits starts ticked; a clash starts unticked and, when ticked, comes in under another name.
    // An action that sends the location or the clipboard out, or changes data, is never ticked for the user.
    var actionsOn by remember {
        // A script runs somebody else's code: it starts unticked, and can be ticked once its code was seen.
        mutableStateOf(
            preview.actions.indices.filter {
                preview.actions[it].conflict == null && preview.sends.getOrNull(it)?.needsTrust != true && !PackRules.isScript(preview.actions[it].item)
            }.toSet(),
        )
    }
    var codeSeen by remember { mutableStateOf(emptySet<Int>()) }
    var codeShown by remember { mutableStateOf<Int?>(null) }
    // The sandbox checks each script; one that does not compile still comes in if ticked, marked.
    val context = androidx.compose.ui.platform.LocalContext.current
    val scripts = remember { ScriptRunner(context) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { scripts.close() } }
    var scriptErrors by remember { mutableStateOf(emptyMap<Int, String>()) }
    androidx.compose.runtime.LaunchedEffect(preview) {
        val found = mutableMapOf<Int, String>()
        preview.actions.forEachIndexed { i, entry ->
            val spec = entry.item.script?.takeIf { PackRules.isScript(entry.item) } ?: return@forEachIndexed
            val check = scripts.check(spec)
            if (!check.ok) found[i] = check.line?.let { l -> context.getString(app.lawnchair.command.R.string.cmd_script_line, l, check.message.orEmpty()) } ?: check.message.orEmpty()
        }
        scriptErrors = found
    }
    var aliasesOn by remember { mutableStateOf(preview.aliases.indices.filter { preview.aliases[it].conflict == null }.toSet()) }
    val count = actionsOn.size + aliasesOn.size
    // A provider comes in with the ticked actions that use it, never on its own. The preview's
    // actions already point at the id each provider comes in as.
    val chosenActions = pack.actions.filterIndexed { i, _ -> i in actionsOn }
    val chosenIds = preview.actions.filterIndexed { i, _ -> i in actionsOn }.map { it.item.provider }.toSet()
    val providers = preview.providers.filter { it.item.id in chosenIds }
    // Where the ticked actions send things, one line per host.
    val destinations = actionsOn.mapNotNull { preview.sends.getOrNull(it) }.groupBy { it.host }
    var secrets by remember { mutableStateOf(emptyMap<String, String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cmd_imp_title)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.cmd_imp_summary, pack.actions.size, pack.aliases.size),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.cmd_imp_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
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
                    val script = PackRules.isScript(entry.item)
                    EntryRow(
                        title = "${entry.item.letter} · ${entry.item.label}",
                        entry = entry,
                        checked = index in actionsOn,
                        onChange = { actionsOn = if (it) actionsOn + index else actionsOn - index },
                        enabled = !script || index in codeSeen,
                        error = scriptErrors[index],
                        extra = if (script) {
                            {
                                TextButton(onClick = {
                                    codeShown = index
                                    codeSeen = codeSeen + index
                                }) { Text(stringResource(R.string.cmd_imp_view_code)) }
                            }
                        } else {
                            null
                        },
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
                if (destinations.isNotEmpty()) {
                    Text(
                        stringResource(R.string.cmd_imp_destinations),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    destinations.forEach { (host, all) ->
                        val merged = PackSends(host, all.any { it.typed }, all.flatMap { it.variables }.toSet())
                        // The methods that change data on that host, said up front: "(POST, DELETE)".
                        val writes = all.filter { it.writes }.map { it.method }.distinct()
                        val methods = if (writes.isEmpty()) "" else " (" + writes.joinToString(", ") + ")"
                        Text(
                            stringResource(R.string.cmd_imp_destination, host + methods, merged.label().ifEmpty { stringResource(R.string.cmd_imp_sends_nothing) }),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (merged.isSensitive || writes.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                if (providers.isNotEmpty()) {
                    Text(
                        stringResource(R.string.cmd_imp_providers),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                providers.forEach { entry ->
                    val provider = entry.item
                    Column(Modifier.padding(top = 6.dp)) {
                        Text("${provider.id} · ${provider.name}")
                        Text(
                            entry.detail,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        entry.caution?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                        // Only a provider that comes in asks for its key; the user's own keeps its own.
                        if (provider.id !in preview.reused && provider.auth.needsSecret) {
                            OutlinedTextField(
                                value = secrets[provider.id].orEmpty(),
                                onValueChange = { secrets = secrets + (provider.id to it) },
                                label = { Text(stringResource(R.string.cmd_imp_provider_key)) },
                                supportingText = { Text(stringResource(R.string.cmd_imp_provider_key_hint)) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                            if (provider.keyHelp.isNotEmpty()) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                TextButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.keyHelp)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                }) { Text(stringResource(R.string.cmd_providers_get_key)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = count > 0,
                onClick = {
                    onImport(
                        CommandPack(
                            chosenActions,
                            pack.aliases.filterIndexed { i, _ -> i in aliasesOn },
                            // The pack's own providers: the merge renames them the same way again.
                            providers = pack.providers.filter { p -> chosenActions.any { it.provider == p.id } },
                        ),
                        secrets,
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
    codeShown?.let { index ->
        val action = preview.actions[index].item
        AlertDialog(
            onDismissRequest = { codeShown = null },
            title = { Text("${action.letter} · ${action.label}") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        (if (action.script?.expr != null) "node -p\n" else "node -e\n") + action.script?.code.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { codeShown = null }) { Text(stringResource(R.string.cmd_imp_code_seen)) } },
        )
    }
}

@Composable
private fun <T> EntryRow(
    title: String,
    entry: PackEntry<T>,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    error: String? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    // A clash with no free name cannot be imported at all.
    val selectable = (entry.conflict == null || entry.suggestion != null) && enabled
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
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            extra?.invoke()
        }
    }
}
