package app.lawnchair.ui.preferences.destinations

import android.content.ComponentName
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.lawnchair.command.ACTION_VIEW
import app.lawnchair.command.ActionKind
import app.lawnchair.command.ApiCall
import app.lawnchair.command.ApiFormat
import app.lawnchair.command.ApiProvider
import app.lawnchair.command.ApiSpec
import app.lawnchair.command.AppEntry
import app.lawnchair.command.ArgKind
import app.lawnchair.command.BodyType
import app.lawnchair.command.CommandAction
import app.lawnchair.command.CommandAlias
import app.lawnchair.command.CommandLine
import app.lawnchair.command.ContactEntry
import app.lawnchair.command.CustomAction
import app.lawnchair.command.ExtraType
import app.lawnchair.command.HttpResult
import app.lawnchair.command.HttpRunner
import app.lawnchair.command.IntentCatalog
import app.lawnchair.command.IntentExtra
import app.lawnchair.command.ParseResult
import app.lawnchair.command.ParsedCommand
import app.lawnchair.command.ProviderStore
import app.lawnchair.command.Providers
import app.lawnchair.command.R as CommandR
import app.lawnchair.command.SecretStore
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.navigation.CommandProviders
import com.android.launcher3.R
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** What the command editor opens on: a saved action, a saved alias, or nothing for a new command. */
internal sealed interface EditorTarget {
    data object New : EditorTarget
    data class Action(val action: CustomAction) : EditorTarget
    data class Alias(val alias: CommandAlias) : EditorTarget
}

enum class CommandType(val label: String) {
    LINK("Link"),
    APP("App"),
    INTENT("Intent"),
    API("API"),
    ALIAS("Apelido"),
}

internal fun inferArg(template: String, textExtra: String? = null): ArgKind {
    val text = "{text}" in template || textExtra != null || (1..9).any { "{$it}" in template }
    val contact = listOf("{name}", "{phone}", "{number}").any { it in template }
    return when {
        text && contact -> ArgKind.CONTACT_AND_TEXT
        contact -> ArgKind.CONTACT
        text -> ArgKind.TEXT
        else -> ArgKind.NONE
    }
}

/**
 * Unified command editor screen with bidirectional line/fields synchronization.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    val editingAction = (target as? EditorTarget.Action)?.action
    val editingAlias = (target as? EditorTarget.Alias)?.alias

    val providers by produceState(Providers.BUILT_IN) {
        value = withContext(Dispatchers.IO) { ProviderStore.all(context) }
    }
    val configured by produceState(emptySet<String>(), providers) {
        value = withContext(Dispatchers.IO) { SecretStore.configured(context, providers.map { it.id }) }
    }

    // Determine initial type
    val initialType = when {
        editingAction?.kind == ActionKind.API -> CommandType.API
        editingAction != null && editingAction.packages.isNotEmpty() && editingAction.template.isEmpty() -> CommandType.APP
        editingAlias?.expansion?.startsWith("abrir ") == true -> CommandType.APP
        editingAlias != null -> CommandType.ALIAS
        editingAction != null && (editingAction.intentAction != ACTION_VIEW || editingAction.extras.isNotEmpty() || editingAction.target != "activity" || editingAction.mimeType != null) -> CommandType.INTENT
        else -> CommandType.LINK
    }

    // Form fields state
    var name by remember { mutableStateOf(editingAction?.letter ?: editingAlias?.name.orEmpty()) }
    var label by remember { mutableStateOf(editingAction?.label ?: "") }
    var type by remember { mutableStateOf(initialType) }

    // Link & Intent & API fields
    var template by remember { mutableStateOf(editingAction?.template ?: "") }
    var smsFallback by remember { mutableStateOf(editingAction?.smsFallback ?: false) }

    // App type
    var selectedAppPackage by remember {
        mutableStateOf(
            editingAction?.packages?.firstOrNull()
                ?: if (editingAlias?.expansion?.startsWith("abrir ") == true) {
                    val appLabel = editingAlias.expansion.removePrefix("abrir ").trim()
                    apps.firstOrNull { it.label.equals(appLabel, ignoreCase = true) }?.id.orEmpty()
                } else {
                    ""
                },
        )
    }

    // Intent fields
    var intentAction by remember { mutableStateOf(editingAction?.intentAction ?: ACTION_VIEW) }
    var intentTarget by remember { mutableStateOf(editingAction?.target ?: "activity") }
    var intentPackages by remember { mutableStateOf(editingAction?.packages ?: emptyList()) }
    var mimeType by remember { mutableStateOf(editingAction?.mimeType.orEmpty()) }
    var extras by remember { mutableStateOf(editingAction?.extras ?: emptyList()) }
    var categories by remember { mutableStateOf(editingAction?.categories ?: emptyList()) }
    var component by remember { mutableStateOf(editingAction?.component.orEmpty()) }

    // API fields
    var provider by remember { mutableStateOf(editingAction?.provider.orEmpty()) }
    var apiMethod by remember { mutableStateOf(editingAction?.api?.method ?: "GET") }
    var apiHeaders by remember { mutableStateOf(editingAction?.api?.headers ?: emptyMap()) }
    var apiBody by remember { mutableStateOf(editingAction?.api?.body.orEmpty()) }
    var apiBodyType by remember { mutableStateOf(editingAction?.api?.bodyType ?: BodyType.JSON) }
    var apiFormat by remember { mutableStateOf(editingAction?.api?.format.orEmpty()) }
    var apiList by remember { mutableStateOf(editingAction?.api?.list.orEmpty()) }
    var apiMax by remember { mutableIntStateOf(editingAction?.api?.max ?: 5) }
    var apiUpcoming by remember { mutableStateOf(editingAction?.api?.upcoming.orEmpty()) }
    var pattern by remember { mutableStateOf(editingAction?.pattern.orEmpty()) }

    // Alias fields
    var expansion by remember { mutableStateOf(editingAlias?.expansion.orEmpty()) }

    // Synchronization state
    val initialLine = when {
        editingAction != null -> CommandLine.serialize(editingAction)
        editingAlias != null -> CommandLine.serialize(editingAlias)
        else -> ""
    }
    var line by remember { mutableStateOf(initialLine) }
    var lineError by remember { mutableStateOf<String?>(null) }
    var isSyncingFromFields by remember { mutableStateOf(false) }
    var isSyncingFromLine by remember { mutableStateOf(false) }

    // Builder function for current form state
    fun buildCommand(): ParsedCommand {
        val cleanName = name.trim().lowercase()
        val cleanLabel = label.trim()
        return when (type) {
            CommandType.LINK -> {
                val cleanTemplate = template.trim()
                val arg = inferArg(cleanTemplate)
                val action = CustomAction(
                    letter = cleanName,
                    label = cleanLabel.ifEmpty { cleanName },
                    kind = ActionKind.INTENT,
                    template = cleanTemplate,
                    packages = emptyList(),
                    arg = arg,
                    smsFallback = smsFallback,
                    intentAction = ACTION_VIEW,
                )
                ParsedCommand.Action(cleanName, cleanLabel, action)
            }

            CommandType.APP -> {
                val app = apps.firstOrNull { it.id == selectedAppPackage }
                val appLabel = app?.label ?: selectedAppPackage
                val al = CommandAlias(name = cleanName, expansion = "abrir $appLabel")
                ParsedCommand.Alias(cleanName, cleanLabel, al)
            }

            CommandType.INTENT -> {
                val cleanTemplate = template.trim()
                val allText = cleanTemplate + " " + extras.joinToString(" ") { it.value }
                val arg = inferArg(allText)
                val action = CustomAction(
                    letter = cleanName,
                    label = cleanLabel.ifEmpty { cleanName },
                    kind = ActionKind.INTENT,
                    template = cleanTemplate,
                    packages = intentPackages,
                    arg = arg,
                    intentAction = intentAction.trim().ifEmpty { ACTION_VIEW },
                    mimeType = mimeType.trim().ifEmpty { null },
                    target = intentTarget,
                    categories = categories,
                    component = component.trim().ifEmpty { null },
                    extras = extras,
                )
                ParsedCommand.Action(cleanName, cleanLabel, action)
            }

            CommandType.API -> {
                val cleanTemplate = template.trim()
                val spec = ApiSpec(
                    format = apiFormat.trim(),
                    method = apiMethod,
                    headers = apiHeaders,
                    body = apiBody,
                    bodyType = apiBodyType,
                    list = apiList.trim(),
                    max = apiMax,
                    upcoming = apiUpcoming.trim(),
                )
                val action = CustomAction(
                    letter = cleanName,
                    label = cleanLabel.ifEmpty { cleanName },
                    kind = ActionKind.API,
                    template = cleanTemplate,
                    packages = emptyList(),
                    arg = ArgKind.TEXT,
                    provider = provider.trim(),
                    api = spec,
                    pattern = pattern.trim(),
                )
                ParsedCommand.Action(cleanName, cleanLabel, action)
            }

            CommandType.ALIAS -> {
                val al = CommandAlias(name = cleanName, expansion = expansion.trim())
                ParsedCommand.Alias(cleanName, cleanLabel, al)
            }
        }
    }

    // Sync from fields to line
    fun syncFieldsToLine() {
        if (isSyncingFromLine) return
        val cmd = buildCommand()
        val generated = CommandLine.serialize(cmd)
        if (generated != line) {
            isSyncingFromFields = true
            line = generated
            lineError = null
            isSyncingFromFields = false
        }
    }

    // Parse debounced from line to fields (300ms)
    LaunchedEffect(line) {
        if (isSyncingFromFields) return@LaunchedEffect
        delay(300)
        if (line.isBlank()) {
            lineError = null
            return@LaunchedEffect
        }
        val res = CommandLine.parse(line, actions, aliases)
        when (res) {
            is ParseResult.Success -> {
                lineError = null
                isSyncingFromLine = true
                val cmd = res.command
                name = cmd.name
                label = cmd.label
                when (cmd) {
                    is ParsedCommand.Action -> {
                        val a = cmd.action
                        when {
                            a.kind == ActionKind.API -> {
                                type = CommandType.API
                                provider = a.provider
                                template = a.template
                                apiMethod = a.api?.method ?: "GET"
                                apiHeaders = a.api?.headers ?: emptyMap()
                                apiBody = a.api?.body.orEmpty()
                                apiBodyType = a.api?.bodyType ?: BodyType.JSON
                                apiFormat = a.api?.format.orEmpty()
                                apiList = a.api?.list.orEmpty()
                                apiMax = a.api?.max ?: 5
                                apiUpcoming = a.api?.upcoming.orEmpty()
                                pattern = a.pattern
                            }

                            a.packages.isNotEmpty() && a.template.isEmpty() && a.intentAction in listOf(ACTION_VIEW, "android.intent.action.MAIN") -> {
                                type = CommandType.APP
                                selectedAppPackage = a.packages.firstOrNull().orEmpty()
                            }

                            a.intentAction == ACTION_VIEW && a.packages.isEmpty() && a.extras.isEmpty() && a.mimeType == null && a.target == "activity" -> {
                                type = CommandType.LINK
                                template = a.template
                                smsFallback = a.smsFallback
                            }

                            else -> {
                                type = CommandType.INTENT
                                intentAction = a.intentAction
                                intentTarget = a.target
                                intentPackages = a.packages
                                template = a.template
                                mimeType = a.mimeType.orEmpty()
                                extras = a.extras
                                categories = a.categories
                                component = a.component.orEmpty()
                            }
                        }
                    }

                    is ParsedCommand.Alias -> {
                        val al = cmd.alias
                        if (al.expansion.startsWith("abrir ")) {
                            val targetLabel = al.expansion.removePrefix("abrir ").trim()
                            val app = apps.firstOrNull { it.label.equals(targetLabel, ignoreCase = true) }
                            if (app != null) {
                                type = CommandType.APP
                                selectedAppPackage = app.id
                            } else {
                                type = CommandType.ALIAS
                                expansion = al.expansion
                            }
                        } else {
                            type = CommandType.ALIAS
                            expansion = al.expansion
                        }
                    }
                }
                isSyncingFromLine = false
            }

            is ParseResult.Failure -> {
                lineError = res.error.message
            }
        }
    }

    // Validation
    val currentCmd = buildCommand()
    val validation = remember(currentCmd, actions, aliases, providers) {
        CommandLine.validate(
            command = currentCmd,
            existingActions = actions,
            existingAliases = aliases,
            providers = providers,
            ignore = editingAction ?: editingAlias,
        )
    }
    val canSave = validation.isValid && name.isNotBlank() && lineError == null

    // Modals state
    var appPickerOpen by remember { mutableStateOf(false) }
    var catalogPickerOpen by remember { mutableStateOf(false) }
    var confirmWriteAlert by remember { mutableStateOf<String?>(null) }
    var confirmDeleteAlert by remember { mutableStateOf(false) }

    // Test section state
    var sampleArg by remember { mutableStateOf("") }
    var testRunning by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var testRawJson by remember { mutableStateOf<String?>(null) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var testLatency by remember { mutableStateOf<Long?>(null) }
    var testError by remember { mutableStateOf<String?>(null) }

    // Save action
    fun save() {
        if (!canSave) return
        when (val cmd = buildCommand()) {
            is ParsedCommand.Action -> onSaveAction(cmd.action)
            is ParsedCommand.Alias -> onSaveAlias(cmd.alias)
        }
    }

    // Run test function
    fun runTest() {
        when (type) {
            CommandType.LINK, CommandType.INTENT -> {
                val cmd = buildCommand()
                if (cmd is ParsedCommand.Action) {
                    onTest(CommandAction.Custom(action = cmd.action, contact = null, text = sampleArg))
                    testResultText = "Intent disparado com sucesso"
                }
            }

            CommandType.APP -> {
                val app = apps.firstOrNull { it.id == selectedAppPackage }
                if (app != null) {
                    val pkg = ComponentName.unflattenFromString(app.id)?.packageName ?: app.id
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                        testResultText = "App ${app.label} aberto"
                    } else {
                        testError = "Não foi possível abrir o app"
                    }
                }
            }

            CommandType.ALIAS -> {
                testResultText = "Apelido: $name → $expansion"
            }

            CommandType.API -> {
                val cmd = buildCommand()
                if (cmd !is ParsedCommand.Action) return
                val action = cmd.action
                val p = Providers.of(action.provider, providers)
                if (p == null) {
                    testError = "Provedor \"${action.provider}\" não encontrado"
                    return
                }
                val spec = action.api ?: return
                testRunning = true
                testError = null
                testResultText = null
                testRawJson = null
                scope.launch {
                    val t0 = System.currentTimeMillis()
                    val today = LocalDate.now()
                    val answer = withContext(Dispatchers.IO) {
                        HttpRunner.send(
                            p,
                            ApiCall.path(action, sampleArg, today),
                            SecretStore.get(context, p.id),
                            spec.method,
                            ApiCall.body(action, sampleArg, today),
                            spec.bodyType,
                            spec.headers,
                        )
                    }
                    val dt = System.currentTimeMillis() - t0
                    testLatency = dt
                    testRunning = false
                    when (answer) {
                        is HttpResult.Answer -> {
                            testStatus = "HTTP ${answer.code}"
                            testRawJson = answer.body
                            val rendered = if (spec.format.isNotBlank() && answer.body.isNotBlank()) {
                                ApiFormat.render(answer.body, spec, today)
                            } else {
                                null
                            }
                            testResultText = rendered ?: answer.body.take(500)
                        }

                        else -> {
                            testStatus = "FALHOU"
                            testError = HttpRunner.describe(answer, p)
                        }
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding(),
            ) {
                // 1. Top Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cmd_pref_cancel))
                    }
                    Text(
                        text = stringResource(if (target == EditorTarget.New) R.string.cmd_pref_new_command else R.string.cmd_pref_edit_command),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (target != EditorTarget.New) {
                        IconButton(onClick = { confirmDeleteAlert = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cmd_pref_remove), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(enabled = canSave, onClick = ::save) {
                        Text(stringResource(R.string.cmd_pref_save), fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider()

                // Scrollable Content
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    // 2. MONO LINE
                    Text(
                        "LINHA DE COMANDO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = line,
                        onValueChange = { line = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        isError = lineError != null,
                        supportingText = {
                            if (lineError != null) {
                                Text(lineError!!, color = MaterialTheme.colorScheme.error)
                            }
                        },
                    )

                    Spacer(Modifier.height(12.dp))

                    // 3. TIPO (Segmented Control)
                    Text(
                        "TIPO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        CommandType.entries.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = type == option,
                                onClick = {
                                    type = option
                                    syncFieldsToLine()
                                },
                                shape = SegmentedButtonDefaults.itemShape(index, CommandType.entries.size),
                            ) {
                                Text(option.label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Common fields: Nome & Rótulo
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                syncFieldsToLine()
                            },
                            label = { Text("Nome (letra)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = label,
                            onValueChange = {
                                label = it
                                syncFieldsToLine()
                            },
                            label = { Text("Rótulo (# comentário)") },
                            modifier = Modifier.weight(1.5f),
                            singleLine = true,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // 4. Campos por Tipo
                    when (type) {
                        CommandType.LINK -> {
                            var phMenuOpen by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = template,
                                onValueChange = {
                                    template = it
                                    syncFieldsToLine()
                                },
                                label = { Text("Link ou URI") },
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    Box {
                                        IconButton(onClick = { phMenuOpen = true }) {
                                            Text("{}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
                                        PlaceholderDropdown(
                                            expanded = phMenuOpen,
                                            onDismiss = { phMenuOpen = false },
                                            onInsert = { ph ->
                                                template += ph
                                                phMenuOpen = false
                                                syncFieldsToLine()
                                            },
                                        )
                                    }
                                },
                                supportingText = { Text("{text} recebe o que vier depois do nome") },
                            )
                            if (template.contains("{number}") || template.contains("{phone}")) {
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        "Abrir SMS se o app não estiver instalado",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Switch(
                                        checked = smsFallback,
                                        onCheckedChange = {
                                            smsFallback = it
                                            syncFieldsToLine()
                                        },
                                    )
                                }
                            }
                        }

                        CommandType.APP -> {
                            val currentApp = apps.firstOrNull { it.id == selectedAppPackage }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { appPickerOpen = true },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(16.dp),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            currentApp?.label ?: "Nenhum app selecionado",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (currentApp != null) {
                                            Text(
                                                currentApp.id,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Button(onClick = { appPickerOpen = true }) {
                                        Text(if (currentApp == null) "Escolher App" else "Trocar")
                                    }
                                }
                            }
                        }

                        CommandType.INTENT -> {
                            // Action field + Catalog button
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = intentAction,
                                    onValueChange = {
                                        intentAction = it
                                        syncFieldsToLine()
                                    },
                                    label = { Text("Ação da Intent") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                )
                                OutlinedButton(onClick = { catalogPickerOpen = true }) {
                                    Text("Catálogo")
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Iniciar / Broadcast
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Destino:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                                FilterChip(
                                    selected = intentTarget == "activity",
                                    onClick = {
                                        intentTarget = "activity"
                                        syncFieldsToLine()
                                    },
                                    label = { Text("Activity") },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                FilterChip(
                                    selected = intentTarget == "broadcast",
                                    onClick = {
                                        intentTarget = "broadcast"
                                        syncFieldsToLine()
                                    },
                                    label = { Text("Broadcast") },
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            // Abre com (Package)
                            val openWithApp = apps.firstOrNull { it.id == intentPackages.firstOrNull() }
                            OutlinedTextField(
                                value = openWithApp?.label ?: intentPackages.firstOrNull() ?: "Qualquer app",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Abre com") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { appPickerOpen = true },
                                trailingIcon = {
                                    IconButton(onClick = { appPickerOpen = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Escolher")
                                    }
                                },
                            )

                            Spacer(Modifier.height(8.dp))

                            // Data URI & MIME
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                var phMenuOpen by remember { mutableStateOf(false) }
                                OutlinedTextField(
                                    value = template,
                                    onValueChange = {
                                        template = it
                                        syncFieldsToLine()
                                    },
                                    label = { Text("Dado (URI)") },
                                    modifier = Modifier.weight(1.5f),
                                    trailingIcon = {
                                        Box {
                                            IconButton(onClick = { phMenuOpen = true }) {
                                                Text("{}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            }
                                            PlaceholderDropdown(
                                                expanded = phMenuOpen,
                                                onDismiss = { phMenuOpen = false },
                                                onInsert = { ph ->
                                                    template += ph
                                                    phMenuOpen = false
                                                    syncFieldsToLine()
                                                },
                                            )
                                        }
                                    },
                                )
                                OutlinedTextField(
                                    value = mimeType,
                                    onValueChange = {
                                        mimeType = it
                                        syncFieldsToLine()
                                    },
                                    label = { Text("Tipo MIME") },
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            // Extras Table
                            Text(
                                "EXTRAS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            extras.forEachIndexed { idx, extra ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedTextField(
                                        value = extra.key,
                                        onValueChange = { k ->
                                            extras = extras.toMutableList().also { it[idx] = extra.copy(key = k) }
                                            syncFieldsToLine()
                                        },
                                        placeholder = { Text("Chave") },
                                        modifier = Modifier.weight(1.2f),
                                        singleLine = true,
                                    )
                                    // Type dropdown
                                    var typeMenuOpen by remember { mutableStateOf(false) }
                                    Box(Modifier.weight(0.9f)) {
                                        OutlinedButton(onClick = { typeMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                                            Text(extra.type.id, maxLines = 1)
                                        }
                                        DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                                            ExtraType.entries.forEach { et ->
                                                DropdownMenuItem(
                                                    text = { Text(et.id) },
                                                    onClick = {
                                                        extras = extras.toMutableList().also { it[idx] = extra.copy(type = et) }
                                                        typeMenuOpen = false
                                                        syncFieldsToLine()
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    OutlinedTextField(
                                        value = extra.value,
                                        onValueChange = { v ->
                                            extras = extras.toMutableList().also { it[idx] = extra.copy(value = v) }
                                            syncFieldsToLine()
                                        },
                                        placeholder = { Text("Valor") },
                                        modifier = Modifier.weight(1.2f),
                                        singleLine = true,
                                    )
                                    IconButton(onClick = {
                                        extras = extras.toMutableList().also { it.removeAt(idx) }
                                        syncFieldsToLine()
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remover", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            TextButton(
                                onClick = {
                                    extras = extras + IntentExtra("", ExtraType.STRING, "")
                                    syncFieldsToLine()
                                },
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Adicionar extra")
                            }
                            Text(
                                "Extras com valor vazio não vão na intent",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        CommandType.API -> {
                            // Provedor
                            var provMenuOpen by remember { mutableStateOf(false) }
                            Box(Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = provider.ifEmpty { "Selecione um provedor" },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Provedor") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { provMenuOpen = true },
                                    trailingIcon = {
                                        IconButton(onClick = { provMenuOpen = true }) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Provedor")
                                        }
                                    },
                                )
                                DropdownMenu(expanded = provMenuOpen, onDismissRequest = { provMenuOpen = false }) {
                                    providers.forEach { p ->
                                        val hasKey = configured.contains(p.id)
                                        DropdownMenuItem(
                                            text = { Text("${p.name} (@${p.id}) · ${if (hasKey) "Chave salva" else "Sem chave"}") },
                                            onClick = {
                                                provider = p.id
                                                provMenuOpen = false
                                                syncFieldsToLine()
                                            },
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("+ Gerenciar Provedores") },
                                        onClick = {
                                            provMenuOpen = false
                                            navController.navigate(CommandProviders())
                                        },
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Método & Caminho
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                var methodMenuOpen by remember { mutableStateOf(false) }
                                Box(Modifier.weight(0.9f)) {
                                    OutlinedTextField(
                                        value = apiMethod,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Método") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { methodMenuOpen = true },
                                        trailingIcon = {
                                            IconButton(onClick = { methodMenuOpen = true }) {
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Método")
                                            }
                                        },
                                    )
                                    DropdownMenu(expanded = methodMenuOpen, onDismissRequest = { methodMenuOpen = false }) {
                                        listOf("GET", "POST", "PUT", "DELETE", "PATCH").forEach { m ->
                                            DropdownMenuItem(
                                                text = { Text(m) },
                                                onClick = {
                                                    apiMethod = m
                                                    methodMenuOpen = false
                                                    syncFieldsToLine()
                                                },
                                            )
                                        }
                                    }
                                }
                                var phMenuOpen by remember { mutableStateOf(false) }
                                OutlinedTextField(
                                    value = template,
                                    onValueChange = {
                                        template = it
                                        syncFieldsToLine()
                                    },
                                    label = { Text("Caminho (ex: /v1/data/{text})") },
                                    modifier = Modifier.weight(1.8f),
                                    trailingIcon = {
                                        Box {
                                            IconButton(onClick = { phMenuOpen = true }) {
                                                Text("{}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            }
                                            PlaceholderDropdown(
                                                expanded = phMenuOpen,
                                                onDismiss = { phMenuOpen = false },
                                                onInsert = { ph ->
                                                    template += ph
                                                    phMenuOpen = false
                                                    syncFieldsToLine()
                                                },
                                            )
                                        }
                                    },
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            // Headers (collapsible)
                            var headersOpen by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { headersOpen = !headersOpen }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(if (headersOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Cabeçalhos (${if (apiHeaders.isEmpty()) "nenhum · a chave vem do provedor" else "${apiHeaders.size} configurados"})",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            if (headersOpen) {
                                apiHeaders.forEach { (hk, hv) ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        OutlinedTextField(
                                            value = hk,
                                            onValueChange = { newK ->
                                                val m = apiHeaders.toMutableMap()
                                                m.remove(hk)
                                                m[newK] = hv
                                                apiHeaders = m
                                                syncFieldsToLine()
                                            },
                                            placeholder = { Text("Header") },
                                            modifier = Modifier.weight(1f),
                                        )
                                        OutlinedTextField(
                                            value = hv,
                                            onValueChange = { newV ->
                                                val m = apiHeaders.toMutableMap()
                                                m[hk] = newV
                                                apiHeaders = m
                                                syncFieldsToLine()
                                            },
                                            placeholder = { Text("Valor") },
                                            modifier = Modifier.weight(1.2f),
                                        )
                                        IconButton(onClick = {
                                            val m = apiHeaders.toMutableMap()
                                            m.remove(hk)
                                            apiHeaders = m
                                            syncFieldsToLine()
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remover", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                TextButton(onClick = {
                                    apiHeaders = apiHeaders + ("X-Custom" to "valor")
                                    syncFieldsToLine()
                                }) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Text("Adicionar cabeçalho")
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Body (if method != GET)
                            if (apiMethod != "GET") {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Tipo do corpo:", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                                    BodyType.entries.forEach { bt ->
                                        FilterChip(
                                            selected = apiBodyType == bt,
                                            onClick = {
                                                apiBodyType = bt
                                                syncFieldsToLine()
                                            },
                                            label = { Text(bt.name) },
                                            modifier = Modifier.padding(end = 4.dp),
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = apiBody,
                                    onValueChange = {
                                        apiBody = it
                                        syncFieldsToLine()
                                    },
                                    label = { Text("Corpo da requisição") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 80.dp),
                                )
                                Spacer(Modifier.height(8.dp))
                            }

                            // Format
                            OutlinedTextField(
                                value = apiFormat,
                                onValueChange = {
                                    apiFormat = it
                                    syncFieldsToLine()
                                },
                                label = { Text("Mostrar na barra (formato)") },
                                modifier = Modifier.fillMaxWidth(),
                                supportingText = { Text("Ex: {logradouro} · {bairro}") },
                            )

                            Spacer(Modifier.height(8.dp))

                            // Lista (collapsible)
                            var listOpen by remember { mutableStateOf(apiList.isNotEmpty() || apiUpcoming.isNotEmpty()) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { listOpen = !listOpen }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(if (listOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Lista, Limite e Próximos", style = MaterialTheme.typography.bodyMedium)
                            }
                            if (listOpen) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = apiList,
                                        onValueChange = {
                                            apiList = it
                                            syncFieldsToLine()
                                        },
                                        label = { Text("--list") },
                                        modifier = Modifier.weight(1.5f),
                                    )
                                    OutlinedTextField(
                                        value = apiMax.toString(),
                                        onValueChange = {
                                            apiMax = it.toIntOrNull() ?: 5
                                            syncFieldsToLine()
                                        },
                                        label = { Text("--max") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(0.8f),
                                    )
                                }
                                OutlinedTextField(
                                    value = apiUpcoming,
                                    onValueChange = {
                                        apiUpcoming = it
                                        syncFieldsToLine()
                                    },
                                    label = { Text("--upcoming") },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        CommandType.ALIAS -> {
                            OutlinedTextField(
                                value = expansion,
                                onValueChange = {
                                    expansion = it
                                    syncFieldsToLine()
                                },
                                label = { Text("Expansão do apelido") },
                                modifier = Modifier.fillMaxWidth(),
                                supportingText = { Text("Ex: abrir youtube, ou w ana {text}") },
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    // 6. TESTAR Section
                    Text(
                        "TESTAR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = sampleArg,
                            onValueChange = { sampleArg = it },
                            placeholder = { Text("Argumento de teste (opcional)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Button(
                            onClick = {
                                if (type == CommandType.API && apiMethod in listOf("POST", "PUT", "PATCH", "DELETE")) {
                                    val p = Providers.of(provider, providers)
                                    val host = p?.baseUrl ?: provider
                                    confirmWriteAlert = "Isto vai enviar $apiMethod para $host. Deseja continuar?"
                                } else {
                                    runTest()
                                }
                            },
                            enabled = !testRunning && canSave,
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (testRunning) "Executando..." else "EXECUTAR")
                        }
                    }

                    // Test output
                    if (testStatus != null || testResultText != null || testError != null) {
                        Spacer(Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        testStatus ?: "RESULTADO",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (testError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    )
                                    if (testLatency != null) {
                                        Text("${testLatency}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                if (testError != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(testError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                                }

                                if (testResultText != null) {
                                    Spacer(Modifier.height(6.dp))
                                    Text("NA BARRA:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        testResultText!!,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }

                                // Interactive JSON Tree for API
                                val raw = testRawJson
                                if (raw != null) {
                                    Spacer(Modifier.height(10.dp))
                                    Text("RESPOSTA JSON (toque em + para adicionar campo ao formato):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(4.dp))
                                    JsonTreeView(
                                        rawJson = raw,
                                        onInsertField = { fieldPath, arrayPath ->
                                            if (arrayPath != null && apiList.isEmpty()) {
                                                apiList = arrayPath
                                                apiFormat += " {$fieldPath}"
                                            } else {
                                                apiFormat += " {$fieldPath}"
                                            }
                                            syncFieldsToLine()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // App Picker Modal Dialog
    if (appPickerOpen) {
        AppPickerDialog(
            apps = apps,
            onSelect = { app ->
                selectedAppPackage = app.id
                if (intentPackages.isEmpty() || type == CommandType.INTENT) {
                    intentPackages = listOf(app.id)
                }
                appPickerOpen = false
                syncFieldsToLine()
            },
            onDismiss = { appPickerOpen = false },
        )
    }

    // Intent Catalog Picker Modal Dialog
    if (catalogPickerOpen) {
        CatalogPickerDialog(
            onSelect = { entry ->
                intentAction = entry.action
                if (!entry.data.isNullOrEmpty()) template = entry.data!!
                if (!entry.mime.isNullOrEmpty()) mimeType = entry.mime!!
                intentTarget = entry.target
                if (entry.extras.isNotEmpty()) {
                    val existingKeys = extras.map { it.key }.toSet()
                    val newExtras = entry.extras.filterNot { it.key in existingKeys }.map {
                        IntentExtra(it.key, it.type, "")
                    }
                    extras = extras + newExtras
                }
                catalogPickerOpen = false
                syncFieldsToLine()
            },
            onDismiss = { catalogPickerOpen = false },
        )
    }

    // Write confirmation dialog
    confirmWriteAlert?.let { message ->
        AlertDialog(
            onDismissRequest = { confirmWriteAlert = null },
            title = { Text("Confirmar execução") },
            text = { Text(message) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmWriteAlert = null
                        runTest()
                    },
                ) {
                    Text("Continuar")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWriteAlert = null }) {
                    Text("Cancelar")
                }
            },
        )
    }

    // Delete confirmation dialog
    if (confirmDeleteAlert) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAlert = false },
            title = { Text(stringResource(R.string.cmd_pref_remove)) },
            text = { Text("Deseja realmente remover o comando \"$name\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteAlert = false
                        onRemove()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.cmd_pref_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAlert = false }) {
                    Text(stringResource(R.string.cmd_pref_cancel))
                }
            },
        )
    }
}

@Composable
private fun PlaceholderDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onInsert: (String) -> Unit,
) {
    val items = listOf(
        "{text}" to "o que vier depois",
        "{1}" to "1ª palavra",
        "{2}" to "2ª palavra",
        "{number}" to "contato → nº com DDI",
        "{name}" to "nome do contato",
        "{phone}" to "telefone do contato",
        "{date}" to "data atual",
        "{time}" to "hora atual",
        "{clip}" to "área de transferência",
        "{location}" to "localização",
        "{maps}" to "coordenadas para mapa",
    )
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        items.forEach { (ph, desc) ->
            DropdownMenuItem(
                text = { Text("$ph  ·  $desc") },
                onClick = { onInsert(ph) },
            )
        }
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<AppEntry>,
    onSelect: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(search, apps) {
        if (search.isBlank()) apps else apps.filter { it.label.contains(search, ignoreCase = true) || it.id.contains(search, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escolher aplicativo") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Buscar app...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 350.dp)) {
                    items(filtered) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(app) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(app.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(app.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cmd_pref_cancel))
            }
        },
    )
}

@Composable
private fun CatalogPickerDialog(
    onSelect: (app.lawnchair.command.CatalogEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(search) {
        val q = search.trim().lowercase()
        if (q.isBlank()) {
            IntentCatalog.ENTRIES
        } else {
            IntentCatalog.ENTRIES.filter {
                it.name.lowercase().contains(q) || it.action.lowercase().contains(q)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catálogo de Ações Comuns") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Buscar ação...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(filtered) { entry ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(entry) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(entry.action, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                if (entry.extras.isNotEmpty()) {
                                    Text(
                                        "Extras: " + entry.extras.joinToString(", ") { it.key.substringAfterLast('.') },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cmd_pref_cancel))
            }
        },
    )
}

/**
 * Interactive expandable JSON Tree with "+" buttons to insert field paths into the format string.
 */
@Composable
private fun JsonTreeView(
    rawJson: String,
    onInsertField: (fieldPath: String, arrayParentPath: String?) -> Unit,
) {
    val parsed = remember(rawJson) {
        runCatching { JSONTokener(rawJson).nextValue() }.getOrNull()
    }

    if (parsed == null) {
        Text("JSON inválido", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        return
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .padding(8.dp),
    ) {
        when (parsed) {
            is JSONObject -> JsonObjectNode(obj = parsed, currentPath = "", arrayParentPath = null, onInsert = onInsertField)
            is JSONArray -> JsonArrayNode(arr = parsed, currentPath = "", arrayParentPath = null, onInsert = onInsertField)
            else -> Text(parsed.toString(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun JsonObjectNode(
    obj: JSONObject,
    currentPath: String,
    arrayParentPath: String?,
    onInsert: (String, String?) -> Unit,
) {
    val keys = remember(obj) { obj.keys().asSequence().toList() }
    Column {
        keys.forEach { key ->
            val value = obj.opt(key)
            val path = if (currentPath.isEmpty()) key else "$currentPath.$key"
            when (value) {
                null, JSONObject.NULL -> {}

                is JSONObject -> {
                    var expanded by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(vertical = 2.dp),
                    ) {
                        Icon(if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("$key: { ... }", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    if (expanded) {
                        Box(Modifier.padding(start = 16.dp)) {
                            JsonObjectNode(obj = value, currentPath = path, arrayParentPath = arrayParentPath, onInsert = onInsert)
                        }
                    }
                }

                is JSONArray -> {
                    var expanded by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(vertical = 2.dp),
                    ) {
                        Icon(if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("$key: [ ${value.length()} itens ]", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    if (expanded) {
                        Box(Modifier.padding(start = 16.dp)) {
                            JsonArrayNode(arr = value, currentPath = path, arrayParentPath = path, onInsert = onInsert)
                        }
                    }
                }

                else -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            "$key: $value",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                        )
                        IconButton(
                            onClick = {
                                val relPath = if (arrayParentPath != null && path.startsWith("$arrayParentPath.")) {
                                    path.removePrefix("$arrayParentPath.").removePrefix("0.").removePrefix("1.").removePrefix("2.")
                                } else {
                                    path
                                }
                                onInsert(relPath, arrayParentPath)
                            },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Inserir campo", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JsonArrayNode(
    arr: JSONArray,
    currentPath: String,
    arrayParentPath: String?,
    onInsert: (String, String?) -> Unit,
) {
    Column {
        val count = minOf(arr.length(), 5) // Show up to 5 items in preview
        for (i in 0 until count) {
            val item = arr.opt(i)
            val path = if (currentPath.isEmpty()) "$i" else "$currentPath.$i"
            when (item) {
                is JSONObject -> {
                    var expanded by remember { mutableStateOf(i == 0) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(vertical = 2.dp),
                    ) {
                        Icon(if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("[$i] { ... }", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    if (expanded) {
                        Box(Modifier.padding(start = 16.dp)) {
                            JsonObjectNode(obj = item, currentPath = path, arrayParentPath = arrayParentPath, onInsert = onInsert)
                        }
                    }
                }

                is JSONArray -> {
                    var expanded by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(vertical = 2.dp),
                    ) {
                        Icon(if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("[$i] [ ... ]", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    if (expanded) {
                        Box(Modifier.padding(start = 16.dp)) {
                            JsonArrayNode(arr = item, currentPath = path, arrayParentPath = arrayParentPath, onInsert = onInsert)
                        }
                    }
                }

                else -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Text("[$i]: $item", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { onInsert(path, arrayParentPath) },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Inserir campo", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        if (arr.length() > 5) {
            Text("... e mais ${arr.length() - 5} itens", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
