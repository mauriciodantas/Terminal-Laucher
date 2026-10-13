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

    /** The lines the command list and "?" show, word first: ("calc", "calc 12*8"). Empty keeps it out. */
    fun usages(): List<Pair<String, String>> = emptyList()

    /** Listed only while it can be used: "ia" shows up once a key is saved. */
    fun isListed(extras: CommandExtras): Boolean = true

    /** What the command does and how to write it, shown under the preview. */
    fun help(): CommandHelpText? = null

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
        /** The built-in commands, in the order the command list shows them. */
        val BUILT_IN: List<Command> by lazy {
            listOf(
                OpenCommand,
                AlarmCommand,
                CallCommand,
                RouteCommand,
                TaskCommand,
                CalcCommand,
                SplitCommand,
                ThemeCommand,
                TimerCommand,
                RemindCommand,
                NoteCommand,
                AgendaCommand,
                ToggleCommand(BuiltInCommand.TORCH, SystemToggle.TORCH, R.string.cmd_usage_switch, R.string.cmd_help_torch),
                ToggleCommand(BuiltInCommand.DND, SystemToggle.DND, R.string.cmd_usage_switch, R.string.cmd_help_dnd),
                ToggleCommand(BuiltInCommand.ROTATION, SystemToggle.ROTATION, R.string.cmd_usage_switch, R.string.cmd_help_rotation),
                ToggleCommand(BuiltInCommand.WIFI, SystemToggle.WIFI, R.string.cmd_usage_panel, R.string.cmd_help_wifi),
                ToggleCommand(BuiltInCommand.BLUETOOTH, SystemToggle.BLUETOOTH, R.string.cmd_usage_panel, R.string.cmd_help_bluetooth),
                BrightnessCommand,
                VolumeCommand,
                MediaCommand(BuiltInCommand.PLAY, MediaKey.PLAY),
                MediaCommand(BuiltInCommand.PAUSE, MediaKey.PAUSE),
                MediaCommand(BuiltInCommand.NEXT, MediaKey.NEXT),
                MediaCommand(BuiltInCommand.PREVIOUS, MediaKey.PREVIOUS),
                CopyCommand,
                PasteCommand,
                SettingsCommand,
                ScheduledCommand,
                CancelCommand,
                InfoCommand(BuiltInCommand.BATTERY, R.string.cmd_help_battery),
                InfoCommand(BuiltInCommand.STORAGE, R.string.cmd_help_storage),
                InfoCommand(BuiltInCommand.IP, R.string.cmd_help_ip),
                InfoCommand(BuiltInCommand.UPTIME, R.string.cmd_help_uptime),
                NetworkCommand,
                FastCommand,
                PingCommand,
                AiAskCommand,
                UrlCommand,
            )
        }

        fun of(custom: List<CustomAction>): CommandRegistry = CommandRegistry(BUILT_IN + custom.map { CustomCommand(it) })
    }
}
