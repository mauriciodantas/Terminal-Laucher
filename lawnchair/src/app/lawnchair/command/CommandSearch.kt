package app.lawnchair.command

import android.content.Context

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

    /** The offer for [query], or null when the bar would not make anything of it. */
    fun offer(context: Context, query: String): Offer? {
        val line = query.trim()
        if (line.isEmpty()) return null
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
        if (!runnable && analysis.tone != Tone.WARN) return null
        val preview = analysis.preview.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
            .take(MAX_PREVIEW_LINES).joinToString("\n")
        return Offer(line, preview, runnable)
    }
}
