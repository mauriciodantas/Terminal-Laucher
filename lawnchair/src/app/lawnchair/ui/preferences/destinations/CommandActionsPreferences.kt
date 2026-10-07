package app.lawnchair.ui.preferences.destinations

import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import app.lawnchair.command.ACTION_VIEW
import app.lawnchair.command.ActionKind
import app.lawnchair.command.AliasStore
import app.lawnchair.command.Aliases
import app.lawnchair.command.AppEntry
import app.lawnchair.command.ArgKind
import app.lawnchair.command.CommandAction
import app.lawnchair.command.CommandAlias
import app.lawnchair.command.CommandExecutor
import app.lawnchair.command.CommandPack
import app.lawnchair.command.CommandPacks
import app.lawnchair.command.CustomAction
import app.lawnchair.command.CustomActionStore
import app.lawnchair.command.CustomActions
import app.lawnchair.command.ImportCommandsActivity
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where the user is in the "add or edit an action" flow. */
private sealed interface Step {
    data object Catalog : Step
    data object Apps : Step
    data class Shortcuts(val app: AppEntry) : Step
    data object Advanced : Step
    data class Letter(val draft: CustomAction, val editing: CustomAction?) : Step
}

/**
 * Binds letters of the command bar to actions of other apps: recipes from the catalog and the
 * shortcuts an app publishes to the launcher. "w" (WhatsApp) is just the first preset.
 */
@Composable
fun CommandActionsPreferences() {
    val context = LocalContext.current
    var actions by remember { mutableStateOf(CustomActionStore.load(context)) }
    var step by remember { mutableStateOf<Step?>(null) }
    var aliases by remember { mutableStateOf(AliasStore.load(context)) }
    // The alias being edited, or a blank one for a new alias; null when no dialog is open.
    var aliasDraft by remember { mutableStateOf<AliasDraft?>(null) }

    fun updateAliases(list: List<CommandAlias>) {
        aliases = list
        AliasStore.save(context, list)
    }

    fun update(list: List<CustomAction>) {
        actions = list
        CustomActionStore.save(context, list)
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
        Toast.makeText(context, if (ok) "Comandos exportados" else "Falha ao exportar", Toast.LENGTH_SHORT).show()
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

    PreferenceLayout(label = stringResource(R.string.command_actions_title)) {
        PreferenceGroup(heading = "Ações cadastradas") {
            if (actions.isEmpty()) {
                ClickablePreference(label = "Nenhuma ação", subtitle = "Adicione abaixo", onClick = {})
            }
            actions.forEach { action ->
                ClickablePreference(
                    label = "${action.letter} · ${action.label}",
                    subtitle = action.usage,
                    onClick = { step = Step.Letter(action, editing = action) },
                )
            }
        }
        PreferenceGroup(heading = "Apelidos") {
            aliases.forEach { alias ->
                ClickablePreference(
                    label = alias.name,
                    subtitle = "→ ${alias.expansion}",
                    onClick = { aliasDraft = AliasDraft(alias) },
                )
            }
            ClickablePreference(
                label = "Novo apelido",
                subtitle = "Uma palavra sua para um comando inteiro (mae = ligar maria). Vale digitado e falado",
                onClick = { aliasDraft = AliasDraft(null) },
            )
        }
        PreferenceGroup(heading = "Adicionar") {
            ClickablePreference(
                label = "Receitas prontas",
                subtitle = "WhatsApp, Telegram, SMS, YouTube, Spotify",
                onClick = { step = Step.Catalog },
            )
            ClickablePreference(
                label = "Funções de um app",
                subtitle = "Atalhos do launcher e intents que o app aceita",
                onClick = { step = Step.Apps },
            )
            ClickablePreference(
                label = "Avançado",
                subtitle = "Monte a intent manualmente (ação, URI, pacote)",
                onClick = { step = Step.Advanced },
            )
            ClickablePreference(
                label = "Compartilhar comandos",
                subtitle = "Escolha uma ou várias ações e apelidos e envie como arquivo",
                onClick = { sharing = ShareMode.Share },
            )
            ClickablePreference(
                label = "Exportar comandos",
                subtitle = "Escolha o que salvar em um arquivo",
                onClick = { sharing = ShareMode.Export },
            )
            ClickablePreference(
                label = "Importar comandos",
                subtitle = "Adiciona os de um arquivo, sem trocar os existentes",
                onClick = { importer.launch(arrayOf("*/*")) },
            )
            ClickablePreference(
                label = "Restaurar padrão",
                subtitle = "Volta apenas o w (WhatsApp)",
                onClick = { update(CustomActions.DEFAULTS) },
            )
        }
    }

    sharing?.let { mode ->
        SelectCommandsDialog(
            title = if (mode == ShareMode.Share) "Compartilhar comandos" else "Exportar comandos",
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
                        Toast.makeText(context, "Falha ao compartilhar", Toast.LENGTH_SHORT).show()
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
            onSave = { saved ->
                updateAliases(
                    if (draft.editing == null) aliases + saved else aliases.map { if (it === draft.editing) saved else it },
                )
                aliasDraft = null
            },
            onRemove = {
                updateAliases(aliases.filter { it !== draft.editing })
                aliasDraft = null
            },
            onDismiss = { aliasDraft = null },
        )
    }

    when (val s = step) {
        null -> Unit

        Step.Catalog -> PickerDialog(
            title = "Receitas prontas",
            items = CustomActions.CATALOG,
            label = { "${it.label} · ${it.usage.substringBefore(" ·")}" },
            onPick = { step = Step.Letter(it, editing = null) },
            onDismiss = { step = null },
        )

        Step.Apps -> {
            // Scanning every installed app takes a moment, so it runs off the main thread.
            val apps by produceState<List<AppEntry>?>(initialValue = null) {
                value = withContext(Dispatchers.Default) { CommandExecutor.loadIntegrationApps(context) }
            }
            val list = apps
            if (list == null || list.isEmpty()) {
                AlertDialog(
                    onDismissRequest = { step = null },
                    confirmButton = { TextButton(onClick = { step = null }) { Text("Fechar") } },
                    title = { Text("Apps com integrações") },
                    text = {
                        Text(
                            if (list == null) "Procurando apps…" else "Nenhum app com integrações externas. Use o modo Avançado.",
                        )
                    },
                )
            } else {
                PickerDialog(
                    title = "Apps com integrações",
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
                    confirmButton = { TextButton(onClick = { step = Step.Apps }) { Text("Voltar") } },
                    title = { Text(s.app.label) },
                    text = { Text("Nenhuma função encontrada. Use o modo Avançado para informar a intent.") },
                )
            } else {
                PickerDialog(
                    title = s.app.label,
                    items = found,
                    label = {
                        when {
                            it.kind == ActionKind.SHORTCUT -> "Atalho · "
                            it.letter.isNotEmpty() -> "Receita · "
                            else -> "Intent · "
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
                update(actions.filterNot { it === s.editing })
                step = null
            },
            onTest = { CommandExecutor.execute(context, CommandAction.Custom(s.draft)) },
            onDismiss = { step = null },
        )
    }
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
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

@Composable
private fun LetterDialog(
    step: Step.Letter,
    existing: List<CustomAction>,
    onSave: (CustomAction) -> Unit,
    onRemove: () -> Unit,
    onTest: () -> Unit,
    onDismiss: () -> Unit,
) {
    var letter by remember { mutableStateOf(step.draft.letter.ifEmpty { suggestLetter(step.draft.label, existing) }) }
    val error = CustomActions.validateLetter(letter, existing, step.editing)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(step.draft.label) },
        text = {
            Column {
                Text(step.draft.usage.replaceBefore(" ·", letter.trim().lowercase()))
                OutlinedTextField(
                    value = letter,
                    onValueChange = { letter = it.take(MAX_LETTER) },
                    singleLine = true,
                    label = { Text("Letra do atalho") },
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = error == null,
                onClick = { onSave(step.draft.copy(letter = letter.trim().lowercase())) },
            ) { Text("Salvar") }
        },
        dismissButton = {
            Column {
                if (step.draft.kind == ActionKind.SHORTCUT && step.draft.arg == ArgKind.NONE) {
                    TextButton(onClick = onTest) { Text("Testar") }
                }
                if (step.editing != null) TextButton(onClick = onRemove) { Text("Remover") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

private class AliasDraft(val editing: CommandAlias?)

/** Creates or edits an alias: the word and the command it stands for. */
@Composable
private fun AliasDialog(
    editing: CommandAlias?,
    aliases: List<CommandAlias>,
    custom: List<CustomAction>,
    onSave: (CommandAlias) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var expansion by remember { mutableStateOf(editing?.expansion.orEmpty()) }
    val error = Aliases.validate(name, expansion, aliases, custom, editing)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Novo apelido" else "Editar apelido") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.filter { c -> !c.isWhitespace() }.take(MAX_ALIAS) },
                    singleLine = true,
                    label = { Text("Apelido") },
                    supportingText = { Text("Digitado ou falado. Acentos não importam") },
                )
                OutlinedTextField(
                    value = expansion,
                    onValueChange = { expansion = it },
                    singleLine = true,
                    label = { Text("Comando") },
                    supportingText = { Text("Ex.: ligar maria · abrir chrome · w ana · rota casa") },
                    modifier = Modifier.padding(top = 8.dp),
                )
                // An empty form needs no complaint; the error appears as soon as something is typed.
                if (error != null && (name.isNotBlank() || expansion.isNotBlank())) {
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = error == null,
                onClick = { onSave(CommandAlias(name.trim().lowercase(), expansion.trim())) },
            ) { Text("Salvar") }
        },
        dismissButton = {
            Column {
                if (editing != null) TextButton(onClick = onRemove) { Text("Remover") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
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
                if (total == 0) Text("Não há ações nem apelidos cadastrados.")
                if (actions.isNotEmpty()) Text("Ações", style = MaterialTheme.typography.titleSmall)
                actions.forEach { Item(it, "${it.letter} · ${it.label}", chosenActions) { s -> chosenActions = s } }
                if (aliases.isNotEmpty()) {
                    Text("Apelidos", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
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
            ) { Text(if (count > 0) "Continuar ($count)" else "Continuar") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        val all = count == total
                        chosenActions = if (all) emptySet() else actions.toSet()
                        chosenAliases = if (all) emptySet() else aliases.toSet()
                    },
                ) { Text(if (count == total) "Nenhum" else "Todos") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
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
        title = { Text("Ação manual") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(label, { label = it }, singleLine = true, label = { Text("Nome") })
                OutlinedTextField(
                    action,
                    { action = it.trim() },
                    singleLine = true,
                    label = { Text("Ação da intent") },
                    supportingText = { Text("VIEW, SEND, SENDTO, DIAL ou um nome completo") },
                )
                OutlinedTextField(
                    template,
                    { template = it.trim() },
                    singleLine = true,
                    label = { Text("URI") },
                    supportingText = { Text("Ex.: tg://resolve?phone={number} · use {text}, {name}, {phone}") },
                )
                OutlinedTextField(
                    pkg,
                    { pkg = it.trim() },
                    singleLine = true,
                    label = { Text("Pacote (opcional)") },
                )
                Text("O que digitar depois da letra", modifier = Modifier.padding(top = 12.dp))
                ArgKind.entries.forEach {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { arg = it },
                    ) {
                        RadioButton(selected = arg == it, onClick = { arg = it })
                        Text(argLabel(it))
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { shareText = !shareText },
                ) {
                    Checkbox(checked = shareText, onCheckedChange = { shareText = it })
                    Text("Enviar o texto como EXTRA_TEXT (tipo text/plain)")
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
            ) { Text("Continuar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun argLabel(arg: ArgKind) = when (arg) {
    ArgKind.NONE -> "Nada (roda só com a letra)"
    ArgKind.TEXT -> "Um texto"
    ArgKind.CONTACT -> "Um contato"
    ArgKind.CONTACT_AND_TEXT -> "Um contato e uma mensagem"
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
