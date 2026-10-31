package app.lawnchair.command

import android.content.Context
import app.lawnchair.command.R as CommandR

/**
 * The command bar's reading of what the drawer search holds, so the search offers to run it
 * with the same preview the bar shows. Work off the main thread.
 */
object CommandSearch {

    /** What the drawer shows for [line]: the [preview] under it, and whether tapping it [runnable] runs right away. */
    data class Offer(val line: String, val preview: String, val runnable: Boolean)

    private const val KEEP_MS = 30_000L
    private const val MAX_PREVIEW_LINES = 2

    private class Snapshot(val at: Long, val apps: List<AppEntry>, val contacts: List<ContactEntry>, val extras: CommandExtras)

    @Volatile
    private var snapshot: Snapshot? = null

    /** The device is read once in a while, not on every key: apps, contacts, calendar and the rest. */
    private fun read(context: Context, history: List<String>): Snapshot {
        val now = System.currentTimeMillis()
        snapshot?.takeIf { now - it.at < KEEP_MS }?.let { return it }
        return Snapshot(
            at = now,
            apps = CommandExecutor.cachedApps ?: CommandExecutor.loadApps(context),
            contacts = CommandExecutor.loadContacts(context),
            extras = CommandSources.load(context, history),
        ).also { snapshot = it }
    }

    /** The commands the drawer lists while a name is only partly typed ("pin" offers "ping"). */
    private const val MAX_COMPLETIONS = 3

    /** Fewer letters than this match too many commands to be worth listing. */
    private const val MIN_COMPLETION_LETTERS = 2

    /**
     * What the drawer offers for [query]: the line itself when the bar can run it, otherwise the
     * commands it completes to, or the line with the hint of what it still lacks ("ping " asks
     * for a host). Empty when the bar would not make anything of it.
     */
    fun offers(context: Context, query: String): List<Offer> {
        val line = query.trim()
        if (line.isEmpty()) return emptyList()
        val history = CommandActivity.loadHistory(context)
        val now = read(context, history)
        val analysis = CommandEngine.analyze(
            line,
            now.apps,
            now.contacts,
            CommandExecutor.hasContactsPermission(context),
            CustomActionStore.load(context),
            AliasStore.load(context),
            now.extras.copy(history = history),
        )
        val runnable = analysis.action != null
        val preview = analysis.preview.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
            .take(MAX_PREVIEW_LINES).joinToString("\n")
        if (runnable || analysis.tone == Tone.WARN) return listOf(Offer(line, preview, runnable))
        val completions = if (line.length >= MIN_COMPLETION_LETTERS) {
            // Commands the user ran before are not offered here: the drawer lists commands, not the history.
            val recent = context.getString(CommandR.string.cmd_kind_recent)
            analysis.suggestions.filter {
                it.kind != recent && it.completion.isNotBlank() && !it.completion.trim().equals(line, ignoreCase = true)
            }
        } else {
            emptyList()
        }
        if (completions.isNotEmpty()) {
            // The completion goes to the bar as typed (with its space) so the argument can follow.
            return completions.take(MAX_COMPLETIONS).map { Offer(it.completion, it.label.takeIf { label -> label != it.completion.trim() }.orEmpty(), runnable = false) }
        }
        return if (analysis.help != null && preview.isNotEmpty()) listOf(Offer(line, preview, runnable = false)) else emptyList()
    }
}
