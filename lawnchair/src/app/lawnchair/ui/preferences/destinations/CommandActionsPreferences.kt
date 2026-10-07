package app.lawnchair.ui.preferences.destinations

import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.lawnchair.command.ACTION_VIEW
import app.lawnchair.command.ActionKind
import app.lawnchair.command.AliasStore
import app.lawnchair.command.Aliases
import app.lawnchair.command.Analysis
import app.lawnchair.command.AppEntry
import app.lawnchair.command.ArgKind
import app.lawnchair.command.CommandAction
import app.lawnchair.command.CommandAlias
import app.lawnchair.command.CommandEngine
import app.lawnchair.command.CommandExecutor
import app.lawnchair.command.CommandPack
import app.lawnchair.command.CommandPacks
import app.lawnchair.command.CommandUsage
import app.lawnchair.command.ContactEntry
import app.lawnchair.command.CustomAction
import app.lawnchair.command.CustomActionStore
import app.lawnchair.command.CustomActions
import app.lawnchair.command.ImportCommandsActivity
import app.lawnchair.command.Tone
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.util.foldAccents
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the user is in the "add or edit an action" flow. */
private sealed interface Step {
    data object Catalog : Step
    data object Apps : Step
    data class Shortcuts(val app: AppEntry) : Step
    data object Advanced : Step
    data class Letter(val draft: CustomAction, val editing: CustomAction?) : Step
}

/** A sample contact for previews when the contacts permission is missing. It can never be run. */
private val DemoContact = ContactEntry("Ana Souza", "+55 11 98765-4321")

/**
 * Binds letters of the command bar to actions of other apps: recipes from the catalog and the
 * shortcuts an app publishes to the launcher. "w" (WhatsApp) is just the first preset.
 */
@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun CommandActionsPreferences() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var actions by remember { mutableStateOf(CustomActionStore.load(context)) }
    var step by remember { mutableStateOf<Step?>(null) }
    var aliases by remember { mutableStateOf(AliasStore.load(context)) }
    // The alias being edited, or a blank one for a new alias; null when no dialog is open.
    var aliasDraft by remember { mutableStateOf<AliasDraft?>(null) }
    var query by remember { mutableStateOf("") }
    var backupOpen by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    val usage = remember { CommandUsage.load(context) }

    // Contacts and apps feed the live previews of the editors; they load off the main thread.
    val contacts by produceState(emptyList<ContactEntry>()) {
        value = withContext(Dispatchers.IO) { CommandExecutor.loadContacts(context) }
    }
    val apps by produceState(CommandExecutor.cachedApps ?: emptyList()) {
        value = withContext(Dispatchers.IO) { CommandExecutor.loadApps(context) }
    }

    val undoLabel = stringResource(R.string.cmd_pref_undo)
    val runFailed = stringResource(R.string.command_failed)

    fun updateAliases(list: List<CommandAlias>) {
        aliases = list
        AliasStore.save(context, list)
    }

    fun update(list: List<CustomAction>) {
        actions = list
        CustomActionStore.save(context, list)
    }

    /** Tells what was lost and offers to take it back. */
    fun undoable(message: String, restore: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(message, actionLabel = undoLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) restore()
        }
    }

    fun removeAction(action: CustomAction) {
        val before = actions
        update(actions.filterNot { it === action })
        undoable(context.getString(R.string.cmd_pref_action_removed, action.letter)) { update(before) }
    }

    fun removeAlias(alias: CommandAlias) {
        val before = aliases
        updateAliases(aliases.filter { it !== alias })
        undoable(context.getString(R.string.cmd_pref_alias_removed, alias.name)) { updateAliases(before) }
    }

    fun restoreDefaults() {
        val before = actions
        update(CustomActions.DEFAULTS)
        undoable(context.getString(R.string.cmd_pref_restored)) { update(before) }
    }

    // What the user chose to send; set once the selection dialog is confirmed.
    var sharing by remember { mutableStateOf<ShareMode?>(null) }
    var selection by remember { mutableStateOf<CommandPack?>(null) }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(CommandPacks.MIME),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                val pack = selection ?: CommandPack(actions, aliases)
                it.write(CommandPacks.encode(pack.actions, pack.aliases).toByteArray())
            } != null
        }.getOrDefault(false)
        val message = if (ok) R.string.cmd_pref_exported else R.string.cmd_pref_export_failed
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Same confirmation as a file opened from a chat.
        context.startActivity(
            Intent(context, ImportCommandsActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }

    val q = query.foldAccents().trim()
    val shownActions = actions.filter {
        q.isEmpty() || it.letter.foldAccents().contains(q) || it.label.foldAccents().contains(q)
    }
    val shownAliases = aliases.filter {
        q.isEmpty() || it.name.foldAccents().contains(q) || it.expansion.foldAccents().contains(q)
    }

    fun usedLabel(key: String): String = CommandUsage.label(usage, key).let { if (it.isEmpty()) "" else " · $it" }

    Box(Modifier.fillMaxSize()) {
        PreferenceLayout(label = stringResource(R.string.command_actions_title)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.cmd_pref_search)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            PreferenceGroup(heading = stringResource(R.string.cmd_pref_group_actions)) {
                if (shownActions.isEmpty()) {
                    ClickablePreference(
                        label = stringResource(R.string.cmd_pref_no_actions),
                        subtitle = stringResource(
                            if (q.isEmpty()) R.string.cmd_pref_no_actions_hint else R.string.cmd_pref_no_match,
                        ),
                        onClick = {},
                    )
                }
                shownActions.forEach { action ->
                    ClickablePreference(
                        label = "${action.letter} · ${action.label}",
                        subtitle = action.usage + usedLabel(CommandUsage.actionKey(action.letter)),
                        onClick = { step = Step.Letter(action, editing = action) },
                    )
                }
            }
            PreferenceGroup(heading = stringResource(R.string.cmd_pref_group_aliases)) {
                shownAliases.forEach { alias ->
                    ClickablePreference(
                        label = alias.name,
                        subtitle = "→ ${alias.expansion}" + usedLabel(CommandUsage.aliasKey(alias.name)),
                        onClick = { aliasDraft = AliasDraft(alias) },
                    )
                }
                ClickablePreference(
                    label = stringResource(R.string.cmd_pref_new_alias),
                    subtitle = stringResource(R.string.cmd_pref_new_alias_hint),
                    onClick = { aliasDraft = AliasDraft(null) },
                )
            }
            PreferenceGroup(heading = stringResource(R.string.cmd_pref_group_add)) {
                ClickablePreference(
                    label = stringResource(R.string.cmd_pref_recipes),
                    subtitle = stringResource(R.string.cmd_pref_recipes_hint),
                    onClick = { step = Step.Catalog },
                )
                ClickablePreference(
                    label = stringResource(R.string.cmd_pref_app_functions),
                    subtitle = stringResource(R.string.cmd_pref_app_functions_hint),
                    onClick = { step = Step.Apps },
                )
                ClickablePreference(
                    label = stringResource(R.string.cmd_pref_advanced),
                    subtitle = stringResource(R.string.cmd_pref_advanced_hint),
                    onClick = { step = Step.Advanced },
                )
            }
            PreferenceGroup(heading = stringResource(R.string.cmd_pref_group_backup)) {
                ClickablePreference(
                    label = stringResource(R.string.cmd_pref_backup),
                    subtitle = stringResource(R.string.cmd_pref_backup_hint),
                    onClick = { backupOpen = true },
                )
                ClickablePreference(
                    label = stringResource(R.string.cmd_pref_restore),
                    subtitle = stringResource(R.string.cmd_pref_restore_hint),
                    onClick = { confirmRestore = true },
                )
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )
    }

    if (backupOpen) {
        BackupDialog(
            onShare = {
                backupOpen = false
                sharing = ShareMode.Share
            },
            onExport = {
                backupOpen = false
                sharing = ShareMode.Export
            },
            onImport = {
                backupOpen = false
                importer.launch(arrayOf("*/*"))
            },
            onDismiss = { backupOpen = false },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(stringResource(R.string.cmd_pref_restore_title)) },
            text = { Text(stringResource(R.string.cmd_pref_restore_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        restoreDefaults()
                    },
                ) {
                    Text(stringResource(R.string.cmd_pref_restore_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.cmd_pref_cancel)) }
            },
        )
    }

    sharing?.let { mode ->
        SelectCommandsDialog(
            title = stringResource(
                if (mode == ShareMode.Share) R.string.cmd_pref_share_title else R.string.cmd_pref_export_title,
            ),
            actions = actions,
            aliases = aliases,
            onConfirm = { pack ->
                sharing = null
                selection = pack
                if (mode == ShareMode.Export) {
                    exporter.launch(CommandPacks.FILE_NAME)
                } else {
                    val send = CommandPacks.shareIntent(context, pack.actions, pack.aliases)
                    if (send != null) {
                        context.startActivity(send)
                    } else {
                        Toast.makeText(context, R.string.cmd_pref_share_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { sharing = null },
        )
    }

    aliasDraft?.let { draft ->
        AliasDialog(
            editing = draft.editing,
            aliases = aliases,
            custom = actions,
            apps = apps,
            contacts = contacts,
            onSave = { saved ->
                updateAliases(
                    if (draft.editing == null) aliases + saved else aliases.map { if (it === draft.editing) saved else it },
                )
                aliasDraft = null
            },
            onRemove = {
                draft.editing?.let { removeAlias(it) }
                aliasDraft = null
            },
            onTest = { action ->
                if (!CommandExecutor.execute(context, action)) {
                    Toast.makeText(context, runFailed, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { aliasDraft = null },
        )
    }

    when (val s = step) {
        null -> Unit

        Step.Catalog -> PickerDialog(
            title = stringResource(R.string.cmd_pref_recipes),
            items = CustomActions.CATALOG,
            label = { "${it.label} · ${it.usage.substringBefore(" ·")}" },
            onPick = { step = Step.Letter(it, editing = null) },
            onDismiss = { step = null },
        )

        Step.Apps -> {
            // Scanning every installed app takes a moment, so it runs off the main thread.
            val integrationApps by produceState<List<AppEntry>?>(initialValue = null) {
                value = withContext(Dispatchers.Default) { CommandExecutor.loadIntegrationApps(context) }
            }
            val list = integrationApps
            if (list == null || list.isEmpty()) {
                AlertDialog(
                    onDismissRequest = { step = null },
                    confirmButton = {
                        TextButton(onClick = { step = null }) { Text(stringResource(R.string.cmd_pref_close)) }
                    },
                    title = { Text(stringResource(R.string.cmd_pref_apps_title)) },
                    text = {
                        Text(
                            stringResource(
                                if (list == null) R.string.cmd_pref_searching_apps else R.string.cmd_pref_no_integrations,
                            ),
                        )
                    },
                )
            } else {
                PickerDialog(
                    title = stringResource(R.string.cmd_pref_apps_title),
                    items = list,
                    label = { it.label },
                    onPick = { step = Step.Shortcuts(it) },
                    onDismiss = { step = null },
                )
            }
        }

        is Step.Shortcuts -> {
            val pkg = ComponentName.unflattenFromString(s.app.id)?.packageName.orEmpty()
            val found = remember(pkg) { CommandExecutor.loadIntegrations(context, pkg) }
            if (found.isEmpty()) {
                AlertDialog(
                    onDismissRequest = { step = null },
                    confirmButton = {
                        TextButton(onClick = { step = Step.Apps }) { Text(stringResource(R.string.cmd_pref_back)) }
                    },
                    title = { Text(s.app.label) },
                    text = { Text(stringResource(R.string.cmd_pref_no_functions)) },
                )
            } else {
                val shortcutPrefix = stringResource(R.string.cmd_pref_kind_shortcut)
                val recipePrefix = stringResource(R.string.cmd_pref_kind_recipe)
                val intentPrefix = stringResource(R.string.cmd_pref_kind_intent)
                PickerDialog(
                    title = s.app.label,
                    items = found,
                    label = {
                        when {
                            it.kind == ActionKind.SHORTCUT -> shortcutPrefix
                            it.letter.isNotEmpty() -> recipePrefix
                            else -> intentPrefix
                        } + it.label
                    },
                    onPick = {
                        // A catalog recipe already carries the app's name; the others get it as a prefix.
                        val label = if (it.label.startsWith(s.app.label, ignoreCase = true)) it.label else "${s.app.label} · ${it.label}"
                        step = Step.Letter(it.copy(label = label), editing = null)
                    },
                    onDismiss = { step = null },
                )
            }
        }

        Step.Advanced -> AdvancedDialog(
            onDone = { step = Step.Letter(it, editing = null) },
            onDismiss = { step = null },
        )

        is Step.Letter -> LetterDialog(
            step = s,
            existing = actions,
            contacts = contacts,
            onSave = { saved ->
                update(
                    if (s.editing == null) {
                        actions + saved
                    } else {
                        actions.map { if (it === s.editing) saved else it }
                    },
                )
                step = null
            },
            onRemove = {
                s.editing?.let { removeAction(it) }
                step = null
            },
            onTest = { action ->
                if (!CommandExecutor.execute(context, action)) {
                    Toast.makeText(context, runFailed, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { step = null },
        )
    }
}

/** Share, export or import: three ways to move commands, one entry in the list. */
@Composable
private fun BackupDialog(onShare: () -> Unit, onExport: () -> Unit, onImport: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cmd_pref_backup)) },
        text = {
            Column {
                @Composable
                fun Option(title: Int, hint: Int, onClick: () -> Unit) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onClick)
                            .padding(vertical = 10.dp),
                    ) {
                        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Option(R.string.cmd_pref_share, R.string.cmd_pref_share_hint, onShare)
                Option(R.string.cmd_pref_export, R.string.cmd_pref_export_hint, onExport)
                Option(R.string.cmd_pref_import, R.string.cmd_pref_import_hint, onImport)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cmd_pref_cancel)) } },
    )
}

@Composable
private fun <T> PickerDialog(
    title: String,
    items: List<T>,
    label: (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cmd_pref_cancel)) } },
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(items) { item ->
                    Text(
                        text = label(item),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(item) }
                            .padding(vertical = 14.dp),
                    )
                }
            }
        },
    )
}

/** The line a preview shows: the technical intent when the command can run, otherwise why it cannot. */
@Composable
private fun PreviewBox(analysis: Analysis?, intro: String? = null) {
    if (analysis == null) return
    val problem = analysis.action == null && analysis.tone == Tone.ERROR
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            stringResource(R.string.cmd_pref_preview),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (intro != null) Text(intro, style = MaterialTheme.typography.bodyMedium)
        when {
            analysis.needsContacts -> Text(
                stringResource(R.string.cmd_pref_needs_contacts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            analysis.intent.isNotEmpty() -> Text(
                analysis.intent,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.tertiary,
            )

            else -> Text(
                analysis.preview.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = if (problem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LetterDialog(
    step: Step.Letter,
    existing: List<CustomAction>,
    contacts: List<ContactEntry>,
    onSave: (CustomAction) -> Unit,
    onRemove: () -> Unit,
    onTest: (CommandAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var letter by remember { mutableStateOf(step.draft.letter.ifEmpty { suggestLetter(step.draft.label, existing) }) }
    val error = CustomActions.validateLetter(letter, existing, step.editing)
    val draft = step.draft.copy(letter = letter.trim().lowercase())
    val sampleText = stringResource(R.string.cmd_pref_sample_text)
    val sampleContact = stringResource(R.string.cmd_pref_sample_contact)
    val sampleBoth = stringResource(R.string.cmd_pref_sample_both)
    var sample by remember {
        mutableStateOf(
            when (step.draft.arg) {
                ArgKind.NONE -> ""
                ArgKind.TEXT -> sampleText
                ArgKind.CONTACT -> sampleContact
                ArgKind.CONTACT_AND_TEXT -> sampleBoth
            },
        )
    }
    // Without contacts access the preview uses a sample contact, and that one is never run.
    val pool = contacts.ifEmpty { listOf(DemoContact) }
    val analysis = if (error != null) {
        null
    } else {
        val typed = draft.letter + if (sample.isBlank()) "" else " $sample"
        CommandEngine.analyze(typed, emptyList(), pool, true, listOf(draft), emptyList())
    }
    val runnable = (analysis?.action as? CommandAction.Custom)
        ?.takeIf { it.contact !== DemoContact }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(step.draft.label) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(step.draft.usage.replaceBefore(" ·", letter.trim().lowercase()))
                OutlinedTextField(
                    value = letter,
                    onValueChange = { letter = it.take(MAX_LETTER) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.cmd_pref_letter_label)) },
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (step.draft.arg != ArgKind.NONE) {
                    OutlinedTextField(
                        value = sample,
                        onValueChange = { sample = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.cmd_pref_try_label)) },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                PreviewBox(analysis)
            }
        },
        confirmButton = {
            TextButton(
                enabled = error == null,
                onClick = { onSave(draft) },
            ) { Text(stringResource(R.string.cmd_pref_save)) }
        },
        dismissButton = {
            Column {
                TextButton(enabled = runnable != null, onClick = { runnable?.let(onTest) }) {
                    Text(stringResource(R.string.cmd_pref_test))
                }
                if (step.editing != null) TextButton(onClick = onRemove) { Text(stringResource(R.string.cmd_pref_remove)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cmd_pref_cancel)) }
            }
        },
    )
}

private class AliasDraft(val editing: CommandAlias?)

/** Creates or edits an alias: the word and the command it stands for, with what it resolves to now. */
@Composable
private fun AliasDialog(
    editing: CommandAlias?,
    aliases: List<CommandAlias>,
    custom: List<CustomAction>,
    apps: List<AppEntry>,
    contacts: List<ContactEntry>,
    onSave: (CommandAlias) -> Unit,
    onRemove: () -> Unit,
    onTest: (CommandAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var expansion by remember { mutableStateOf(editing?.expansion.orEmpty()) }
    val error = Aliases.validate(name, expansion, aliases, custom, editing)
    val granted = remember { CommandExecutor.hasContactsPermission(context) }
    val analysis = if (expansion.isBlank()) {
        null
    } else {
        CommandEngine.analyze(expansion, apps, contacts, granted, custom, emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (editing == null) R.string.cmd_pref_alias_new_title else R.string.cmd_pref_alias_edit_title))
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.filter { c -> !c.isWhitespace() }.take(MAX_ALIAS) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.cmd_pref_alias_label)) },
                    supportingText = { Text(stringResource(R.string.cmd_pref_alias_hint)) },
                )
                OutlinedTextField(
                    value = expansion,
                    onValueChange = { expansion = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.cmd_pref_alias_command)) },
                    supportingText = { Text(stringResource(R.string.cmd_pref_alias_command_hint)) },
                    modifier = Modifier.padding(top = 8.dp),
                )
                // An empty form needs no complaint; the error appears as soon as something is typed.
                if (error != null && (name.isNotBlank() || expansion.isNotBlank())) {
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
                PreviewBox(
                    analysis,
                    intro = name.trim().takeIf { it.isNotEmpty() }?.let {
                        stringResource(R.string.cmd_pref_alias_typing, it.lowercase())
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = error == null,
                onClick = { onSave(CommandAlias(name.trim().lowercase(), expansion.trim())) },
            ) { Text(stringResource(R.string.cmd_pref_save)) }
        },
        dismissButton = {
            Column {
                TextButton(enabled = analysis?.action != null, onClick = { analysis?.action?.let(onTest) }) {
                    Text(stringResource(R.string.cmd_pref_test))
                }
                if (editing != null) TextButton(onClick = onRemove) { Text(stringResource(R.string.cmd_pref_remove)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cmd_pref_cancel)) }
            }
        },
    )
}

private const val MAX_ALIAS = 20

private enum class ShareMode { Share, Export }

/** Lists every action and alias with a checkbox, all ticked, so the user sends one, some or all. */
@Composable
private fun SelectCommandsDialog(
    title: String,
    actions: List<CustomAction>,
    aliases: List<CommandAlias>,
    onConfirm: (CommandPack) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosenActions by remember { mutableStateOf(actions.toSet()) }
    var chosenAliases by remember { mutableStateOf(aliases.toSet()) }
    val count = chosenActions.size + chosenAliases.size
    val total = actions.size + aliases.size

    @Composable
    fun <T> Item(item: T, text: String, chosen: Set<T>, onChange: (Set<T>) -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChange(if (item in chosen) chosen - item else chosen + item) },
        ) {
            Checkbox(checked = item in chosen, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
            Text(text)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (total == 0) Text(stringResource(R.string.cmd_pref_nothing_to_send))
                if (actions.isNotEmpty()) {
                    Text(stringResource(R.string.cmd_pref_group_actions_short), style = MaterialTheme.typography.titleSmall)
                }
                actions.forEach { Item(it, "${it.letter} · ${it.label}", chosenActions) { s -> chosenActions = s } }
                if (aliases.isNotEmpty()) {
                    Text(
                        stringResource(R.string.cmd_pref_group_aliases),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                aliases.forEach { Item(it, "${it.name} → ${it.expansion}", chosenAliases) { s -> chosenAliases = s } }
            }
        },
        confirmButton = {
            TextButton(
                enabled = count > 0,
                onClick = {
                    onConfirm(CommandPack(actions.filter { it in chosenActions }, aliases.filter { it in chosenAliases }))
                },
            ) {
                Text(
                    if (count > 0) stringResource(R.string.cmd_pref_continue_count, count) else stringResource(R.string.cmd_pref_continue),
                )
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        val all = count == total
                        chosenActions = if (all) emptySet() else actions.toSet()
                        chosenAliases = if (all) emptySet() else aliases.toSet()
                    },
                ) { Text(stringResource(if (count == total) R.string.cmd_pref_none else R.string.cmd_pref_all)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cmd_pref_cancel)) }
            }
        },
    )
}

/** Manual recipe: the intent action, an optional URI template, the package and what to type. */
@Composable
private fun AdvancedDialog(onDone: (CustomAction) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    var action by remember { mutableStateOf(ACTION_VIEW) }
    var template by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("") }
    var arg by remember { mutableStateOf(ArgKind.TEXT) }
    var shareText by remember { mutableStateOf(false) }
    val valid = label.isNotBlank() && (template.isNotBlank() || shareText)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cmd_pref_manual_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(label, { label = it }, singleLine = true, label = { Text(stringResource(R.string.cmd_pref_manual_name)) })
                OutlinedTextField(
                    action,
                    { action = it.trim() },
                    singleLine = true,
                    label = { Text(stringResource(R.string.cmd_pref_manual_action)) },
                    supportingText = { Text(stringResource(R.string.cmd_pref_manual_action_hint)) },
                )
                OutlinedTextField(
                    template,
                    { template = it.trim() },
                    singleLine = true,
                    label = { Text(stringResource(R.string.cmd_pref_manual_uri)) },
                    supportingText = { Text(stringResource(R.string.cmd_pref_manual_uri_hint)) },
                )
                OutlinedTextField(
                    pkg,
                    { pkg = it.trim() },
                    singleLine = true,
                    label = { Text(stringResource(R.string.cmd_pref_manual_package)) },
                )
                Text(stringResource(R.string.cmd_pref_manual_arg), modifier = Modifier.padding(top = 12.dp))
                ArgKind.entries.forEach {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { arg = it },
                    ) {
                        RadioButton(selected = arg == it, onClick = { arg = it })
                        Text(stringResource(argLabel(it)))
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { shareText = !shareText },
                ) {
                    Checkbox(checked = shareText, onCheckedChange = { shareText = it })
                    Text(stringResource(R.string.cmd_pref_manual_share))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onDone(
                        CustomAction(
                            letter = "",
                            label = label.trim(),
                            kind = ActionKind.INTENT,
                            template = template,
                            packages = listOfNotNull(pkg.takeIf { it.isNotEmpty() }),
                            arg = arg,
                            intentAction = expandAction(action),
                            mimeType = if (shareText) "text/plain" else null,
                            textExtra = if (shareText) "android.intent.extra.TEXT" else null,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.cmd_pref_continue)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cmd_pref_cancel)) } },
    )
}

private fun argLabel(arg: ArgKind) = when (arg) {
    ArgKind.NONE -> R.string.cmd_pref_arg_none
    ArgKind.TEXT -> R.string.cmd_pref_arg_text
    ArgKind.CONTACT -> R.string.cmd_pref_arg_contact
    ArgKind.CONTACT_AND_TEXT -> R.string.cmd_pref_arg_contact_text
}

/** "VIEW" becomes "android.intent.action.VIEW"; a full name is kept. */
private fun expandAction(raw: String) = if ('.' in raw) raw else "android.intent.action." + raw.uppercase().ifEmpty { "VIEW" }

private const val MAX_LETTER = 4

/** A free letter taken from the label: "Lanterna" gives "l", then "la" and so on. */
private fun suggestLetter(label: String, existing: List<CustomAction>): String {
    val base = label.lowercase().filter { it.isLetterOrDigit() }
    for (n in 1..minOf(MAX_LETTER, base.length)) {
        val candidate = base.take(n)
        if (CustomActions.validateLetter(candidate, existing) == null) return candidate
    }
    return ""
}
