package app.lawnchair.command

import app.lawnchair.util.Texts

/** "t comprar pão" sends a task to the tasks app picked before; with none, the system list asks once. */
object TaskCommand : Command {

    override val word: String get() = BuiltInCommand.TASK

    override fun names(): Set<String> = setOf(BuiltInCommand.TASK)

    override val requiresArgument = true

    override fun usages(): List<Pair<String, String>> = listOf(word to word + " " + Texts.get(R.string.cmd_usage_task))

    override fun help(): CommandHelpText = CommandHelpText(word.uppercase(), Texts.get(R.string.cmd_help_task))

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val title = arg.trim()
        val app = ctx.extras.taskApp
        return Analysis(
            suggestions = emptyList(),
            listTitle = Texts.get(R.string.cmd_task_list),
            needsContacts = false,
            previewTitle = if (app == null) Texts.get(R.string.cmd_task_title) else Texts.get(R.string.cmd_task_title_app, app.uppercase()),
            preview = if (title.isEmpty()) Texts.get(R.string.cmd_task_empty) else title.uppercase(),
            tone = if (title.isEmpty()) Tone.IDLE else Tone.OK,
            action = title.takeIf { it.isNotEmpty() }?.let { CommandAction.NewTask(it) },
        )
    }
}
