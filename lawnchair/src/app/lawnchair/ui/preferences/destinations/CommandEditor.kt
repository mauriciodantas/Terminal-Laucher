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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.rememberCoroutineScope
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
import app.lawnchair.command.ACTION_VIEW
import app.lawnchair.command.ActionKind
import app.lawnchair.command.Aliases
import app.lawnchair.command.ApiCall
import app.lawnchair.command.ApiFormat
import app.lawnchair.command.ApiProvider
import app.lawnchair.command.ApiSpec
import app.lawnchair.command.AppEntry
import app.lawnchair.command.ArgKind
import app.lawnchair.command.BodyType
import app.lawnchair.command.CommandAction
import app.lawnchair.command.CommandAlias
import app.lawnchair.command.CommandEngine
import app.lawnchair.command.CommandExecutor
import app.lawnchair.command.CommandExtras
import app.lawnchair.command.CommandPattern
import app.lawnchair.command.ContactEntry
import app.lawnchair.command.CustomAction
import app.lawnchair.command.CustomActions
import app.lawnchair.command.HttpMethods
import app.lawnchair.command.HttpResult
import app.lawnchair.command.HttpRunner
import app.lawnchair.command.ProviderStore
import app.lawnchair.command.Providers
import app.lawnchair.command.R as CommandR
import app.lawnchair.command.SecretStore
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    data object Templates : EditorPicker
    data class Functions(val app: AppEntry) : EditorPicker
    data object Package : EditorPicker
}

private const val EXTRA_TEXT = "android.intent.extra.TEXT"

/** How much of the raw answer the API test shows, enough to see the names of the fields. */
private const val RAW_PREVIEW = 1500

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
    var help by remember { mutableStateOf(start.help) }
    // "Consulta uma API": a provider, a path on it and what of the answer to show.
    var apiMode by remember { mutableStateOf(start.kind == ActionKind.API) }
    var provider by remember { mutableStateOf(start.provider) }
    var format by remember { mutableStateOf(start.api?.format.orEmpty()) }
    var listPath by remember { mutableStateOf(start.api?.list.orEmpty()) }
    var maxItems by remember { mutableStateOf((start.api?.max ?: 5).toString()) }
    var upcoming by remember { mutableStateOf(start.api?.upcoming.orEmpty()) }
    var method by remember { mutableStateOf(start.api?.method ?: HttpMethods.GET) }
    var body by remember { mutableStateOf(TextFieldValue(start.api?.body.orEmpty())) }
    var bodyType by remember { mutableStateOf(start.api?.bodyType ?: BodyType.JSON) }
    // A test of a write sends it for real, so it asks first.
    var confirmTest by remember { mutableStateOf(false) }
    var apiResult by remember { mutableStateOf<Pair<String, String>?>(null) }
    var apiTesting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val providers by produceState(Providers.BUILT_IN) { value = withContext(Dispatchers.IO) { ProviderStore.all(context) } }
    val configured by produceState(emptySet<String>(), providers) {
        value = withContext(Dispatchers.IO) { SecretStore.configured(context, providers.map { it.id }) }
    }
    var expansion by remember { mutableStateOf(editingAlias?.expansion.orEmpty()) }
    // The rare options start open only when the action uses one of them.
    var moreOpen by remember {
        mutableStateOf(
            expandAction(start.intentAction) != expandAction("VIEW") ||
                shareText ||
                smsFallback ||
                start.pattern.isNotEmpty() ||
                start.api?.let { it.method != HttpMethods.GET || it.list.isNotEmpty() || it.upcoming.isNotEmpty() } == true,
        )
    }
    var apiDetailsOpen by remember { mutableStateOf(target == EditorTarget.New) }
    var picker by remember { mutableStateOf<EditorPicker?>(null) }

    fun suggest(label: String) {
        if (!wordTouched) word = suggestLetter(label, actions)
    }

    /** A recipe or an app's function fills the form; the letter and the name follow it while untouched. */
    fun fill(from: CustomAction) {
        base = from
        apiMode = false
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

    val shortcut = !apiMode && base.kind == ActionKind.SHORTCUT
    val arg = when {
        apiMode -> (template.text + " " + (if (method == HttpMethods.GET) "" else body.text)).let { sent ->
            // "{1}", "{2}": words of what is typed, so a text too.
            inferArg(sent, null).let { if (it == ArgKind.NONE && ApiCall.usesWords(sent)) ArgKind.TEXT else it }
        }

        shortcut -> base.arg

        else -> inferArg(template.text, if (shareText) EXTRA_TEXT else null)
    }
    val linkDraft = base.copy(
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
        help = help.trim(),
    ).let { if (it.kind == ActionKind.API) it.copy(kind = ActionKind.INTENT, provider = "", api = null) else it }
    val draft = if (apiMode) {
        linkDraft.copy(
            kind = ActionKind.API,
            packages = emptyList(),
            intentAction = ACTION_VIEW,
            mimeType = null,
            textExtra = null,
            smsFallback = false,
            provider = provider,
            api = ApiSpec(
                format = format.trim(),
                list = listPath.trim(),
                max = maxItems.toIntOrNull()?.coerceIn(1, ApiFormat.MAX_ITEMS) ?: 5,
                upcoming = upcoming.trim(),
                method = method,
                // A GET sends no body: whatever was typed for another method is not kept.
                body = if (method == HttpMethods.GET) "" else body.text.trim(),
                bodyType = bodyType,
            ),
        )
    } else {
        linkDraft
    }
    val letterError = CustomActions.validateLetter(word, actions, editingAction)
    val patternError = CommandPattern.validate(pattern.trim())
    val hasTarget = if (apiMode) {
        // A write may show only that it went through; a GET has to show something.
        provider.isNotEmpty() && template.text.isNotBlank() && (format.isNotBlank() || method != HttpMethods.GET)
    } else {
        shortcut || template.text.isNotBlank() || shareText
    }
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
        CommandEngine.analyze(
            previewLetter + if (sample.isBlank()) "" else " $sample",
            emptyList(),
            pool,
            true,
            listOf(draft.copy(letter = previewLetter)),
            emptyList(),
            CommandExtras(providers = providers, configuredProviders = configured),
        )
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

    /** Asks the API with the sample, as the bar would, and keeps what came back to show it. */
    fun testApi() {
        val p = Providers.of(provider, providers) ?: return
        val spec = draft.api ?: return
        apiTesting = true
        scope.launch {
            val today = java.time.LocalDate.now()
            val answer = withContext(Dispatchers.IO) {
                HttpRunner.send(p, ApiCall.path(draft, sample, today), SecretStore.get(context, p.id), spec.method, ApiCall.body(draft, sample, today), spec.bodyType)
            }
            apiResult = when (answer) {
                is HttpResult.Answer -> {
                    val shown = if (spec.format.isBlank() || answer.body.isBlank()) null else ApiFormat.render(answer.body, spec, today)
                    val wentThrough = spec.writes && (spec.format.isBlank() || answer.body.isBlank())
                    (shown ?: if (wentThrough) ApiCall.outcome(spec, answer.code, answer.body, today) else context.getString(R.string.cmd_pref_api_nothing)) to
                        answer.body.take(RAW_PREVIEW)
                }

                else -> HttpRunner.describe(answer, p) to ""
            }
            apiTesting = false
        }
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
                        // The name first: it is the command, whatever it does.
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                suggest(it)
                            },
                            singleLine = true,
                            label = { Text(stringResource(R.string.cmd_pref_name_label)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // One option a line: side by side the longer label wrapped and the two fell out of line.
                        // Only a new command chooses its kind: a saved command keeps it.
                        if (target == EditorTarget.New) {
                            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                listOf(false to R.string.cmd_pref_kind_open, true to R.string.cmd_pref_kind_api).forEach { (api, label) ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 44.dp)
                                            .clickable {
                                                apiMode = api
                                                apiResult = null
                                            },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(selected = apiMode == api, onClick = null, modifier = Modifier.padding(end = 6.dp))
                                        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                    if (tab == EditorTab.ACTION && apiMode) {
                        ApiForm(
                            providers = providers,
                            configured = configured,
                            provider = provider,
                            onProvider = {
                                provider = it
                                apiResult = null
                            },
                            path = template,
                            onPath = {
                                template = it
                                apiResult = null
                            },
                            format = format,
                            onFormat = {
                                format = it
                                apiResult = null
                            },
                            listPath = listPath,
                            onListPath = { listPath = it },
                            maxItems = maxItems,
                            onMaxItems = { maxItems = it.filter { c -> c.isDigit() }.take(2) },
                            upcoming = upcoming,
                            onUpcoming = { upcoming = it },
                            method = method,
                            onMethod = {
                                method = it
                                apiResult = null
                            },
                            body = body,
                            onBody = {
                                body = it
                                apiResult = null
                            },
                            bodyType = bodyType,
                            onBodyType = { bodyType = it },
                            pattern = pattern,
                            onPattern = { pattern = it.take(CommandPattern.MAX_LENGTH) },
                            patternError = patternError,
                            detailsOpen = apiDetailsOpen,
                            onDetailsOpen = { apiDetailsOpen = it },
                            moreOpen = moreOpen,
                            onMoreOpen = { moreOpen = it },
                        )
                    }
                    if (tab == EditorTab.ACTION && !apiMode) {
                        ActionForm(
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
                    }
                    if (tab == EditorTab.ACTION) {
                        OutlinedTextField(
                            value = help,
                            onValueChange = { help = it.take(CustomActions.MAX_HELP) },
                            minLines = 1,
                            maxLines = 4,
                            label = { Text(stringResource(R.string.cmd_pref_help_label)) },
                            supportingText = { Text(stringResource(R.string.cmd_pref_help_hint)) },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
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
                        if (apiMode) ApiResult(apiTesting, apiResult)
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
                    TextButton(
                        enabled = if (apiMode && tab == EditorTab.ACTION) hasTarget && !apiTesting else runnable != null,
                        onClick = {
                            when {
                                !(apiMode && tab == EditorTab.ACTION) -> runnable?.let(onTest)
                                method != HttpMethods.GET -> confirmTest = true
                                else -> testApi()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.cmd_pref_test))
                    }
                }
            }
        }
    }

    if (confirmTest) {
        AlertDialog(
            onDismissRequest = { confirmTest = false },
            title = { Text(stringResource(R.string.cmd_pref_api_test_write_title, method)) },
            text = { Text(stringResource(R.string.cmd_pref_api_test_write, method, Providers.of(provider, providers)?.host.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    confirmTest = false
                    testApi()
                }) { Text(stringResource(R.string.cmd_pref_api_test_write_ok, method)) }
            },
            dismissButton = { TextButton(onClick = { confirmTest = false }) { Text(stringResource(R.string.cmd_pref_cancel)) } },
        )
    }

    when (val p = picker) {
        null -> Unit

        EditorPicker.Templates -> TemplatePicker(
            apps = apps,
            onRecipe = {
                fill(it)
                picker = null
            },
            onApp = { picker = EditorPicker.Functions(it) },
            onDismiss = { picker = null },
        )

        is EditorPicker.Functions -> {
            val pkg = ComponentName.unflattenFromString(p.app.id)?.packageName.orEmpty()
            val found = remember(pkg) { CommandExecutor.loadIntegrations(context, pkg) }
            if (found.isEmpty()) {
                AlertDialog(
                    onDismissRequest = { picker = null },
                    confirmButton = { TextButton(onClick = { picker = EditorPicker.Templates }) { Text(stringResource(R.string.cmd_pref_back)) } },
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
    OutlinedButton(onClick = { onPick(EditorPicker.Templates) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(stringResource(R.string.cmd_pref_start_template))
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

/** The "consulta uma API" form: the provider, the path on it and what of the answer to show. */
@Composable
private fun ApiForm(
    providers: List<ApiProvider>,
    configured: Set<String>,
    provider: String,
    onProvider: (String) -> Unit,
    path: TextFieldValue,
    onPath: (TextFieldValue) -> Unit,
    format: String,
    onFormat: (String) -> Unit,
    listPath: String,
    onListPath: (String) -> Unit,
    maxItems: String,
    onMaxItems: (String) -> Unit,
    upcoming: String,
    onUpcoming: (String) -> Unit,
    method: String,
    onMethod: (String) -> Unit,
    body: TextFieldValue,
    onBody: (TextFieldValue) -> Unit,
    bodyType: BodyType,
    onBodyType: (BodyType) -> Unit,
    pattern: String,
    onPattern: (String) -> Unit,
    patternError: String?,
    detailsOpen: Boolean,
    onDetailsOpen: (Boolean) -> Unit,
    moreOpen: Boolean,
    onMoreOpen: (Boolean) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val chosen = Providers.of(provider, providers)
    val noKey = stringResource(R.string.cmd_providers_key_missing)
    val mono = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    Box(Modifier.padding(top = 12.dp)) {
        OutlinedTextField(
            value = chosen?.let { it.name + " · " + it.host } ?: provider,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.cmd_pref_api_provider)) },
            placeholder = { Text(stringResource(R.string.cmd_pref_api_pick_provider)) },
            isError = provider.isNotEmpty() && chosen == null,
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // A read-only field swallows taps, so a transparent layer on top opens the list.
        Box(Modifier.matchParentSize().clickable { picking = true })
    }
    val note = when {
        chosen == null -> stringResource(R.string.cmd_pref_api_where_providers)
        chosen.auth.needsSecret && chosen.id !in configured -> stringResource(R.string.cmd_pref_api_no_key, chosen.name)
        else -> null
    }
    note?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = if (chosen != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clickable { onDetailsOpen(!detailsOpen) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (detailsOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 6.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.cmd_pref_api_details),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!detailsOpen && path.text.isNotBlank()) {
                Text(
                    "$method · ${path.text.trim()}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }

    if (detailsOpen) {
        OutlinedTextField(
            value = path,
            onValueChange = onPath,
            singleLine = true,
            textStyle = mono,
            label = { Text(stringResource(R.string.cmd_pref_api_path)) },
            placeholder = { Text("/ws/{text}/json/", fontFamily = FontFamily.Monospace) },
            supportingText = { Text(chosen?.let { stringResource(R.string.cmd_pref_api_path_hint, it.baseUrl) } ?: "") },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(R.string.cmd_pref_insert_text to "{text}", R.string.cmd_pref_insert_year to "{year}").forEach { (label, placeholder) ->
                SuggestionChip(
                    onClick = {
                        val at = path.selection.end.coerceIn(0, path.text.length)
                        onPath(TextFieldValue(path.text.substring(0, at) + placeholder + path.text.substring(at), TextRange(at + placeholder.length)))
                    },
                    label = { Text(stringResource(label)) },
                )
            }
        }
        OutlinedTextField(
            value = format,
            onValueChange = onFormat,
            minLines = 2,
            textStyle = mono,
            label = { Text(stringResource(if (method == HttpMethods.GET) R.string.cmd_pref_api_format else R.string.cmd_pref_api_format_optional)) },
            placeholder = { Text("{bairro} · {localidade}/{uf}", fontFamily = FontFamily.Monospace) },
            supportingText = { Text(stringResource(R.string.cmd_pref_api_format_hint)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        TextButton(onClick = { onMoreOpen(!moreOpen) }, modifier = Modifier.padding(top = 8.dp)) {
            Icon(if (moreOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
            Text(stringResource(R.string.cmd_pref_more_options))
        }
        if (moreOpen) {
            Text(
                stringResource(R.string.cmd_pref_api_method),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                HttpMethods.ALL.forEach { m ->
                    FilterChip(
                        selected = method == m,
                        onClick = { onMethod(m) },
                        label = { Text(m, fontFamily = FontFamily.Monospace) },
                    )
                }
            }
            if (method != HttpMethods.GET) {
                Text(
                    stringResource(if (HttpMethods.confirms(method)) R.string.cmd_pref_api_method_confirm else R.string.cmd_pref_api_method_writes, method),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.cmd_pref_api_body_type), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                    BodyType.entries.forEach { type ->
                        FilterChip(
                            selected = bodyType == type,
                            onClick = { onBodyType(type) },
                            label = { Text(type.name.lowercase(), fontFamily = FontFamily.Monospace) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = body,
                    onValueChange = onBody,
                    minLines = 3,
                    textStyle = mono,
                    label = { Text(stringResource(R.string.cmd_pref_api_body)) },
                    placeholder = {
                        Text(
                            when (bodyType) {
                                BodyType.JSON -> "{\"title\": \"{text}\"}"
                                BodyType.FORM -> "title={text}&done=false"
                                BodyType.TEXT -> "{text}"
                            },
                            fontFamily = FontFamily.Monospace,
                        )
                    },
                    supportingText = { Text(stringResource(R.string.cmd_pref_api_body_hint)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(R.string.cmd_pref_insert_text to "{text}", R.string.cmd_pref_insert_year to "{year}").forEach { (label, placeholder) ->
                        SuggestionChip(
                            onClick = {
                                val at = body.selection.end.coerceIn(0, body.text.length)
                                onBody(TextFieldValue(body.text.substring(0, at) + placeholder + body.text.substring(at), TextRange(at + placeholder.length)))
                            },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = listPath,
                onValueChange = onListPath,
                singleLine = true,
                textStyle = mono,
                label = { Text(stringResource(R.string.cmd_pref_api_list)) },
                supportingText = { Text(stringResource(R.string.cmd_pref_api_list_hint)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = maxItems,
                onValueChange = onMaxItems,
                singleLine = true,
                label = { Text(stringResource(R.string.cmd_pref_api_max)) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = upcoming,
                onValueChange = onUpcoming,
                singleLine = true,
                textStyle = mono,
                label = { Text(stringResource(R.string.cmd_pref_api_upcoming)) },
                supportingText = { Text(stringResource(R.string.cmd_pref_api_upcoming_hint)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = pattern,
                onValueChange = onPattern,
                singleLine = true,
                textStyle = mono,
                label = { Text(stringResource(R.string.cmd_pref_pattern_label)) },
                isError = patternError != null,
                supportingText = { Text(patternError ?: stringResource(R.string.cmd_pref_pattern_hint)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
    if (picking) {
        PickerDialog(
            title = stringResource(R.string.cmd_pref_api_provider),
            items = providers,
            label = { it.name + " · " + it.host + if (it.auth.needsSecret && it.id !in configured) " · $noKey" else "" },
            onPick = {
                onProvider(it.id)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** What the API test brought: the text the bar would show, then the start of the raw answer. */
@Composable
private fun ApiResult(testing: Boolean, result: Pair<String, String>?) {
    if (!testing && result == null) return
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(stringResource(R.string.cmd_pref_api_result), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            if (testing) stringResource(R.string.command_running) else result?.first.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp),
        )
        val raw = result?.second.orEmpty()
        if (!testing && raw.isNotEmpty()) {
            Text(
                stringResource(R.string.cmd_pref_api_raw),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(raw, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * One list to start from: the ready-made recipes of the apps that are installed (and SMS, which
 * belongs to no app) first, then every app with something to bind, whose functions open next.
 */
@Composable
private fun TemplatePicker(apps: List<AppEntry>, onRecipe: (CustomAction) -> Unit, onApp: (AppEntry) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val installed = remember(apps) { apps.mapNotNull { ComponentName.unflattenFromString(it.id)?.packageName }.toSet() }
    // A recipe whose app is missing could not open, so it is left out.
    val suggestions = remember(installed) {
        CustomActions.CATALOG.filter { recipe -> recipe.packages.isEmpty() || recipe.packages.any { it in installed } }
    }
    // Scanning every installed app takes a moment, so it runs off the main thread.
    val integrationApps by produceState<List<AppEntry>?>(initialValue = null) {
        value = withContext(Dispatchers.Default) { CommandExecutor.loadIntegrationApps(context) }
    }

    @Composable
    fun Header(text: Int) {
        Text(
            stringResource(text),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }

    @Composable
    fun Entry(text: String, onClick: () -> Unit) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cmd_pref_cancel)) } },
        title = { Text(stringResource(R.string.cmd_pref_start_template)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                if (suggestions.isNotEmpty()) {
                    item { Header(R.string.cmd_pref_suggestions) }
                    items(suggestions) { recipe -> Entry("${recipe.label} · ${recipe.usage.substringBefore(" ·")}") { onRecipe(recipe) } }
                }
                item { Header(R.string.cmd_pref_apps_title) }
                val list = integrationApps
                when {
                    list == null -> item { Text(stringResource(R.string.cmd_pref_searching_apps), style = MaterialTheme.typography.bodySmall) }
                    list.isEmpty() -> item { Text(stringResource(R.string.cmd_pref_no_integrations), style = MaterialTheme.typography.bodySmall) }
                    else -> items(list) { app -> Entry(app.label) { onApp(app) } }
                }
            }
        },
    )
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
