package app.lawnchair.command

import android.content.Context
import org.json.JSONObject

/** How many times each action letter and alias was run, to show in settings and clear out dead ones. */
object CommandUsage {

    private const val STORE = "nostromo"
    private const val KEY = "command_usage"

    fun actionKey(letter: String) = "action:$letter"

    fun aliasKey(name: String) = "alias:" + Aliases.key(name)

    /** The counters a run of [action] typed as [command] adds to: its letter and the alias it started with. */
    fun keysFor(action: CommandAction, command: String, aliases: List<CommandAlias>): List<String> = buildList {
        if (action is CommandAction.Custom) add(actionKey(action.action.letter))
        val first = command.trimStart().takeWhile { !it.isWhitespace() }
        Aliases.find(first, aliases)?.let { add(aliasKey(it.name)) }
    }

    fun load(context: Context): Map<String, Int> {
        val raw = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { o.getInt(it) }
        }.getOrDefault(emptyMap())
    }

    /** Counts one run of each of [keys]. */
    fun record(context: Context, keys: List<String>) {
        if (keys.isEmpty()) return
        val counts = load(context).toMutableMap()
        keys.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
            .putString(KEY, JSONObject(counts as Map<*, *>).toString()).apply()
    }

    /** "usado 14×", or an empty string when it never ran. */
    fun label(counts: Map<String, Int>, key: String): String =
        counts[key]?.takeIf { it > 0 }?.let { "usado $it×" }.orEmpty()
}
