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
 * Opens a routines file (`.trtn`, or the JSON pasted in the bar) and asks before adding it. A rule
 * or a scheduled command runs by itself later, so every routine is shown as the line it will run and
 * has its own checkbox. The letters and aliases the file brings go through the usual command import
 * screen afterwards, which shows what each one does.
 */
class ImportRoutinesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pack = incomingUri(intent)?.let { RoutinePacks.read(this, it) }
        if (pack == null) {
            Toast.makeText(this, R.string.routines_import_invalid, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val extras = CommandExtras()
        val actions = CustomActionStore.load(this)
        val aliases = AliasStore.load(this)
        val commands = WidgetPacks.newCommands(pack.commands, actions, aliases)
        val split = RoutinePacks.splitCommands(commands, PackRules.preview(actions, aliases, commands, ProviderStore.load(this)))
        val existing = JobStore.load(this)
        // Each routine with the job it becomes now, and whether the user already has it.
        val entries = pack.routines.mapNotNull { spec ->
            val job = RoutinePacks.toJob(spec, extras) ?: return@mapNotNull null
            Entry(job, RoutinePacks.line(spec, extras), RoutinePacks.isDuplicate(job, existing))
        }
        setContent {
            LawnchairTheme {
                ImportDialog(
                    name = pack.name,
                    entries = entries,
                    skipped = pack.skipped + (pack.routines.size - entries.size),
                    safe = split.safe,
                    reviewCount = split.review.actions.size + split.review.aliases.size,
                    onAdd = { chosen, own -> add(chosen, own, split.review) },
                    onDismiss = ::finish,
                )
            }
        }
    }

    private class Entry(val job: ScheduledJob, val line: String, val duplicate: Boolean)

    private fun add(chosen: List<ScheduledJob>, own: CommandPack, review: CommandPack) {
        // The commands the routines use come first, so they exist when the first one runs.
        if (!own.isEmpty) {
            val installed = WidgetPacks.install(this, own)
            if (installed.keysPending > 0) Toast.makeText(this, getString(R.string.cmd_imp_keys_pending, installed.keysPending), Toast.LENGTH_LONG).show()
        }
        var problem: Int? = null
        chosen.forEach { job ->
            val saved = CommandScheduler.add(this, job)
            if (problem == null) problem = CommandScheduler.notificationProblem(this, saved)
        }
        Toast.makeText(this, getString(R.string.routines_import_done, chosen.size), Toast.LENGTH_LONG).show()
        problem?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        if (!review.isEmpty) {
            // What cannot come in unseen (a clash, a script, something that sends data out) goes through the command import screen.
            val file = java.io.File(cacheDir, "commands").apply { mkdirs() }.resolve("import.${CommandPacks.EXTENSION}")
            file.writeText(CommandPacks.encode(review.actions, review.aliases, review.providers))
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            startActivity(
                Intent(this, ImportCommandsActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .setData(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }
        finish()
    }

    private fun incomingUri(intent: Intent): Uri? = when (intent.action) {
        Intent.ACTION_VIEW -> intent.data

        Intent.ACTION_SEND ->
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)

        else -> null
    }

    @androidx.compose.runtime.Composable
    private fun ImportDialog(
        name: String,
        entries: List<Entry>,
        skipped: Int,
        safe: CommandPack,
        reviewCount: Int,
        onAdd: (List<ScheduledJob>, CommandPack) -> Unit,
        onDismiss: () -> Unit,
    ) {
        // What the user already has starts unticked; the rest starts ticked, since every line is in front of them.
        var on by remember { mutableStateOf(entries.indices.filter { !entries[it].duplicate }.toSet()) }
        // The commands the routines need come in with them, each with its own box.
        var actionsOn by remember { mutableStateOf(safe.actions.indices.toSet()) }
        var aliasesOn by remember { mutableStateOf(safe.aliases.indices.toSet()) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(name.ifBlank { stringResource(R.string.routines_import_title) }) },
            text = {
                Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.routines_import_summary, entries.size), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.routines_import_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (skipped > 0) {
                        Text(
                            stringResource(R.string.routines_import_skipped, skipped),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (reviewCount > 0) {
                        Text(
                            stringResource(R.string.routines_import_commands, reviewCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    entries.forEachIndexed { index, entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { on = if (index in on) on - index else on + index }.padding(top = 8.dp),
                        ) {
                            Checkbox(checked = index in on, onCheckedChange = { on = if (it) on + index else on - index })
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text(entry.line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                                if (entry.duplicate) {
                                    Text(
                                        stringResource(R.string.routines_import_have),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    if (safe.actions.isNotEmpty() || safe.aliases.isNotEmpty()) {
                        Text(
                            stringResource(R.string.routines_import_needs),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                    safe.actions.forEachIndexed { index, action ->
                        CommandRow("${action.letter} · ${action.label}", index in actionsOn) { actionsOn = if (it) actionsOn + index else actionsOn - index }
                    }
                    safe.aliases.forEachIndexed { index, alias ->
                        CommandRow("${alias.name} → ${alias.expansion}", index in aliasesOn) { aliasesOn = if (it) aliasesOn + index else aliasesOn - index }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = on.isNotEmpty(),
                    onClick = {
                        val actions = safe.actions.filterIndexed { i, _ -> i in actionsOn }
                        val own = CommandPack(
                            actions,
                            safe.aliases.filterIndexed { i, _ -> i in aliasesOn },
                            providers = safe.providers.filter { p -> actions.any { it.provider == p.id } },
                        )
                        onAdd(on.sorted().map { entries[it].job }, own)
                    },
                ) {
                    Text(stringResource(R.string.routines_import_add, on.size))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        )
    }

    @androidx.compose.runtime.Composable
    private fun CommandRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(top = 4.dp),
        ) {
            Checkbox(checked = checked, onCheckedChange = onChange)
            Text(title, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 4.dp))
        }
    }
}
