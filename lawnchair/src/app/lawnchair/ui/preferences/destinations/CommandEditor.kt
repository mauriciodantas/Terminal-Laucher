package app.lawnchair.ui.preferences.destinations

import android.content.ComponentName
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.lawnchair.command.ActionKind
import app.lawnchair.command.Aliases
import app.lawnchair.command.AppEntry
import app.lawnchair.command.ArgKind
import app.lawnchair.command.CommandAction
import app.lawnchair.command.CommandAlias
import app.lawnchair.command.CommandEngine
import app.lawnchair.command.CommandExecutor
import app.lawnchair.command.CommandPattern
import app.lawnchair.command.ContactEntry
import app.lawnchair.command.CustomAction
import app.lawnchair.command.CustomActions
import app.lawnchair.command.R as CommandR
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the command editor opens on: a saved action, a saved alias, or nothing for a new command. */
internal sealed interface EditorTarget {
    data object New : EditorTarget
    data class Action(val action: CustomAction) : EditorTarget
    data class Alias(val alias: CommandAlias) : EditorTarget
}

/** The two kinds of command the editor makes, one tab each. */
private enum class EditorTab { ACTION, ALIAS }

/** A list the editor opens over itself to fill the form. */
private sealed interface EditorPicker {
    data object Recipes : EditorPicker
    data object Apps : EditorPicker
    data class Functions(val app: AppEntry) : EditorPicker
    data object Package : EditorPicker
}

private const val EXTRA_TEXT = "android.intent.extra.TEXT"

/**
 * What the user types after the letter, read from the template: `{text}` (or text sent as an extra)
 * asks for a text, `{name}`, `{phone}` or `{number}` for a contact, both for a contact and a message.
 */
internal fun inferArg(template: String, textExtra: String?): ArgKind {
    val text = "{text}" in template || textExtra != null
    val contact = listOf("{name}", "{phone}", "{number}").any { it in template }
    return when {
        text && contact -> ArgKind.CONTACT_AND_TEXT
        contact -> ArgKind.CONTACT
        text -> ArgKind.TEXT
        else -> ArgKind.NONE
    }
}

/**
 * Creates or edits a command in one place: an action that opens a site or an app, or an alias for
 * commands that already exist. A ready-made recipe or an app's function only fills the form, so
 * everything stays editable afterwards, also for a saved action.
 */
@Suppress("ktlint:compose:modifier-missing-check")
@Composable
internal fun CommandEditor(
    target: EditorTarget,
    actions: List<CustomAction>,
    aliases: List<CommandAlias>,
    apps: List<AppEntry>,
    contacts: List<ContactEntry>,
    onSaveAction: (CustomAction) -> Unit,
    onSaveAlias: (CommandAlias) -> Unit,
    onRemove: () -> Unit,
    onTest: (CommandAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val editingAction = (target as? EditorTarget.Action)?.action
    val editingAlias = (target as? EditorTarget.Alias)?.alias
    val start = editingAction ?: CustomAction("", "", ActionKind.INTENT, "", emptyList(), ArgKind.TEXT)

    var tab by remember { mutableStateOf(if (editingAlias != null) EditorTab.ALIAS else EditorTab.ACTION) }
    var word by remember { mutableStateOf(editingAction?.letter ?: editingAlias?.name.orEmpty()) }
    // A new command suggests its letter from the name until the user types one.
    var wordTouched by remember { mutableStateOf(target != EditorTarget.New) }
    var name by remember { mutableStateOf(start.label) }
    // What the form does not show (the kind, a shortcut id, the MIME type) comes from here.
    var base by remember { mutableStateOf(start) }
    var template by remember { mutableStateOf(TextFieldValue(start.template)) }
    var packages by remember { mutableStateOf(start.packages) }
    var intentAction by remember { mutableStateOf(start.intentAction) }
    var shareText by remember { mutableStateOf(start.textExtra != null) }
    var smsFallback by remember { mutableStateOf(start.smsFallback) }
    var pattern by remember { mutableStateOf(start.pattern) }
    var expansion by remember { mutableStateOf(editingAlias?.expansion.orEmpty()) }
    // The rare options start open only when the action uses one of them.
    var moreOpen by remember {
        mutableStateOf(expandAction(start.intentAction) != expandAction("VIEW") || shareText || smsFallback || start.pattern.isNotEmpty())
    }
    var picker by remember { mutableStateOf<EditorPicker?>(null) }

    fun suggest(label: String) {
        if (!wordTouched) word = suggestLetter(label, actions)
    }

    /** A recipe or an app's function fills the form; the letter and the name follow it while untouched. */
    fun fill(from: CustomAction) {
        base = from
        template = TextFieldValue(from.template)
        packages = from.packages
        intentAction = from.intentAction
        shareText = from.textExtra != null
        smsFallback = from.smsFallback
        name = from.label
        if (!wordTouched) {
            word = from.letter.takeIf { it.isNotEmpty() && CustomActions.validateLetter(it, actions, editingAction) == null }
                ?: suggestLetter(from.label, actions)
        }
    }

    val shortcut = base.kind == ActionKind.SHORTCUT
    val arg = if (shortcut) base.arg else inferArg(template.text, if (shareText) EXTRA_TEXT else null)
    val draft = base.copy(
        letter = word.trim().lowercase(),
        label = name.trim(),
        template = if (shortcut) base.template else template.text.trim(),
        packages = packages,
        arg = arg,
        intentAction = expandAction(intentAction),
        mimeType = when {
            shareText -> base.mimeType ?: "text/plain"
            base.textExtra != null -> null
            else -> base.mimeType
        },
        textExtra = if (shareText) base.textExtra ?: EXTRA_TEXT else null,
        smsFallback = smsFallback,
        pattern = pattern.trim(),
    )
    val letterError = CustomActions.validateLetter(word, actions, editingAction)
    val patternError = CommandPattern.validate(pattern.trim())
    val hasTarget = shortcut || template.text.isNotBlank() || shareText
    val actionValid = letterError == null && patternError == null && name.isNotBlank() && hasTarget
    val aliasError = Aliases.validate(word, expansion, aliases, actions, editingAlias)
    val canSave = if (tab == EditorTab.ACTION) actionValid else aliasError == null

    val sampleText = stringResource(R.string.cmd_pref_sample_text)
    val sampleContact = stringResource(R.string.cmd_pref_sample_contact)
    val sampleBoth = stringResource(R.string.cmd_pref_sample_both)
    var sample by remember(arg) {
        mutableStateOf(
            when (arg) {
                ArgKind.NONE -> ""
                ArgKind.TEXT -> sampleText
                ArgKind.CONTACT -> sampleContact
                ArgKind.CONTACT_AND_TEXT -> sampleBoth
            },
        )
    }
    // Same engine as the bar; without contacts access a sample contact stands in, and it is never run.
    val pool = contacts.ifEmpty { listOf(DemoContact) }
    val previewLetter = if (letterError == null) draft.letter else PREVIEW_LETTER
    val actionAnalysis = if (tab == EditorTab.ACTION && hasTarget) {
        CommandEngine.analyze(previewLetter + if (sample.isBlank()) "" else " $sample", emptyList(), pool, true, listOf(draft.copy(letter = previewLetter)), emptyList())
    } else {
        null
    }
    val granted = remember { CommandExecutor.hasContactsPermission(context) }
    val aliasAnalysis = if (tab == EditorTab.ALIAS && expansion.isNotBlank()) {
        CommandEngine.analyze(expansion, apps, contacts, granted, actions, emptyList())
    } else {
        null
    }
    val runnable = if (tab == EditorTab.ACTION) {
        (actionAnalysis?.action as? CommandAction.Custom)?.takeIf { it.contact !== DemoContact }
    } else {
        aliasAnalysis?.action
    }

    fun save() {
        if (tab == EditorTab.ACTION) onSaveAction(draft) else onSaveAlias(CommandAlias(word.trim().lowercase(), expansion.trim()))
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cmd_pref_cancel))
                    }
                    Text(
                        stringResource(if (target == EditorTarget.New) R.string.cmd_pref_new_command else R.string.cmd_pref_edit_command),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = canSave, onClick = ::save) { Text(stringResource(R.string.cmd_pref_save)) }
                }
                // A saved command keeps its kind: only a new one picks between the tabs.
                SecondaryTabRow(selectedTabIndex = tab.ordinal) {
                    EditorTab.entries.forEach {
                        Tab(
                            selected = tab == it,
                            enabled = target == EditorTarget.New || tab == it,
                            onClick = { tab = it },
                            text = { Text(stringResource(if (it == EditorTab.ACTION) R.string.cmd_pref_tab_action else R.string.cmd_pref_tab_alias)) },
                        )
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    OutlinedTextField(
                        value = word,
                        onValueChange = {
                            word = if (tab == EditorTab.ALIAS) it.filter { c -> !c.isWhitespace() }.take(MAX_ALIAS) else it.take(MAX_LETTER)
                            wordTouched = true
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        label = { Text(stringResource(if (tab == EditorTab.ACTION) R.string.cmd_pref_letter_label else R.string.cmd_pref_alias_label)) },
                        isError = tab == EditorTab.ACTION && word.isNotEmpty() && letterError != null,
                        supportingText = {
                            when {
                                tab == EditorTab.ALIAS -> Text(stringResource(R.string.cmd_pref_alias_hint))
                                word.isNotEmpty() && letterError != null -> Text(letterError)
                                else -> {}
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (tab == EditorTab.ACTION) {
                        ActionForm(
                            name = name,
                            onName = {
                                name = it
                                suggest(it)
                            },
                            base = base,
                            template = template,
                            onTemplate = { template = it },
                            arg = arg,
                            packages = packages,
                            apps = apps,
                            intentAction = intentAction,
                            onIntentAction = { intentAction = it },
                            shareText = shareText,
                            onShareText = { shareText = it },
                            smsFallback = smsFallback,
                            onSmsFallback = { smsFallback = it },
                            pattern = pattern,
                            onPattern = { pattern = it.take(CommandPattern.MAX_LENGTH) },
                            patternError = patternError,
                            moreOpen = moreOpen,
                            onMoreOpen = { moreOpen = it },
                            onPick = { picker = it },
                        )
                        if (arg != ArgKind.NONE && hasTarget) {
                            OutlinedTextField(
                                value = sample,
                                onValueChange = { sample = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                prefix = { Text("$previewLetter ", fontFamily = FontFamily.Monospace) },
                                label = { Text(stringResource(R.string.cmd_pref_try_label)) },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            )
                        }
                        LetterPreview(previewLetter, sample, actionAnalysis, draft.smsFallback, draft.label)
                    } else {
                        AliasForm(word = word, expansion = expansion, onExpansion = { expansion = it }, error = aliasError, analysis = aliasAnalysis)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    if (target != EditorTarget.New) {
                        TextButton(onClick = onRemove) {
                            Text(stringResource(R.string.cmd_pref_remove), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(enabled = runnable != null, onClick = { runnable?.let(onTest) }) {
                        Text(stringResource(R.string.cmd_pref_test))
                    }
                }
            }
        }
    }

    when (val p = picker) {
        null -> Unit

        EditorPicker.Recipes -> PickerDialog(
            title = stringResource(R.string.cmd_pref_recipes),
            items = CustomActions.CATALOG,
            label = { "${it.label} · ${it.usage.substringBefore(" ·")}" },
            onPick = {
                fill(it)
                picker = null
            },
            onDismiss = { picker = null },
        )

        EditorPicker.Apps -> {
            // Scanning every installed app takes a moment, so it runs off the main thread.
            val integrationApps by produceState<List<AppEntry>?>(initialValue = null) {
                value = withContext(Dispatchers.Default) { CommandExecutor.loadIntegrationApps(context) }
            }
            val list = integrationApps
            if (list.isNullOrEmpty()) {
                AlertDialog(
                    onDismissRequest = { picker = null },
                    confirmButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.cmd_pref_close)) } },
                    title = { Text(stringResource(R.string.cmd_pref_apps_title)) },
                    text = { Text(stringResource(if (list == null) R.string.cmd_pref_searching_apps else R.string.cmd_pref_no_integrations)) },
                )
            } else {
                PickerDialog(
                    title = stringResource(R.string.cmd_pref_apps_title),
                    items = list,
                    label = { it.label },
                    onPick = { picker = EditorPicker.Functions(it) },
                    onDismiss = { picker = null },
                )
            }
        }

        is EditorPicker.Functions -> {
            val pkg = ComponentName.unflattenFromString(p.app.id)?.packageName.orEmpty()
            val found = remember(pkg) { CommandExecutor.loadIntegrations(context, pkg) }
            if (found.isEmpty()) {
                AlertDialog(
                    onDismissRequest = { picker = null },
                    confirmButton = { TextButton(onClick = { picker = EditorPicker.Apps }) { Text(stringResource(R.string.cmd_pref_back)) } },
                    title = { Text(p.app.label) },
                    text = { Text(stringResource(R.string.cmd_pref_no_functions)) },
                )
            } else {
                val shortcutPrefix = stringResource(R.string.cmd_pref_kind_shortcut)
                val recipePrefix = stringResource(R.string.cmd_pref_kind_recipe)
                val intentPrefix = stringResource(R.string.cmd_pref_kind_intent)
                PickerDialog(
                    title = p.app.label,
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
                        val label = if (it.label.startsWith(p.app.label, ignoreCase = true)) it.label else "${p.app.label} · ${it.label}"
                        fill(it.copy(label = label))
                        picker = null
                    },
                    onDismiss = { picker = null },
                )
            }
        }

        EditorPicker.Package -> {
            val anyApp = stringResource(R.string.cmd_pref_app_any)
            PickerDialog(
                title = stringResource(R.string.cmd_pref_app_label),
                items = listOf<AppEntry?>(null) + apps.sortedBy { it.label.lowercase() },
                label = { it?.label ?: anyApp },
                onPick = { app ->
                    packages = listOfNotNull(app?.let { ComponentName.unflattenFromString(it.id)?.packageName })
                    picker = null
                },
                onDismiss = { picker = null },
            )
        }
    }
}

/** The "opens a site or an app" tab: where the form comes from, the link, the app and the rare options. */
@Composable
private fun ActionForm(
    name: String,
    onName: (String) -> Unit,
    base: CustomAction,
    template: TextFieldValue,
    onTemplate: (TextFieldValue) -> Unit,
    arg: ArgKind,
    packages: List<String>,
    apps: List<AppEntry>,
    intentAction: String,
    onIntentAction: (String) -> Unit,
    shareText: Boolean,
    onShareText: (Boolean) -> Unit,
    smsFallback: Boolean,
    onSmsFallback: (Boolean) -> Unit,
    pattern: String,
    onPattern: (String) -> Unit,
    patternError: String?,
    moreOpen: Boolean,
    onMoreOpen: (Boolean) -> Unit,
    onPick: (EditorPicker) -> Unit,
) {
    val shortcut = base.kind == ActionKind.SHORTCUT
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        singleLine = true,
        label = { Text(stringResource(R.string.cmd_pref_name_label)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        stringResource(R.string.cmd_pref_start_from),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        OutlinedButton(onClick = { onPick(EditorPicker.Recipes) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.cmd_pref_recipes), maxLines = 1)
        }
        OutlinedButton(onClick = { onPick(EditorPicker.Apps) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.cmd_pref_app_functions), maxLines = 1)
        }
    }
    if (shortcut) {
        // An app's launcher shortcut has no link to edit: it is the shortcut of that app.
        Text(
            stringResource(CommandR.string.cmd_desc_shortcut, base.packages.firstOrNull().orEmpty()),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 16.dp),
        )
    } else {
        OutlinedTextField(
            value = template,
            onValueChange = onTemplate,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            label = { Text(stringResource(R.string.cmd_pref_link_label)) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        // Each chip puts its placeholder where the cursor is; what to type after the letter follows from them.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                R.string.cmd_pref_insert_text to "{text}",
                R.string.cmd_pref_insert_name to "{name}",
                R.string.cmd_pref_insert_phone to "{phone}",
                R.string.cmd_pref_insert_number to "{number}",
            ).forEach { (label, placeholder) ->
                SuggestionChip(
                    onClick = {
                        val at = template.selection.end.coerceIn(0, template.text.length)
                        val text = template.text.substring(0, at) + placeholder + template.text.substring(at)
                        onTemplate(TextFieldValue(text, TextRange(at + placeholder.length)))
                    },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        Text(
            stringResource(R.string.cmd_pref_manual_arg) + ": " + stringResource(argLabel(arg)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val anyApp = stringResource(R.string.cmd_pref_app_any)
        val appNames = packages.joinToString { pkg ->
            apps.firstOrNull { ComponentName.unflattenFromString(it.id)?.packageName == pkg }?.label ?: pkg
        }.ifEmpty { anyApp }
        Box(Modifier.padding(top = 12.dp)) {
            OutlinedTextField(
                value = appNames,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(stringResource(R.string.cmd_pref_app_label)) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
            // A read-only field swallows taps, so a transparent layer on top opens the list.
            Box(Modifier.matchParentSize().clickable { onPick(EditorPicker.Package) })
        }
    }
    TextButton(onClick = { onMoreOpen(!moreOpen) }, modifier = Modifier.padding(top = 8.dp)) {
        Icon(if (moreOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
        Text(stringResource(R.string.cmd_pref_more_options))
    }
    if (moreOpen) {
        if (!shortcut) {
            ActionPicker(action = intentAction, onChange = onIntentAction)
            CheckRow(stringResource(R.string.cmd_pref_manual_share), shareText, onShareText)
        }
        if (arg == ArgKind.CONTACT || arg == ArgKind.CONTACT_AND_TEXT) {
            CheckRow(stringResource(R.string.cmd_pref_sms_option), smsFallback, onSmsFallback)
        }
        // Text typed alone that matches it runs the action, without the letter.
        OutlinedTextField(
            value = pattern,
            onValueChange = onPattern,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            label = { Text(stringResource(R.string.cmd_pref_pattern_label)) },
            isError = patternError != null,
            supportingText = { Text(patternError ?: stringResource(R.string.cmd_pref_pattern_hint)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The alias tab: the command the word stands for and what it resolves to now. */
@Composable
private fun AliasForm(word: String, expansion: String, onExpansion: (String) -> Unit, error: String?, analysis: app.lawnchair.command.Analysis?) {
    OutlinedTextField(
        value = expansion,
        onValueChange = onExpansion,
        singleLine = true,
        label = { Text(stringResource(R.string.cmd_pref_alias_command)) },
        supportingText = { Text(stringResource(R.string.cmd_pref_alias_command_hint)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
    // An empty form needs no complaint; the error appears as soon as something is typed.
    if (error != null && (word.isNotBlank() || expansion.isNotBlank())) {
        Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
    }
    PreviewBox(
        analysis,
        intro = word.trim().takeIf { it.isNotEmpty() }?.let { stringResource(R.string.cmd_pref_alias_typing, it.lowercase()) },
    )
    if (error == null && analysis != null) {
        val alias = word.trim().lowercase()
        Text(
            stringResource(R.string.cmd_pref_alias_spoken, alias),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            stringResource(R.string.cmd_pref_alias_with_text, alias, expansion.trim()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
