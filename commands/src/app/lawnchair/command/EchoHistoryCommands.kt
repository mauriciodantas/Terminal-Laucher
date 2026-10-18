package app.lawnchair.command

import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents

/** "echo ...": shows what a line would do, the intent or the request it builds, without running it. */
object EchoCommand : BuiltIn(BuiltInCommand.ECHO, R.string.cmd_usage_echo, R.string.cmd_help_echo) {

    override val requiresArgument: Boolean get() = true

    override fun analyze(arg: String, ctx: CommandContext): Analysis = CommandEngine.dryRun(arg, ctx)
}

/**
 * "history [text]": the commands that ran, most recent first and numbered, so "!3" runs the third.
 * A text narrows the list to the commands that hold it; "history clear" forgets them all.
 */
object HistoryCommand : BuiltIn(BuiltInCommand.HISTORY, R.string.cmd_usage_history, R.string.cmd_help_history) {

    /** How many commands the list shows at most. */
    private const val SHOWN = 30

    private val CLEAR = setOf("clear", "limpar", "apagar")

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val title = BuiltInCommand.HISTORY.word.uppercase()
        val entries = CommandEngine.recallable(ctx.extras.history)
        val query = arg.trim()
        if (query.foldAccents() in CLEAR) {
            return if (entries.isEmpty()) {
                commandResult(title, Texts.get(R.string.cmd_history_empty), Tone.IDLE, null)
            } else {
                commandResult(title, Texts.get(R.string.cmd_history_clear_ready, entries.size), Tone.WARN, CommandAction.ClearHistory)
            }
        }
        if (entries.isEmpty()) return commandResult(title, Texts.get(R.string.cmd_history_empty), Tone.IDLE, null)
        val numbered = entries.mapIndexed { index, line -> (index + 1) to line }
        val words = query.foldAccents().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val matches = if (words.isEmpty()) {
            numbered
        } else {
            numbered.filter { (_, line) -> line.foldAccents().let { l -> words.all { it in l } } }
        }
        val shown = matches.take(SHOWN)
        return Analysis(
            suggestions = shown.map { (n, line) -> Suggestion(line, line, "!$n") },
            listTitle = Texts.get(R.string.cmd_list_history),
            needsContacts = false,
            previewTitle = title,
            preview = Texts.get(if (matches.isEmpty()) R.string.cmd_history_no_match else R.string.cmd_history_hint, matches.size),
            tone = if (matches.isEmpty()) Tone.ERROR else Tone.IDLE,
            action = null,
            total = if (matches.size > shown.size) matches.size else 0,
            picksOnEnter = matches.isNotEmpty(),
        )
    }
}
