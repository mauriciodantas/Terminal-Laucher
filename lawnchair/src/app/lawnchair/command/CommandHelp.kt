package app.lawnchair.command

import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents
import com.android.launcher3.R

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
        custom.firstOrNull { it.letter == lead }?.let {
            return CommandHelpText(it.letter.uppercase() + " · " + it.label.uppercase(), Texts.get(R.string.cmd_help_custom) + "\n› " + it.usage)
        }
        if (lead == BuiltInCommand.TASK) return help(BuiltInCommand.TASK, R.string.cmd_help_task)
        val command = BuiltInCommand.of(lead) ?: return null
        return CommandHelpText(command.word.uppercase(), text(command))
    }

    /** Aliases that name other aliases are followed this deep, so a loop of them ends. */
    private const val MAX_DEPTH = 4

    private fun help(title: String, res: Int) = CommandHelpText(title.uppercase(), Texts.get(res))

    private fun text(command: BuiltInCommand): String = Texts.get(
        when (command) {
            BuiltInCommand.OPEN -> R.string.cmd_help_open
            BuiltInCommand.ALARM -> R.string.cmd_help_alarm
            BuiltInCommand.CALC -> R.string.cmd_help_calc
            BuiltInCommand.CALL -> R.string.cmd_help_call
            BuiltInCommand.ROUTE -> R.string.cmd_help_route
            BuiltInCommand.SPLIT -> R.string.cmd_help_split
            BuiltInCommand.THEME -> R.string.cmd_help_theme
            BuiltInCommand.TIMER -> R.string.cmd_help_timer
            BuiltInCommand.REMIND -> R.string.cmd_help_remind
            BuiltInCommand.WIFI -> R.string.cmd_help_wifi
            BuiltInCommand.BLUETOOTH -> R.string.cmd_help_bluetooth
            BuiltInCommand.TORCH -> R.string.cmd_help_torch
            BuiltInCommand.DND -> R.string.cmd_help_dnd
            BuiltInCommand.BRIGHTNESS -> R.string.cmd_help_brightness
            BuiltInCommand.ROTATION -> R.string.cmd_help_rotation
            BuiltInCommand.VOLUME -> R.string.cmd_help_volume
            BuiltInCommand.PLAY, BuiltInCommand.PAUSE, BuiltInCommand.NEXT, BuiltInCommand.PREVIOUS -> R.string.cmd_help_media
            BuiltInCommand.COPY -> R.string.cmd_help_copy
            BuiltInCommand.PASTE -> R.string.cmd_help_paste
            BuiltInCommand.SETTINGS -> R.string.cmd_help_settings
            BuiltInCommand.AGENDA -> R.string.cmd_help_agenda
            BuiltInCommand.NOTE -> R.string.cmd_help_note
            BuiltInCommand.SCHEDULED -> R.string.cmd_help_scheduled
            BuiltInCommand.CANCEL -> R.string.cmd_help_cancel
            BuiltInCommand.BATTERY -> R.string.cmd_help_battery
            BuiltInCommand.STORAGE -> R.string.cmd_help_storage
            BuiltInCommand.IP -> R.string.cmd_help_ip
            BuiltInCommand.UPTIME -> R.string.cmd_help_uptime
            BuiltInCommand.PING -> R.string.cmd_help_ping
        },
    )
}
