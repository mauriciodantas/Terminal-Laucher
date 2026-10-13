package app.lawnchair.command

import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents

/** The help shown under the preview: what the command being typed does and how to write it. */
data class CommandHelpText(val title: String, val text: String)

/**
 * Finds the command the line names and explains it: a built-in word, a custom letter, an alias or
 * the start of a scheduled command. In a chain it explains the command being typed, the last one.
 * Pure, so it can be unit tested.
 */
object CommandHelp {

    fun of(typed: String, custom: List<CustomAction>, aliases: List<CommandAlias>, depth: Int = 0): CommandHelpText? {
        if (depth > MAX_DEPTH) return null
        val line = CommandEngine.splitChain(typed).last().trim()
        if (line.isEmpty()) return null
        val first = line.takeWhile { !it.isWhitespace() }
        if (first == "?") return help(Texts.get(R.string.cmd_help), R.string.cmd_help_general)
        if (first == BuiltInCommand.REPEAT) return help(BuiltInCommand.REPEAT, R.string.cmd_help_repeat)
        // "em 30m: tema escuro" explains the command after the colon once it is being typed.
        val lead = first.foldAccents()
        if (TimeParse.isIn(lead) || TimeParse.isAt(lead) || TimeParse.isTomorrow(lead) || TimeParse.isWhen(lead)) {
            val after = line.substringAfter(':', "").trim()
            if (after.isNotEmpty()) return of(after, custom, aliases, depth + 1)
            return CommandHelpText(
                Texts.get(R.string.cmd_schedule_title),
                Texts.get(R.string.cmd_help_schedule, TriggerEvent.entries.joinToString(", ") { it.word }),
            )
        }
        Aliases.find(first, aliases)?.let { alias ->
            val expanded = alias.expansion.trim()
            val inner = of(expanded, custom, aliases, depth + 1)
            val text = Texts.get(R.string.cmd_help_alias, expanded) + (inner?.let { "\n\n" + it.text }.orEmpty())
            return CommandHelpText(alias.name.uppercase(), text)
        }
        return CommandRegistry.of(custom).named(lead)?.help()
    }

    /** Aliases that name other aliases are followed this deep, so a loop of them ends. */
    private const val MAX_DEPTH = 4

    private fun help(title: String, res: Int) = CommandHelpText(title.uppercase(), Texts.get(res))
}
