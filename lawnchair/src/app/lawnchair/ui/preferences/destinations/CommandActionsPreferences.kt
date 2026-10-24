package app.lawnchair.ui.preferences.destinations

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.lawnchair.command.ACTION_VIEW
import app.lawnchair.command.ActionKind
import app.lawnchair.command.AdvancedMode
import app.lawnchair.command.AdvancedModeStore
import app.lawnchair.command.AiClient
import app.lawnchair.command.AiConfig
import app.lawnchair.command.AiProvider
import app.lawnchair.command.AiStore
import app.lawnchair.command.AiTurn
import app.lawnchair.command.AliasStore
import app.lawnchair.command.Aliases
import app.lawnchair.command.Analysis
import app.lawnchair.command.AppEntry
import app.lawnchair.command.ArgKind
import app.lawnchair.command.CommandAction
import app.lawnchair.command.CommandActivity
import app.lawnchair.command.CommandAlias
import app.lawnchair.command.CommandEngine
import app.lawnchair.command.CommandExecutor
import app.lawnchair.command.CommandLine
import app.lawnchair.command.CommandPack
import app.lawnchair.command.CommandPacks
import app.lawnchair.command.CommandPattern
import app.lawnchair.command.CommandUsage
import app.lawnchair.command.ContactEntry
import app.lawnchair.command.CustomAction
import app.lawnchair.command.CustomActionStore
import app.lawnchair.command.CustomActions
import app.lawnchair.command.FavoriteStore
import app.lawnchair.command.ImportCommandsActivity
import app.lawnchair.command.ParseResult
import app.lawnchair.command.ParsedCommand
import app.lawnchair.command.ProviderStore
import app.lawnchair.command.Providers
import app.lawnchair.command.R as CommandR
import app.lawnchair.command.StatsStore
import app.lawnchair.command.TaskApp
import app.lawnchair.command.Tone
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.ClickableIcon
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.CommandGuide
import app.lawnchair.ui.preferences.navigation.CommandProviders
import app.lawnchair.ui.preferences.navigation.CommandVariables
import app.lawnchair.util.foldAccents
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A sample contact for previews when the contacts permission is missing. It can never be run. */
internal val DemoContact = ContactEntry("Ana Souza", "+55 11 98765-4321")

/**
 * Binds letters of the command bar to actions of other apps: recipes from the catalog and the
 * shortcuts an app publishes to the launcher. "w" (WhatsApp) is just the first preset.
 */
@Suppress("ktlint:compose:modifier-missing-check")
@Composable
fun CommandActionsPreferences(openEdit: String = "") {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var actions by remember { mutableStateOf(CustomActionStore.load(context)) }
    var aliases by remember { mutableStateOf(AliasStore.load(context)) }
    // The command open in the editor; null when it is closed.
    var editor by remember { mutableStateOf<EditorTarget?>(null) }

    androidx.compose.runtime.LaunchedEffect(openEdit) {
        if (openEdit.isNotEmpty()) {
            val act = actions.firstOrNull { it.letter.equals(openEdit, ignoreCase = true) }
            val al = aliases.firstOrNull { it.name.equals(openEdit, ignoreCase = true) }
            if (act != null) {
                editor = EditorTarget.Action(act)
            } else if (al != null) {
                editor = EditorTarget.Alias(al)
            }
        }
    }
    // The advanced mode shows the providers and the toggle to turn it off; what the line accepts does not change.
    val advanced by remember {
        AdvancedModeStore.load(context)
        AdvancedMode.enabled
    }.collectAsState()
    var query by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(FavoriteStore.load(context)) }
    var othersOpen by remember { mutableStateOf(false) }
    var backupOpen by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    val usage = remember { CommandUsage.load(context) }
    var history by remember { mutableStateOf(CommandActivity.loadHistory(context)) }
    var taskApp by remember { mutableStateOf(TaskApp.label(context)) }
    var taskPicker by remember { mutableStateOf(false) }
    var aiConfig by remember { mutableStateOf(AiStore.load(context)) }
    var aiOpen by remember { mutableStateOf(false) }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedActions by remember { mutableStateOf(emptySet<CustomAction>()) }
    var selectedAliases by remember { mutableStateOf(emptySet<CommandAlias>()) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }

    var newCommandLine by remember { mutableStateOf("") }
    var newLineError by remember { mutableStateOf<String?>(null) }
    var pendingConflict by remember { mutableStateOf<ParsedCommand?>(null) }

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedActions = emptySet()
        selectedAliases = emptySet()
    }

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

    fun saveFromLine() {
        val line = newCommandLine.trim()
        if (line.isEmpty()) return

        val providers = ProviderStore.all(context)
        val parseResult = CommandLine.parse(line, actions, aliases, providers)
        if (parseResult is ParseResult.Failure) {
            newLineError = parseResult.error.message
            return
        }

        val parsed = (parseResult as ParseResult.Success).command
        val name = parsed.name
        val existingAction = actions.firstOrNull { it.letter == name }
        val existingAlias = aliases.firstOrNull { it.name.lowercase() == name }

        val validation = CommandLine.validate(
            command = parsed,
            existingActions = actions,
            existingAliases = aliases,
            providers = providers,
            ignore = existingAction ?: existingAlias,
        )
        if (!validation.isValid) {
            newLineError = validation.errors.first()
            return
        }

        val hasConflict = existingAction != null || existingAlias != null
        if (hasConflict && pendingConflict != parsed) {
            pendingConflict = parsed
            val canonical = CommandLine.serialize(parsed)
            newLineError = context.getString(CommandR.string.cmd_alias_conflict_preview) + " · " +
                context.getString(CommandR.string.cmd_alias_conflict_new, canonical)
            return
        }

        val beforeActions = actions
        val beforeAliases = aliases
        when (parsed) {
            is ParsedCommand.Action -> {
                val next = actions.filterNot { it.letter == parsed.action.letter } + parsed.action
                update(next)
                val nextAliases = aliases.filterNot { it.name.lowercase() == parsed.action.letter }
                if (nextAliases.size != aliases.size) updateAliases(nextAliases)
            }

            is ParsedCommand.Alias -> {
                val next = aliases.filterNot { it.name.lowercase() == parsed.alias.name.lowercase() } + parsed.alias
                updateAliases(next)
                val nextActions = actions.filterNot { it.letter == parsed.alias.name.lowercase() }
                if (nextActions.size != actions.size) update(nextActions)
            }
        }
        val canonical = CommandLine.serialize(parsed)
        newCommandLine = ""
        newLineError = null
        pendingConflict = null
        undoable(context.getString(CommandR.string.cmd_alias_saved, canonical)) {
            update(beforeActions)
            updateAliases(beforeAliases)
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

    fun removeSelected() {
        val toRemoveActions = selectedActions
        val toRemoveAliases = selectedAliases
        val beforeActions = actions
        val beforeAliases = aliases
        val count = toRemoveActions.size + toRemoveAliases.size
        if (count == 0) return

        if (toRemoveActions.isNotEmpty()) {
            update(actions.filterNot { it in toRemoveActions })
        }
        if (toRemoveAliases.isNotEmpty()) {
            updateAliases(aliases.filterNot { it in toRemoveAliases })
        }

        selectionMode = false
        selectedActions = emptySet()
        selectedAliases = emptySet()

        val message = if (count == 1) {
            context.getString(R.string.cmd_pref_command_removed)
        } else {
            context.getString(R.string.cmd_pref_commands_removed, count)
        }
        undoable(message) {
            update(beforeActions)
            updateAliases(beforeAliases)
        }
    }

    fun restoreDefaults() {
        val before = actions
        update(CustomActions.DEFAULTS)
        undoable(context.getString(R.string.cmd_pref_restored)) { update(before) }
    }

    fun clearHistory() {
        val before = history
        val stats = StatsStore.load(context)
        history = emptyList()
        CommandActivity.clearHistory(context)
        undoable(context.getString(R.string.cmd_pref_history_cleared)) {
            history = before
            CommandActivity.saveHistory(context, before)
            StatsStore.save(context, stats)
        }
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
                val providers = CommandPacks.providersFor(pack.actions, ProviderStore.load(context))
                it.write(CommandPacks.encode(pack.actions, pack.aliases, providers).toByteArray())
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

    val commandItems = remember(actions, aliases, usage) {
        val nonApi = actions.filter { it.kind != ActionKind.API }.map { action ->
            val count = usage[CommandUsage.actionKey(action.letter)] ?: 0
            val dest = if (action.kind == ActionKind.SCRIPT) {
                scriptSummary(action)
            } else {
                CommandLine.serialize(action).substringAfter(' ').substringBefore(" # ")
            }
            CommandItem.Action(action, dest, count)
        }
        val aliasItems = aliases.map { alias ->
            val count = usage[CommandUsage.aliasKey(alias.name)] ?: 0
            CommandItem.Alias(alias, count)
        }
        (nonApi + aliasItems).sortedWith(
            compareByDescending<CommandItem> { it.usageCount }
                .thenBy { it.name },
        )
    }

    val apiItems = remember(actions, usage) {
        actions.filter { it.kind == ActionKind.API }.map { action ->
            val count = usage[CommandUsage.actionKey(action.letter)] ?: 0
            val provider = if (action.provider.isNotEmpty()) "@${action.provider}" else ""
            val dest = if (provider.isNotEmpty()) "$provider · ${action.template}" else action.template
            CommandItem.Action(action, dest, count)
        }.sortedWith(
            compareByDescending<CommandItem.Action> { it.usageCount }
                .thenBy { it.name },
        )
    }

    val shownCommands = commandItems.filter { it.matches(query) }
    val shownApiCommands = apiItems.filter { it.matches(query) }

    val selectedCount = selectedActions.size + selectedAliases.size
    val screenTitle = if (selectionMode) {
        if (selectedCount == 0) {
            stringResource(R.string.cmd_pref_select_commands)
        } else {
            stringResource(R.string.cmd_pref_selected_count, selectedCount)
        }
    } else {
        stringResource(R.string.command_actions_title)
    }

    Box(Modifier.fillMaxSize()) {
        PreferenceLayout(
            label = screenTitle,
            actions = {
                if (selectionMode) {
                    val allShownSelected = (shownCommands.isNotEmpty() || shownApiCommands.isNotEmpty()) &&
                        shownCommands.all {
                            when (it) {
                                is CommandItem.Action -> it.action in selectedActions
                                is CommandItem.Alias -> it.alias in selectedAliases
                            }
                        } &&
                        shownApiCommands.all { it.action in selectedActions }
                    ClickableIcon(
                        imageVector = if (allShownSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                        onClick = {
                            if (allShownSelected) {
                                val actionsToDeselect = shownCommands.filterIsInstance<CommandItem.Action>().map { it.action } +
                                    shownApiCommands.map { it.action }
                                val aliasesToDeselect = shownCommands.filterIsInstance<CommandItem.Alias>().map { it.alias }
                                selectedActions = selectedActions - actionsToDeselect.toSet()
                                selectedAliases = selectedAliases - aliasesToDeselect.toSet()
                            } else {
                                val actionsToSelect = shownCommands.filterIsInstance<CommandItem.Action>().map { it.action } +
                                    shownApiCommands.map { it.action }
                                val aliasesToSelect = shownCommands.filterIsInstance<CommandItem.Alias>().map { it.alias }
                                selectedActions = selectedActions + actionsToSelect
                                selectedAliases = selectedAliases + aliasesToSelect
                            }
                        },
                    )
                    ClickableIcon(
                        imageVector = Icons.Rounded.Delete,
                        enabled = selectedCount > 0,
                        tint = if (selectedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        onClick = {
                            if (selectedCount > 0) {
                                confirmDeleteSelected = true
                            }
                        },
                    )
                    ClickableIcon(
                        imageVector = Icons.Rounded.Close,
                        onClick = {
                            selectionMode = false
                            selectedActions = emptySet()
                            selectedAliases = emptySet()
                        },
                    )
                } else if (actions.isNotEmpty() || aliases.isNotEmpty()) {
                    ClickableIcon(
                        imageVector = Icons.Rounded.Checklist,
                        onClick = { selectionMode = true },
                    )
                }
            },
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.cmd_pref_search)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            if (!selectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    OutlinedTextField(
                        value = newCommandLine,
                        onValueChange = {
                            newCommandLine = it
                            newLineError = null
                            pendingConflict = null
                        },
                        placeholder = { Text(stringResource(R.string.cmd_pref_new_command)) },
                        singleLine = true,
                        isError = newLineError != null,
                        supportingText = newLineError?.let { { Text(it) } },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { saveFromLine() },
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(
                        onClick = { editor = EditorTarget.New },
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.cmd_pref_new_command),
                        )
                    }
                }
            }
            @Composable
            fun ItemRow(item: CommandItem) {
                val isSelected = when (item) {
                    is CommandItem.Action -> item.action in selectedActions
                    is CommandItem.Alias -> item.alias in selectedAliases
                }
                fun toggleSelected() {
                    when (item) {
                        is CommandItem.Action -> selectedActions = if (isSelected) selectedActions - item.action else selectedActions + item.action
                        is CommandItem.Alias -> selectedAliases = if (isSelected) selectedAliases - item.alias else selectedAliases + item.alias
                    }
                }
                CommandLinePreference(
                    name = item.name,
                    destination = item.destination,
                    usageCount = item.usageCount,
                    isSelected = isSelected,
                    selectionMode = selectionMode,
                    favorite = item.name.lowercase() in favorites,
                    onToggleFavorite = { favorites = FavoriteStore.toggle(context, item.name) },
                    onClick = {
                        if (selectionMode) {
                            toggleSelected()
                        } else {
                            when (item) {
                                is CommandItem.Action -> editor = EditorTarget.Action(item.action)
                                is CommandItem.Alias -> editor = EditorTarget.Alias(item.alias)
                            }
                        }
                    },
                    onLongClick = {
                        if (selectionMode) {
                            toggleSelected()
                        } else {
                            selectionMode = true
                            when (item) {
                                is CommandItem.Action -> selectedActions = setOf(item.action)
                                is CommandItem.Alias -> selectedAliases = setOf(item.alias)
                            }
                        }
                    },
                )
            }

            // Favorites come first and are what the watch carries; the rest folds away once there are favorites.
            val favoriteShown = (shownCommands + shownApiCommands).filter { it.name.lowercase() in favorites }
            val restCommands = shownCommands.filter { it.name.lowercase() !in favorites }
            val restApi = shownApiCommands.filter { it.name.lowercase() !in favorites }
            val folded = favorites.isNotEmpty() && query.isEmpty() && !selectionMode && !othersOpen
            if (favoriteShown.isNotEmpty()) {
                PreferenceGroup(heading = stringResource(R.string.cmd_pref_group_favorites)) {
                    favoriteShown.forEach { ItemRow(it) }
                }
            }
            if (folded) {
                if (restCommands.isNotEmpty() || restApi.isNotEmpty()) {
                    PreferenceGroup(heading = stringResource(R.string.cmd_pref_group_commands)) {
                        ClickablePreference(
                            label = stringResource(R.string.cmd_pref_show_others, restCommands.size + restApi.size),
                            subtitle = stringResource(R.string.cmd_pref_favorites_hint),
                            onClick = { othersOpen = true },
                        )
                    }
                }
            } else {
                val canHide = favorites.isNotEmpty() && othersOpen && query.isEmpty() && !selectionMode
                val hideRow: @Composable () -> Unit = {
                    ClickablePreference(
                        label = stringResource(R.string.cmd_pref_hide_others),
                        onClick = { othersOpen = false },
                    )
                }
                if (restCommands.isNotEmpty() || (favoriteShown.isEmpty() && shownApiCommands.isEmpty() && (query.isNotEmpty() || (commandItems.isEmpty() && apiItems.isEmpty())))) {
                    PreferenceGroup(heading = stringResource(R.string.cmd_pref_group_commands)) {
                        if (query.isNotEmpty() && shownCommands.isEmpty() && shownApiCommands.isEmpty()) {
                            ClickablePreference(
                                label = stringResource(R.string.cmd_pref_no_actions),
                                subtitle = stringResource(R.string.cmd_pref_no_match),
                                onClick = {},
                            )
                        }
                        if (canHide) hideRow()
                        restCommands.forEach { ItemRow(it) }
                    }
                }
                if (restApi.isNotEmpty()) {
                    PreferenceGroup(heading = stringResource(R.string.cmd_pref_group_api_commands)) {
                        if (canHide && restCommands.isEmpty()) hideRow()
                        restApi.forEach { ItemRow(it) }
                    }
                }
            }
            if (!selectionMode) {
                PreferenceGroup(heading = stringResource(R.string.cmd_pref_group_default_apps)) {
                    // Which launcher answers the home button is the system's choice; the screen opens from here.
                    val defaultLauncher = remember { defaultLauncherLabel(context) }
                    ClickablePreference(
                        label = stringResource(R.string.cmd_pref_default_launcher),
                        subtitle = defaultLauncher ?: stringResource(R.string.cmd_pref_default_launcher_none),
                        onClick = {
                            runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        },
                    )
                    ClickablePreference(
                        label = stringResource(R.string.cmd_pref_task_app),
                        subtitle = taskApp ?: stringResource(R.string.cmd_pref_task_app_none),
                        onClick = { taskPicker = true },
                    )
                }
                PreferenceGroup(heading = stringResource(R.string.cmd_pref_group_ai)) {
                    ClickablePreference(
                        label = stringResource(R.string.cmd_pref_ai),
                        subtitle = aiConfig?.label ?: stringResource(R.string.cmd_pref_ai_none),
                        onClick = { aiOpen = true },
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
                PreferenceGroup(heading = stringResource(R.string.cmd_variables_title)) {
                    NavigationActionPreference(
                        label = stringResource(R.string.cmd_variables_title),
                        destination = CommandVariables,
                        subtitle = stringResource(R.string.cmd_variables_entry),
                    )
                }
                PreferenceGroup(heading = stringResource(R.string.cmd_pref_group_history)) {
                    ClickablePreference(
                        label = stringResource(R.string.cmd_pref_clear_history),
                        subtitle = if (history.isEmpty()) {
                            stringResource(R.string.cmd_pref_clear_history_empty)
                        } else {
                            stringResource(R.string.cmd_pref_clear_history_count, history.size)
                        },
                        onClick = { if (history.isNotEmpty()) clearHistory() },
                    )
                }
                // Rarely needed, so kept apart: what API commands go through and their keys.
                PreferenceGroup(heading = stringResource(R.string.cmd_pref_group_advanced)) {
                    // Only once the mode is on, and then it can be turned off here without the bar.
                    if (advanced) {
                        SwitchPreference(
                            checked = true,
                            onCheckedChange = { AdvancedModeStore.set(context, it) },
                            label = stringResource(R.string.cmd_pref_advanced_mode),
                            description = stringResource(R.string.cmd_pref_advanced_mode_hint),
                        )
                        NavigationActionPreference(
                            label = stringResource(R.string.cmd_providers_title),
                            destination = CommandProviders(),
                            subtitle = stringResource(R.string.cmd_providers_entry),
                        )
                    }
                    NavigationActionPreference(
                        label = stringResource(R.string.cmd_guide_title),
                        destination = CommandGuide,
                        subtitle = stringResource(R.string.cmd_guide_subtitle),
                    )
                }
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
            onOpenGuide = {
                backupOpen = false
                navController.navigate(CommandGuide)
            },
            onDismiss = { backupOpen = false },
        )
    }

    if (confirmDeleteSelected) {
        AlertDialog(
            onDismissRequest = { confirmDeleteSelected = false },
            title = { Text(stringResource(R.string.cmd_pref_delete_confirm_title)) },
            text = {
                val message = if (selectedCount == 1) {
                    stringResource(R.string.cmd_pref_delete_confirm_text, selectedCount)
                } else {
                    stringResource(R.string.cmd_pref_delete_confirm_text_multiple, selectedCount)
                }
                Text(message)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteSelected = false
                        removeSelected()
                    },
                ) {
                    Text(stringResource(R.string.cmd_pref_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelected = false }) { Text(stringResource(R.string.cmd_pref_cancel)) }
            },
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
                if (mode == ShareMode.Share) CommandR.string.cmd_pref_share_title else R.string.cmd_pref_export_title,
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

    editor?.let { target ->
        CommandEditor(
            target = target,
            actions = actions,
            aliases = aliases,
            apps = apps,
            contacts = contacts,
            onSaveAction = { saved ->
                val old = (target as? EditorTarget.Action)?.action
                update(if (old == null) actions + saved else actions.map { if (it === old) saved else it })
                editor = null
            },
            onSaveAlias = { saved ->
                val old = (target as? EditorTarget.Alias)?.alias
                updateAliases(if (old == null) aliases + saved else aliases.map { if (it === old) saved else it })
                editor = null
            },
            onRemove = {
                when (target) {
                    is EditorTarget.Action -> removeAction(target.action)
                    is EditorTarget.Alias -> removeAlias(target.alias)
                    EditorTarget.New -> Unit
                }
                editor = null
            },
            onTest = { action ->
                if (!CommandExecutor.execute(context, action)) {
                    Toast.makeText(context, runFailed, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { editor = null },
        )
    }

    if (aiOpen) {
        AiDialog(
            current = aiConfig,
            onSave = { saved ->
                AiStore.save(context, saved)
                aiConfig = saved
                aiOpen = false
            },
            onRemove = {
                val before = aiConfig
                AiStore.save(context, null)
                aiConfig = null
                aiOpen = false
                undoable(context.getString(R.string.cmd_pref_ai_removed)) {
                    AiStore.save(context, before)
                    aiConfig = before
                }
            },
            onDismiss = { aiOpen = false },
        )
    }

    if (taskPicker) {
        val candidates by produceState<List<AppEntry>?>(initialValue = null) {
            value = withContext(Dispatchers.Default) { TaskApp.candidates(context) }
        }
        val list = candidates
        if (list == null || list.isEmpty()) {
            AlertDialog(
                onDismissRequest = { taskPicker = false },
                confirmButton = {
                    TextButton(onClick = { taskPicker = false }) { Text(stringResource(R.string.cmd_pref_close)) }
                },
                title = { Text(stringResource(R.string.cmd_pref_task_app)) },
                text = {
                    Text(stringResource(if (list == null) R.string.cmd_pref_searching_apps else R.string.cmd_pref_task_app_empty))
                },
            )
        } else {
            val askLabel = stringResource(R.string.cmd_pref_task_app_ask)
            // null first: forget the app, so the system list asks again the next time.
            PickerDialog(
                title = stringResource(R.string.cmd_pref_task_app),
                items = listOf<AppEntry?>(null) + list,
                label = { it?.label ?: askLabel },
                onPick = { picked ->
                    TaskApp.save(context, picked?.let { ComponentName.unflattenFromString(it.id) })
                    taskApp = picked?.label
                    taskPicker = false
                },
                onDismiss = { taskPicker = false },
            )
        }
    }
}

/** Share, export or import: three ways to move commands, one entry in the list. */
@Composable
private fun BackupDialog(
    onShare: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onOpenGuide: () -> Unit,
    onDismiss: () -> Unit,
) {
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
                Option(R.string.cmd_guide_title, R.string.cmd_guide_backup_hint, onOpenGuide)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cmd_pref_cancel)) } },
    )
}

@Composable
internal fun <T> PickerDialog(
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

/**
 * The dashed box under the editors: what was typed, the technical intent it produces (or why it
 * cannot run) and an optional [note].
 */
@Composable
internal fun PreviewBox(analysis: Analysis?, intro: String? = null, note: String? = null) {
    if (analysis == null) return
    val problem = analysis.action == null && analysis.tone == Tone.ERROR
    val stroke = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = stroke,
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (intro != null) Text(intro, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        when {
            analysis.needsContacts -> Text(
                stringResource(R.string.cmd_pref_needs_contacts),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            analysis.intent.isNotEmpty() -> Text(
                "⇢ " + analysis.intent,
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
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Remove (when editing) on the left, then Test, Cancel and Save on one row. */
@Composable
internal fun DialogButtons(
    saveEnabled: Boolean,
    testEnabled: Boolean,
    canRemove: Boolean,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
    saveLabel: Int = R.string.cmd_pref_save,
) {
    val pad = PaddingValues(horizontal = 4.dp)

    @Composable
    fun Button(text: Int, enabled: Boolean = true, color: Color = Color.Unspecified, onClick: () -> Unit) {
        TextButton(enabled = enabled, onClick = onClick, contentPadding = pad) {
            Text(stringResource(text), color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (canRemove) Button(R.string.cmd_pref_remove, color = MaterialTheme.colorScheme.error, onClick = onRemove)
        Spacer(Modifier.weight(1f))
        Button(R.string.cmd_pref_test, enabled = testEnabled, onClick = onTest)
        Button(R.string.cmd_pref_cancel, onClick = onCancel)
        Button(saveLabel, enabled = saveEnabled, onClick = onSave)
    }
}

/** What typing the letter with the sample does: the command with its argument highlighted, then the intent. */
@Composable
internal fun LetterPreview(letter: String, sample: String, analysis: Analysis?, smsFallback: Boolean, appLabel: String) {
    if (analysis == null) return
    val line = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .drawBehind {
                drawRoundRect(
                    color = line,
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
            .padding(12.dp),
    ) {
        Text(
            buildAnnotatedString {
                append(letter)
                if (sample.isNotBlank()) {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary)) { append(" $sample") }
                }
            },
            style = mono,
        )
        when {
            analysis.needsContacts -> Text(
                stringResource(R.string.cmd_pref_needs_contacts),
                style = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            analysis.intent.isNotEmpty() -> {
                Text("→ ${analysis.intent}", style = mono)
                if (smsFallback) {
                    Text(stringResource(R.string.cmd_pref_sms_fallback, appLabel), style = mono)
                }
            }

            else -> Text(
                analysis.preview.lowercase().replaceFirstChar { it.uppercase() },
                style = mono,
                color = if (analysis.action == null && analysis.tone == Tone.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal const val MAX_ALIAS = 20

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
internal fun argLabel(arg: ArgKind) = when (arg) {
    ArgKind.NONE -> R.string.cmd_pref_arg_none
    ArgKind.TEXT -> R.string.cmd_pref_arg_text
    ArgKind.CONTACT -> R.string.cmd_pref_arg_contact
    ArgKind.CONTACT_AND_TEXT -> R.string.cmd_pref_arg_contact_text
}

/** The intent actions the recipes and the engine use, as a menu; anything else is typed under "Outra…". */
private val IntentActions = listOf("VIEW", "SEND", "SENDTO", "DIAL", "WEB_SEARCH")

@Composable
internal fun ActionPicker(action: String, onChange: (String) -> Unit) {
    val known = IntentActions.firstOrNull { expandAction(action) == expandAction(it) }
    var other by remember { mutableStateOf(known == null) }
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = if (other) stringResource(R.string.cmd_pref_action_other) else known ?: IntentActions.first(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.cmd_pref_manual_action)) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // A read-only field swallows taps, so a transparent layer on top opens the menu.
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            IntentActions.forEach { id ->
                DropdownMenuItem(
                    text = { Text(id) },
                    onClick = {
                        other = false
                        onChange(id)
                        open = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.cmd_pref_action_other)) },
                onClick = {
                    other = true
                    open = false
                },
            )
        }
    }
    if (other) {
        OutlinedTextField(
            value = action,
            onValueChange = { onChange(it.trim()) },
            singleLine = true,
            label = { Text(stringResource(R.string.cmd_pref_action_other_hint)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

/** "VIEW" becomes "android.intent.action.VIEW"; a full name is kept. */
internal fun expandAction(raw: String) = if ('.' in raw) raw else "android.intent.action." + raw.uppercase().ifEmpty { "VIEW" }

internal const val MAX_LETTER = 4

/** Stands for the letter in the manual form's preview, before the user picks one. */
internal const val PREVIEW_LETTER = "x"

/** A free letter taken from the label: "Lanterna" gives "l", then "la" and so on. */
internal fun suggestLetter(label: String, existing: List<CustomAction>): String {
    val base = label.lowercase().filter { it.isLetterOrDigit() }
    for (n in 1..minOf(MAX_LETTER, base.length)) {
        val candidate = base.take(n)
        if (CustomActions.validateLetter(candidate, existing) == null) return candidate
    }
    return ""
}

/**
 * The AI "ia" talks to: the service, the key the user created there and, optionally, a model. The
 * service is picked from the key when its prefix tells. Test sends a short message with what is typed.
 */
@Composable
private fun AiDialog(
    current: AiConfig?,
    onSave: (AiConfig) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(current?.provider ?: AiProvider.OPENAI) }
    var key by remember { mutableStateOf(current?.key.orEmpty()) }
    var model by remember { mutableStateOf(current?.model.orEmpty()) }
    var timeout by remember { mutableStateOf((current?.timeoutSeconds ?: AiConfig.DEFAULT_TIMEOUT).toString()) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val okLabel = stringResource(R.string.cmd_pref_ai_test_ok)
    // Blank or out of range falls back to the default, so the field can be cleared while typing.
    val seconds = timeout.toIntOrNull()?.takeIf { it in AiConfig.TIMEOUT_RANGE }
    val config = AiConfig(provider, key.trim(), model.trim(), seconds ?: AiConfig.DEFAULT_TIMEOUT)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cmd_pref_ai)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.cmd_pref_ai_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AiProvider.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clickable { provider = option },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = provider == option, onClick = { provider = option })
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                OutlinedTextField(
                    value = key,
                    onValueChange = { typed ->
                        key = typed
                        result = null
                        // A pasted key names its service.
                        AiProvider.guess(typed)?.let { provider = it }
                    },
                    label = { Text(stringResource(R.string.cmd_pref_ai_key)) },
                    placeholder = { Text(provider.keyHint) },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = {
                        model = it
                        result = null
                    },
                    label = { Text(stringResource(R.string.cmd_pref_ai_model)) },
                    placeholder = { Text(provider.defaultModel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = timeout,
                    onValueChange = { typed ->
                        timeout = typed.filter { it.isDigit() }.take(3)
                        result = null
                    },
                    label = { Text(stringResource(R.string.cmd_pref_ai_timeout)) },
                    supportingText = {
                        Text(stringResource(R.string.cmd_pref_ai_timeout_hint, AiConfig.TIMEOUT_RANGE.first, AiConfig.TIMEOUT_RANGE.last))
                    },
                    isError = timeout.isNotEmpty() && seconds == null,
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    stringResource(R.string.cmd_pref_ai_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
                val shown = if (testing) stringResource(R.string.command_running) else result
                if (shown != null) {
                    Text(
                        shown,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = when {
                            testing -> MaterialTheme.colorScheme.onSurfaceVariant
                            shown == okLabel -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            DialogButtons(
                saveEnabled = key.isNotBlank() && (timeout.isEmpty() || seconds != null),
                testEnabled = key.isNotBlank() && !testing,
                canRemove = current != null,
                onSave = { onSave(config) },
                onTest = {
                    testing = true
                    scope.launch {
                        result = runCatching {
                            AiClient.send(config, "Reply with the single word OK.", listOf(AiTurn(fromUser = true, text = "ping")))
                        }.fold({ okLabel }, { AiClient.describe(context, it) })
                        testing = false
                    }
                },
                onRemove = onRemove,
                onCancel = onDismiss,
            )
        },
    )
}

private sealed interface CommandItem {
    val name: String
    val destination: String
    val usageCount: Int
    fun matches(query: String): Boolean

    data class Action(
        val action: CustomAction,
        override val destination: String,
        override val usageCount: Int,
    ) : CommandItem {
        override val name: String get() = action.letter
        override fun matches(query: String): Boolean {
            val q = query.foldAccents().trim()
            if (q.isEmpty()) return true
            return action.letter.foldAccents().contains(q) ||
                action.label.foldAccents().contains(q) ||
                destination.foldAccents().contains(q)
        }
    }

    data class Alias(
        val alias: CommandAlias,
        override val usageCount: Int,
    ) : CommandItem {
        override val name: String get() = alias.name
        override val destination: String get() = alias.expansion
        override fun matches(query: String): Boolean {
            val q = query.foldAccents().trim()
            if (q.isEmpty()) return true
            return alias.name.foldAccents().contains(q) ||
                alias.expansion.foldAccents().contains(q)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommandLinePreference(
    name: String,
    destination: String,
    usageCount: Int,
    isSelected: Boolean,
    selectionMode: Boolean,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(name)
                        }
                        append("  ")
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append(destination)
                        }
                    },
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (usageCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "$usageCount×",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        leadingContent = if (selectionMode) {
            {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = null,
                )
            }
        } else {
            null
        },
        trailingContent = if (selectionMode) {
            null
        } else {
            {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = stringResource(if (favorite) R.string.cmd_pref_favorite_remove else R.string.cmd_pref_favorite_add),
                        tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            headlineColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    )
}

/** The name of the app that answers the home button, or null when the system names none. */
private fun defaultLauncherLabel(context: Context): String? {
    val pm = context.packageManager
    val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY) ?: return null
    if (home.activityInfo.packageName == "android") return null
    return home.loadLabel(pm).toString()
}

/** "JS › node -e function main(input, ctx) { …": a script in one line, cut short, for the lists. */
internal fun scriptSummary(action: CustomAction): String {
    val script = action.script
    val code = script?.code.orEmpty().trim()
    val first = code.lineSequence().first().take(48)
    return "JS › " + (if (script?.expr != null) "node -p " else "node -e ") + first + if (first.length < code.length) " …" else ""
}
