package app.lawnchair.command

/** Everything a line is analyzed against. */
data class CommandContext(
    val apps: List<AppEntry>,
    val contacts: List<ContactEntry>,
    val contactsGranted: Boolean,
    val custom: List<CustomAction>,
    val aliases: List<CommandAlias>,
    val extras: CommandExtras,
)

/**
 * One thing the command bar understands. It runs when its word is typed ("calc 12*8") and, when it
 * has a [pattern] or overrides [isApplicable], when text it recognizes is typed alone ("12*8").
 */
interface Command {

    /** The word suggested and shown for the command, or empty for one that only answers to text it recognizes. */
    val word: String

    /** Every accent-folded word that runs the command. */
    fun names(): Set<String>

    /** Without an argument the command does not run: "calc" alone is only completed to "calc ". */
    val requiresArgument: Boolean get() = false

    /** Text typed alone that the command takes, matched against the whole trimmed line. */
    val pattern: Regex? get() = null

    fun isApplicable(text: String, ctx: CommandContext): Boolean = pattern?.matches(text.trim()) == true

    /** Where the command goes in the options when several take the same text; lower comes first. */
    val optionOrder: Int get() = 0

    /** [arg] is what follows the command word. */
    fun analyze(arg: String, ctx: CommandContext): Analysis

    /** Text typed alone that [isApplicable] took; by default the same as typing the word before it. */
    fun analyzeMatch(text: String, ctx: CommandContext): Analysis = analyze(text.trim(), ctx)

    /** How the command shows up when several take the same text; picking it types the whole command. */
    fun option(text: String, ctx: CommandContext): Suggestion = Suggestion("$word ${text.trim()}", word.uppercase(), CommandEngine.optionKind())
}

/** The commands of a line: the built-in ones, then the user's custom actions. */
class CommandRegistry(private val commands: List<Command>) {

    /** The command [token], already accent-folded, names; null when it names none. */
    fun named(token: String): Command? = commands.firstOrNull { token in it.names() }

    /** Every command that takes [text] typed alone, in the order the options show them. */
    fun applicable(text: String, ctx: CommandContext): List<Command> = commands.filter { it.isApplicable(text, ctx) }.sortedBy { it.optionOrder }

    companion object {
        /** The built-in commands; the ones still analyzed by [CommandEngine] go through [LegacyCommand]. */
        val BUILT_IN: List<Command>
            get() = BuiltInCommand.entries.map {
                when (it) {
                    BuiltInCommand.CALC -> CalcCommand
                    BuiltInCommand.CALL -> CallCommand
                    else -> LegacyCommand(it)
                }
            } + TaskCommand + UrlCommand

        fun of(custom: List<CustomAction>): CommandRegistry = CommandRegistry(BUILT_IN + custom.map { CustomCommand(it) })
    }
}

/** A built-in command whose rules still live in [CommandEngine]; each one moves to its own class over time. */
class LegacyCommand(private val command: BuiltInCommand) : Command {
    override val word: String get() = command.word
    override fun names(): Set<String> = command.words()
    override val requiresArgument: Boolean get() = command.requiresArgument
    override fun analyze(arg: String, ctx: CommandContext): Analysis = CommandEngine.analyzeBuiltIn(command, arg, ctx)
}
