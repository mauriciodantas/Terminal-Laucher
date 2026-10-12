package app.lawnchair.command

import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents
import com.android.launcher3.R

data class AppEntry(val label: String, val id: String)

data class ContactEntry(val name: String, val number: String, val email: String = "")

/** How the preview box is colored: neutral, a command that can run, or a problem. */
enum class Tone { IDLE, OK, WARN, ERROR }

data class Suggestion(val completion: String, val label: String, val kind: String)

/** What running the command does. */
sealed interface CommandAction {
    data class OpenApp(val app: AppEntry) : CommandAction

    /** [days] are [java.util.Calendar] day numbers for a repeating alarm; empty for a single one. */
    data class SetAlarm(val hour: Int, val minute: Int, val days: List<Int> = emptyList()) : CommandAction
    data class SetTimer(val seconds: Int) : CommandAction
    data class Calc(val expression: String, val result: String) : CommandAction
    data class Call(val contact: ContactEntry) : CommandAction

    /** A user-defined command: [action] filled with the contact and/or text that was typed. */
    data class Custom(
        val action: CustomAction,
        val contact: ContactEntry? = null,
        val text: String = "",
    ) : CommandAction
    data class Route(val query: String) : CommandAction

    /** Two apps side by side in split screen. */
    data class SplitScreen(val first: AppEntry, val second: AppEntry) : CommandAction
    data class NewTask(val title: String) : CommandAction

    /** Inverts the theme colors, lays them light or dark, or makes them follow the system mode. */
    data class Theme(val change: ThemeChange) : CommandAction

    /** Sets what [spec] has: one element ("tema fundo #000"), a saved theme or a ready-made look. */
    data class ThemeApply(val spec: ThemeSpec) : CommandAction

    /** Keeps the theme on screen now under [name]. */
    data class ThemeSave(val name: String) : CommandAction

    /** Sends the theme called [name] as a file; an empty name sends the one on screen. */
    data class ThemeShare(val name: String) : CommandAction
    data class ThemeDelete(val name: String) : CommandAction
    data class WebSearch(val query: String) : CommandAction

    /** A web address typed on its own: it opens in the browser instead of being searched. */
    data class OpenUrl(val url: String) : CommandAction

    /** A reminder, a command at a time or a rule; the id is given when it is saved. */
    data class Schedule(val job: ScheduledJob) : CommandAction
    data class CancelJob(val job: ScheduledJob) : CommandAction

    /** [on] null flips the switch. */
    data class Toggle(val target: SystemToggle, val on: Boolean?) : CommandAction

    /** [percent] null is automatic brightness. */
    data class Brightness(val percent: Int?) : CommandAction
    data class Volume(val percent: Int) : CommandAction
    data class Media(val key: MediaKey) : CommandAction
    data class Copy(val text: String) : CommandAction

    /** Puts [text] on the command line instead of running anything: "colar", an AI suggestion. */
    data class FillLine(val text: String) : CommandAction
    data class OpenSettings(val page: SettingsPage) : CommandAction
    data class OpenCalendar(val at: Long) : CommandAction
    data class AddNote(val text: String) : CommandAction
    data class ShareNotes(val text: String) : CommandAction

    /** Runs and shows its output in the bar instead of closing it. */
    data class Ping(val host: String) : CommandAction

    /**
     * "ia ...": talks to the AI the user set up; the bar shows the answer or fills in the command.
     * [force] is for "ia pastas --force" only: apps already in folders may move too.
     */
    data class Ai(val mode: AiMode, val prompt: String, val force: Boolean = false) : CommandAction

    /** "tema escuro; silencio on; abrir kindle": [steps] run in order. */
    data class Chain(val steps: List<CommandAction>, val lines: List<String>) : CommandAction
}

/** Everything the screen needs for the text typed so far. [action] is null while it cannot run. */
data class Analysis(
    val suggestions: List<Suggestion>,
    val listTitle: String,
    val needsContacts: Boolean,
    val previewTitle: String,
    val preview: String,
    val tone: Tone,
    val action: CommandAction?,
    /** The technical line of what [action] asks the system to do ("tel:+55…"); empty when it cannot run. */
    val intent: String = "",
    /** How many suggestions exist when the list is cut short; 0 when nothing was cut. */
    val total: Int = 0,
    /** A permission the command waits for, besides contacts. */
    val need: Need? = null,
    /** What the command being typed does and how to write it, shown under the preview. */
    val help: CommandHelpText? = null,
)

/** A one-tap start for the empty command line: a recent command, an alias or a custom letter. */
data class QuickChip(val fill: String, val label: String, val kind: String)

/** Pure rules of the command bar, free of Android types so they can be unit tested. */
object CommandEngine {

    const val MAX_SUGGESTIONS = 4

    /** With nothing typed there is room to show more of what exists. */
    const val EMPTY_SUGGESTIONS = 6
    const val MAX_CHIPS = 3
    const val MAX_HISTORY = 8

    /** Name, usage line, in the app language. "c" is a shortcut of calc. */
    val COMMANDS: List<Pair<String, String>>
        get() = listOf(
            BuiltInCommand.OPEN to R.string.cmd_usage_open,
            BuiltInCommand.ALARM to R.string.cmd_usage_alarm,
            BuiltInCommand.CALC to R.string.cmd_usage_calc,
            BuiltInCommand.CALL to R.string.cmd_usage_call,
            BuiltInCommand.ROUTE to R.string.cmd_usage_route,
        ).map { (command, usage) -> command.word to command.word + " " + Texts.get(usage) } +
            listOf(
                BuiltInCommand.TASK to BuiltInCommand.TASK + " " + Texts.get(R.string.cmd_usage_task),
                BuiltInCommand.CALC_SHORT to BuiltInCommand.CALC_SHORT + " " + Texts.get(R.string.cmd_usage_calc_short),
                BuiltInCommand.SPLIT.word to BuiltInCommand.SPLIT.word + " " + Texts.get(R.string.cmd_usage_split),
                BuiltInCommand.THEME.word to BuiltInCommand.THEME.word + " " + ThemeChange.usage(),
            ) +
            listOf(
                BuiltInCommand.TIMER to R.string.cmd_usage_timer,
                BuiltInCommand.REMIND to R.string.cmd_usage_remind,
                BuiltInCommand.NOTE to R.string.cmd_usage_note,
                BuiltInCommand.AGENDA to R.string.cmd_usage_agenda,
                BuiltInCommand.TORCH to R.string.cmd_usage_switch,
                BuiltInCommand.DND to R.string.cmd_usage_switch,
                BuiltInCommand.ROTATION to R.string.cmd_usage_switch,
                BuiltInCommand.WIFI to R.string.cmd_usage_panel,
                BuiltInCommand.BLUETOOTH to R.string.cmd_usage_panel,
                BuiltInCommand.BRIGHTNESS to R.string.cmd_usage_brightness,
                BuiltInCommand.VOLUME to R.string.cmd_usage_volume,
                BuiltInCommand.PLAY to R.string.cmd_usage_media,
                BuiltInCommand.PAUSE to R.string.cmd_usage_media,
                BuiltInCommand.NEXT to R.string.cmd_usage_media,
                BuiltInCommand.PREVIOUS to R.string.cmd_usage_media,
                BuiltInCommand.COPY to R.string.cmd_usage_copy,
                BuiltInCommand.PASTE to R.string.cmd_usage_paste,
                BuiltInCommand.SETTINGS to R.string.cmd_usage_settings,
                BuiltInCommand.SCHEDULED to R.string.cmd_usage_scheduled,
                BuiltInCommand.CANCEL to R.string.cmd_usage_cancel,
                BuiltInCommand.BATTERY to R.string.cmd_usage_info,
                BuiltInCommand.STORAGE to R.string.cmd_usage_info,
                BuiltInCommand.IP to R.string.cmd_usage_info,
                BuiltInCommand.UPTIME to R.string.cmd_usage_info,
                BuiltInCommand.PING to R.string.cmd_usage_ping,
                BuiltInCommand.AI to R.string.cmd_usage_ai,
            ).map { (command, usage) -> command.word to command.word + " " + Texts.get(usage) } +
            listOf(
                BuiltInCommand.REPEAT to BuiltInCommand.REPEAT + " " + Texts.get(R.string.cmd_usage_repeat),
            )

    private val splitter = Regex("^(\\S*)(\\s+(.*))?$", RegexOption.DOT_MATCHES_ALL)

    fun analyze(
        typed: String,
        apps: List<AppEntry>,
        contacts: List<ContactEntry>,
        contactsGranted: Boolean,
        custom: List<CustomAction> = CustomActions.DEFAULTS,
        aliases: List<CommandAlias> = emptyList(),
        extras: CommandExtras = CommandExtras(),
    ): Analysis {
        val ctx = Ctx(apps, contacts, contactsGranted, custom, aliases, extras)
        val analysis = analyzeLine(typed, ctx, 0).copy(help = CommandHelp.of(typed, custom, aliases))
        return analysis.action?.let { analysis.copy(intent = describe(it)) } ?: analysis
    }

    /** Everything a line is analyzed against. */
    private data class Ctx(
        val apps: List<AppEntry>,
        val contacts: List<ContactEntry>,
        val contactsGranted: Boolean,
        val custom: List<CustomAction>,
        val aliases: List<CommandAlias>,
        val extras: CommandExtras,
    )

    /** How deep aliases that hold chains may nest before the bar stops expanding them. */
    private const val MAX_DEPTH = 4

    /**
     * A whole line: "!!" repeats the last command, an alias stands for its command, "em 30m: ..."
     * schedules what follows and ";" chains several commands.
     */
    private fun analyzeLine(typed: String, ctx: Ctx, depth: Int): Analysis {
        if (typed.trim() == BuiltInCommand.REPEAT) {
            val last = ctx.extras.history.firstOrNull { it.trim() != BuiltInCommand.REPEAT }
                ?: return Analysis(
                    suggestions = emptyList(),
                    listTitle = Texts.get(R.string.cmd_list_suggestions),
                    needsContacts = false,
                    previewTitle = Texts.get(R.string.cmd_repeat_title),
                    preview = Texts.get(R.string.cmd_repeat_empty),
                    tone = Tone.IDLE,
                    action = null,
                )
            val again = analyzeLine(last, ctx, depth + 1)
            return again.copy(previewTitle = Texts.get(R.string.cmd_repeat_title) + " · " + again.previewTitle, suggestions = emptyList())
        }
        // An alias typed in full stands for its command; a partial one is only suggested.
        val text = Aliases.resolve(typed, ctx.aliases).trimStart()
        if (depth == 0) MoreCommands.schedule(text, ctx.extras) { analyzeLine(it, ctx, depth + 1) }?.let { return it }
        val parts = splitChain(text)
        if (parts.size > 1 && depth < MAX_DEPTH) return analyzeChain(text, parts, ctx, depth)
        return analyzeText(text, ctx)
    }

    /** The commands of "a; b; c", keeping an empty last one while it is being typed. */
    fun splitChain(text: String): List<String> {
        if (';' !in text) return listOf(text)
        val parts = text.split(';').map { it.trim() }
        return parts.dropLast(1).filter { it.isNotEmpty() } + parts.last()
    }

    private fun analyzeChain(text: String, parts: List<String>, ctx: Ctx, depth: Int): Analysis {
        val results = parts.map { analyzeLine(it, ctx, depth + 1) }
        // A part the bar does not understand would only search the web: the chain waits for a fix.
        val stuck = results.indexOfFirst { it.action == null || it.tone == Tone.ERROR }
        if (stuck >= 0) {
            val r = results[stuck]
            // While the last command is typed, its suggestions complete the whole line.
            val prefix = text.substring(0, text.lastIndexOf(';') + 1) + " "
            return r.copy(
                previewTitle = Texts.get(R.string.cmd_chain_step, stuck + 1, parts.size) + " · " + r.previewTitle,
                action = null,
                suggestions = if (stuck == parts.size - 1) r.suggestions.map { it.copy(completion = prefix + it.completion) } else emptyList(),
            )
        }
        val steps = results.flatMap { (it.action as? CommandAction.Chain)?.steps ?: listOf(it.action!!) }
        return Analysis(
            suggestions = emptyList(),
            listTitle = Texts.get(R.string.cmd_list_suggestions),
            needsContacts = false,
            previewTitle = Texts.get(R.string.cmd_chain_title, steps.size),
            preview = results.joinToString(" › ") { it.preview.lineSequence().first() },
            tone = Tone.OK,
            action = CommandAction.Chain(steps, parts),
        )
    }

    private fun analyzeText(typed: String, ctx: Ctx): Analysis {
        val apps = ctx.apps
        val contacts = ctx.contacts
        val contactsGranted = ctx.contactsGranted
        val custom = ctx.custom
        val aliases = ctx.aliases
        val extras = ctx.extras
        val expansion = Variables.expand(typed, extras)
        if (expansion.missing.isNotEmpty()) missingVariables(expansion.missing, extras)?.let { return it }
        val text = expansion.text
        val m = splitter.find(text)
        val token = m?.groupValues?.get(1).orEmpty().foldAccents()
        val hasArg = m != null && m.groups[2] != null
        val arg = if (hasArg) m?.groups?.get(3)?.value.orEmpty() else ""

        // A web address on its own opens the site; anything else the bar does not know is still searched.
        WebAddress.of(text)?.let { return analyzeUrl(it) }
        if (!hasArg) {
            if (token == "?") return analyzeHelp(custom, aliases, extras)
            if (token.length > 1 && token.endsWith("?")) {
                MoreCommands.contactInfo(text.trim().dropLast(1), contacts, contactsGranted)?.let { return it }
                if (!contactsGranted) return needsContactsAnalysis()
            }
            val bare = custom.firstOrNull { it.letter == token && it.arg == ArgKind.NONE }
            if (bare != null) return analyzeCustom(bare, "", contacts, contactsGranted, extras)
            BuiltInCommand.of(token)?.takeIf { it.runsBare }?.let { return analyzeBuiltIn(it, "", ctx) }
            return analyzeCommandName(text, token, apps, custom, aliases, extras)
        }

        if (token == BuiltInCommand.TASK) return analyzeTask(arg, ctx.extras.taskApp)
        val command = BuiltInCommand.of(token)
        if (command != null) return analyzeBuiltIn(command, arg, ctx)
        return custom.firstOrNull { it.letter == token }
            ?.let { analyzeCustom(it, arg, contacts, contactsGranted, extras) }
            ?: analyzeOpen(text.trim(), apps).takeIf { it.action != null }
            ?: unknown(text)
    }

    private fun analyzeUrl(url: String): Analysis = Analysis(
        suggestions = emptyList(),
        listTitle = Texts.get(R.string.cmd_list_suggestions),
        needsContacts = false,
        previewTitle = Texts.get(R.string.cmd_url_title),
        preview = url.substringAfter("://").removeSuffix("/"),
        tone = Tone.OK,
        action = CommandAction.OpenUrl(url),
    )

    /** A line the bar does not understand: Enter searches it on the web. */
    private fun unknown(text: String): Analysis = Analysis(
        suggestions = emptyList(),
        listTitle = Texts.get(R.string.cmd_list_suggestions),
        needsContacts = false,
        previewTitle = Texts.get(R.string.cmd_unknown_title),
        preview = Texts.get(R.string.cmd_unknown_preview),
        tone = Tone.ERROR,
        action = text.trim().takeIf { it.isNotEmpty() }?.let { CommandAction.WebSearch(it) },
    )

    /** The preview when a variable has no value: location can be granted, the others are just empty. */
    private fun missingVariables(missing: Set<Variables.Var>, extras: CommandExtras): Analysis? {
        val location = Variables.Var.LOCATION in missing || Variables.Var.MAPS in missing
        return Analysis(
            suggestions = emptyList(),
            listTitle = Texts.get(R.string.cmd_list_suggestions),
            needsContacts = false,
            previewTitle = Texts.get(R.string.cmd_variables_title),
            preview = when {
                location && !extras.locationGranted -> Texts.get(R.string.cmd_location_pending)
                location -> Texts.get(R.string.cmd_location_unknown)
                Variables.Var.CLIP in missing -> Texts.get(R.string.cmd_paste_empty)
                else -> Texts.get(R.string.cmd_selection_empty)
            },
            tone = Tone.WARN,
            action = null,
            need = if (location && !extras.locationGranted) Need.LOCATION else null,
        )
    }

    private fun analyzeBuiltIn(command: BuiltInCommand, arg: String, ctx: Ctx): Analysis {
        val apps = ctx.apps
        val contacts = ctx.contacts
        val contactsGranted = ctx.contactsGranted
        val extras = ctx.extras
        return when (command) {
            BuiltInCommand.OPEN -> analyzeOpen(arg, apps)

            BuiltInCommand.CALL -> analyzeContact(arg, contacts, contactsGranted, BuiltInCommand.CALL.word, Texts.get(R.string.cmd_call_title)) {
                CommandAction.Call(it)
            }

            BuiltInCommand.ALARM -> analyzeAlarm(arg)

            BuiltInCommand.CALC -> analyzeCalc(arg, extras.rates)

            BuiltInCommand.ROUTE -> analyzeRoute(arg)

            BuiltInCommand.SPLIT -> analyzeSplit(arg, apps)

            BuiltInCommand.THEME -> ThemeCommand.analyze(arg, extras)

            BuiltInCommand.TIMER -> MoreCommands.timer(arg)

            BuiltInCommand.REMIND -> MoreCommands.remind(arg, extras)

            BuiltInCommand.WIFI -> MoreCommands.toggle(command, SystemToggle.WIFI, arg)

            BuiltInCommand.BLUETOOTH -> MoreCommands.toggle(command, SystemToggle.BLUETOOTH, arg)

            BuiltInCommand.TORCH -> MoreCommands.toggle(command, SystemToggle.TORCH, arg)

            BuiltInCommand.DND -> MoreCommands.toggle(command, SystemToggle.DND, arg)

            BuiltInCommand.ROTATION -> MoreCommands.toggle(command, SystemToggle.ROTATION, arg)

            BuiltInCommand.BRIGHTNESS -> MoreCommands.brightness(arg)

            BuiltInCommand.VOLUME -> MoreCommands.volume(arg)

            BuiltInCommand.PLAY -> MoreCommands.media(MediaKey.PLAY)

            BuiltInCommand.PAUSE -> MoreCommands.media(MediaKey.PAUSE)

            BuiltInCommand.NEXT -> MoreCommands.media(MediaKey.NEXT)

            BuiltInCommand.PREVIOUS -> MoreCommands.media(MediaKey.PREVIOUS)

            BuiltInCommand.COPY -> MoreCommands.copy(arg)

            BuiltInCommand.PASTE -> MoreCommands.paste(extras)

            BuiltInCommand.SETTINGS -> MoreCommands.settings(arg)

            BuiltInCommand.AGENDA -> MoreCommands.agenda(extras)

            BuiltInCommand.NOTE -> MoreCommands.note(arg, extras)

            BuiltInCommand.SCHEDULED -> MoreCommands.scheduled(extras)

            BuiltInCommand.CANCEL -> MoreCommands.cancel(arg, extras)

            BuiltInCommand.BATTERY, BuiltInCommand.STORAGE, BuiltInCommand.IP, BuiltInCommand.UPTIME -> MoreCommands.info(command, extras)

            BuiltInCommand.PING -> MoreCommands.ping(arg)

            BuiltInCommand.AI -> MoreCommands.ai(arg, extras)
        }
    }

    private fun analyzeCommandName(
        text: String,
        token: String,
        apps: List<AppEntry>,
        custom: List<CustomAction>,
        aliases: List<CommandAlias>,
        extras: CommandExtras,
    ): Analysis {
        val aliasItems = Aliases.matching(token, aliases).map { Suggestion(it.name, it.expansion, Texts.get(R.string.cmd_kind_alias)) }
        // Commands run before that start with what is typed: "ab" offers "abrir chrome".
        val recent = if (token.isEmpty()) {
            emptyList()
        } else {
            extras.history.filter { it.foldAccents().startsWith(token) && it.trim().foldAccents() != token && it.trim() != BuiltInCommand.REPEAT }
                .map { Suggestion(it, it, Texts.get(R.string.cmd_kind_recent)) }
        }
        val all = commands(extras) + custom.map { it.letter to it.usage }
        val commands = all.filter { it.first.foldAccents().startsWith(token) }.map {
            Suggestion(it.first + " ", it.second, commandKind(it.first))
        }
        val matchingApps = if (token.isEmpty()) {
            emptyList()
        } else {
            apps.filter { it.label.foldAccents().startsWith(token) }.map {
                Suggestion(BuiltInCommand.OPEN.word + " " + it.label.lowercase(), it.label, Texts.get(R.string.cmd_kind_program))
            }
        }
        // A partial word puts the user's own aliases first; with nothing typed the commands lead.
        val ordered = if (token.isEmpty()) commands + aliasItems + matchingApps else aliasItems + commands + recent + matchingApps
        val limit = if (token.isEmpty()) EMPTY_SUGGESTIONS else MAX_SUGGESTIONS
        val items = ordered.take(limit)
        // Nothing is a command or alias: a bare word is taken as "abrir <word>" (open).
        if (commands.isEmpty() && aliasItems.isEmpty() && recent.isEmpty() && token.isNotEmpty()) {
            val open = analyzeOpen(text.trim(), apps)
            if (open.action != null) return open
        }
        val none = items.isEmpty() && text.isNotBlank()
        return Analysis(
            suggestions = items,
            listTitle = Texts.get(R.string.cmd_list_suggestions),
            needsContacts = false,
            previewTitle = Texts.get(R.string.cmd_preview_title),
            preview = Texts.get(if (none) R.string.cmd_no_match else R.string.cmd_complete_hint),
            tone = Tone.IDLE,
            action = if (none) CommandAction.WebSearch(text.trim()) else null,
            total = if (ordered.size > items.size) ordered.size else 0,
        )
    }

    /** [COMMANDS] without "ia" while no AI is set up: it only shows up once a key is saved. */
    private fun commands(extras: CommandExtras): List<Pair<String, String>> = if (extras.ai != null) COMMANDS else COMMANDS.filter { it.first != BuiltInCommand.AI.word }

    /** "?" lists everything the bar understands, without the usual cut. */
    private fun analyzeHelp(custom: List<CustomAction>, aliases: List<CommandAlias>, extras: CommandExtras): Analysis {
        val items = commands(extras).map {
            Suggestion(it.first + " ", it.second, commandKind(it.first))
        } + custom.map { Suggestion(it.letter + " ", it.usage, Texts.get(R.string.cmd_kind_action)) } +
            aliases.map { Suggestion(it.name, it.name + " → " + it.expansion, Texts.get(R.string.cmd_kind_alias)) }
        return Analysis(
            suggestions = items,
            listTitle = Texts.get(R.string.cmd_all_commands),
            needsContacts = false,
            previewTitle = Texts.get(R.string.cmd_help),
            preview = Texts.get(R.string.cmd_help_preview),
            tone = Tone.IDLE,
            action = null,
        )
    }

    /** "ATALHO" for a one-letter shortcut, "COMANDO" for a full name. */
    private fun commandKind(name: String): String = Texts.get(if (name.length == 1) R.string.cmd_kind_shortcut else R.string.cmd_kind_command)

    /** Recent commands first, then the user's own aliases and letters, so a tap replaces typing. */
    fun quickChips(
        history: List<String>,
        aliases: List<CommandAlias>,
        custom: List<CustomAction>,
        /** Commands usually run at this hour, most likely first; see [CommandStats.rank]. */
        usual: List<String> = emptyList(),
    ): List<QuickChip> {
        val recent = (usual + history).filter { it.trim() != BuiltInCommand.REPEAT }.distinct().take(MAX_CHIPS)
            .map { QuickChip(it, it, Texts.get(R.string.cmd_kind_recent)) }
        val pinned = aliases.take(MAX_CHIPS).map { QuickChip(it.name, it.name, Texts.get(R.string.cmd_kind_alias)) } +
            custom.take(MAX_CHIPS).map { QuickChip(it.letter + " ", it.letter, Texts.get(R.string.cmd_kind_action)) }
        return (recent + pinned).distinctBy { it.fill.trim() }
    }

    /** What [action] asks the system to do, as a short technical line shown under the preview. */
    fun describe(action: CommandAction): String = when (action) {
        is CommandAction.OpenApp -> "launcher · " + action.app.id.substringBefore('/')

        is CommandAction.SetAlarm -> "ACTION_SET_ALARM · %02d:%02d".format(action.hour, action.minute) +
            if (action.days.isEmpty()) "" else " · " + TimeParse.formatDays(action.days)

        is CommandAction.SetTimer -> "ACTION_SET_TIMER · ${action.seconds}s"

        is CommandAction.Schedule -> when (action.job.kind) {
            JobKind.RULE -> "on " + action.job.event?.name?.lowercase() + " → " + action.job.text
            else -> "AlarmManager · " + java.time.Instant.ofEpochMilli(action.job.at).toString().take(16)
        }

        is CommandAction.CancelJob -> "cancel #" + action.job.id

        is CommandAction.Toggle -> action.target.name.lowercase() + " = " + (action.on?.let { if (it) "on" else "off" } ?: "toggle")

        is CommandAction.Brightness -> "screen_brightness = " + (action.percent?.let { "$it%" } ?: "auto")

        is CommandAction.Volume -> "STREAM_MUSIC = ${action.percent}%"

        is CommandAction.Media -> "KEYCODE_MEDIA_" + action.key.name

        is CommandAction.Copy -> Texts.get(R.string.cmd_desc_copy)

        is CommandAction.FillLine -> Texts.get(R.string.cmd_desc_fill)

        is CommandAction.OpenSettings -> action.page.action.ifEmpty { "launcher settings" }

        is CommandAction.OpenCalendar -> "content://com.android.calendar/time"

        is CommandAction.AddNote -> Texts.get(R.string.cmd_desc_note)

        is CommandAction.ShareNotes -> "ACTION_SEND text/plain"

        is CommandAction.Ping -> "ping -c 3 " + action.host

        is CommandAction.Ai -> when (action.mode) {
            AiMode.SETUP -> "launcher settings · " + Texts.get(R.string.cmd_ai_title).lowercase()
            else -> "POST · " + action.mode.name.lowercase()
        }

        is CommandAction.Chain -> action.steps.joinToString(" ; ") { describe(it).substringBefore(" · ") }

        is CommandAction.Calc -> Texts.get(R.string.cmd_desc_calc, action.result)

        is CommandAction.Call -> "tel:" + action.contact.number.filter { it.isDigit() || it == '+' }

        is CommandAction.Route -> "geo:0,0?q=" + encode(action.query)

        is CommandAction.SplitScreen ->
            "FLAG_ACTIVITY_LAUNCH_ADJACENT · " +
                action.first.id.substringBefore('/') + " | " + action.second.id.substringBefore('/')

        is CommandAction.NewTask -> Texts.get(R.string.cmd_desc_task)

        is CommandAction.Theme -> when (action.change) {
            ThemeChange.INVERT -> "accent ⇄ background"
            ThemeChange.LIGHT -> "background = lighter"
            ThemeChange.DARK -> "background = darker"
            ThemeChange.SYSTEM -> "follow_system_color_mode = true"
            ThemeChange.RESET -> "appearance = default"
        }

        is CommandAction.ThemeApply -> ThemeCommand.describe(action.spec)

        is CommandAction.ThemeSave -> "themes += \"${action.name}\""

        is CommandAction.ThemeShare -> "ACTION_SEND " + ThemeSpecs.MIME

        is CommandAction.ThemeDelete -> "themes -= \"${action.name}\""

        is CommandAction.WebSearch -> "ACTION_WEB_SEARCH · \"${action.query}\""

        is CommandAction.OpenUrl -> "ACTION_VIEW · " + action.url

        is CommandAction.Custom -> describeCustom(action)
    }

    private fun describeCustom(action: CommandAction.Custom): String {
        val spec = action.action
        if (spec.kind == ActionKind.SHORTCUT) return Texts.get(R.string.cmd_desc_shortcut, spec.packages.firstOrNull().orEmpty())
        if (spec.template.isEmpty()) {
            return (spec.intentAction.substringAfterLast('.') + " " + spec.mimeType.orEmpty() + " · " + action.text).trim()
        }
        val values = mapOf(
            "{number}" to action.contact?.number?.filter { it.isDigit() }.orEmpty(),
            "{phone}" to encode(action.contact?.number.orEmpty()),
            "{name}" to encode(action.contact?.name.orEmpty()),
            "{text}" to encode(action.text),
        )
        var uri = spec.template
        values.forEach { (key, value) -> uri = uri.replace(key, value) }
        // Same as the real run: an empty "?text=" parameter is dropped.
        return uri.replace(Regex("[?&][^?&=]+=$"), "")
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun analyzeOpen(arg: String, apps: List<AppEntry>): Analysis {
        val q = arg.trim().foldAccents()
        val matches = matchApps(q, apps)
        val items = matches.take(MAX_SUGGESTIONS).map {
            Suggestion(BuiltInCommand.OPEN.word + " " + it.label.lowercase(), it.label, Texts.get(R.string.cmd_kind_program))
        }
        val first = matches.firstOrNull()
        return Analysis(
            suggestions = items,
            listTitle = Texts.get(R.string.cmd_list_programs),
            needsContacts = false,
            previewTitle = Texts.get(R.string.cmd_open_title),
            preview = when {
                q.isEmpty() -> Texts.get(R.string.cmd_open_empty)
                first != null -> first.label.uppercase()
                else -> Texts.get(R.string.cmd_open_not_found)
            },
            tone = when {
                q.isEmpty() -> Tone.IDLE
                first != null -> Tone.OK
                else -> Tone.ERROR
            },
            action = if (q.isNotEmpty() && first != null) CommandAction.OpenApp(first) else null,
        )
    }

    /** Apps whose name starts with [query] (accent-folded), or contains it when none starts with it. */
    private fun matchApps(query: String, apps: List<AppEntry>): List<AppEntry> = apps.filter { it.label.foldAccents().startsWith(query) }
        .ifEmpty { if (query.isEmpty()) emptyList() else apps.filter { it.label.foldAccents().contains(query) } }

    /** Separators that end the first app of "dividir chrome / maps". */
    private val splitSeparator = Regex("\\s*[/|+]\\s*")

    /**
     * Splits "chrome google maps" into the two app names. A "/", "|" or "+" is an explicit
     * separator; otherwise the first cut where both halves name an app wins, trying the longest
     * first name first. Without one, everything is the first name and the second is empty.
     */
    fun splitAppNames(arg: String, apps: List<AppEntry>): Pair<String, String> {
        val trimmed = arg.trimStart()
        splitSeparator.find(trimmed)?.let { return trimmed.substring(0, it.range.first).trim() to trimmed.substring(it.range.last + 1).trimStart() }
        // A whole name that is one app ("google maps") is not cut in two.
        if (apps.any { it.label.foldAccents() == trimmed.trim().foldAccents() }) return trimmed.trim() to ""
        val words = trimmed.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (k in words.size - 1 downTo 1) {
            val first = words.take(k).joinToString(" ")
            val second = words.drop(k).joinToString(" ")
            if (matchApps(first.foldAccents(), apps).isNotEmpty() && matchApps(second.foldAccents(), apps).isNotEmpty()) {
                return first to second
            }
        }
        return trimmed.trim() to ""
    }

    private fun analyzeSplit(arg: String, apps: List<AppEntry>): Analysis {
        val (firstName, secondName) = splitAppNames(arg, apps)
        val first = firstName.takeIf { it.isNotEmpty() }?.let { matchApps(it.foldAccents(), apps).firstOrNull() }
        val secondMatches = if (secondName.isEmpty()) emptyList() else matchApps(secondName.foldAccents(), apps).filter { it != first }
        val second = secondMatches.firstOrNull()
        val word = BuiltInCommand.SPLIT.word
        // While the first app is typed the list offers it; once it is picked, the list offers the second.
        val pickingSecond = first != null && (secondName.isNotEmpty() || splitSeparator.containsMatchIn(arg))
        val items = if (first != null && pickingSecond) {
            (if (secondName.isEmpty()) apps.filter { it != first } else secondMatches)
                .take(MAX_SUGGESTIONS)
                .map { Suggestion("$word ${first.label.lowercase()} / ${it.label.lowercase()}", it.label, Texts.get(R.string.cmd_kind_program)) }
        } else {
            matchApps(firstName.foldAccents(), apps).take(MAX_SUGGESTIONS)
                .map { Suggestion("$word ${it.label.lowercase()} / ", it.label, Texts.get(R.string.cmd_kind_program)) }
        }
        return Analysis(
            suggestions = if (firstName.isEmpty()) emptyList() else items,
            listTitle = Texts.get(R.string.cmd_list_programs),
            needsContacts = false,
            previewTitle = Texts.get(R.string.cmd_split_title),
            preview = when {
                firstName.isEmpty() -> Texts.get(R.string.cmd_split_empty)
                first == null -> Texts.get(R.string.cmd_open_not_found)
                second != null -> first.label.uppercase() + " ▏ " + second.label.uppercase()
                secondName.isEmpty() -> first.label.uppercase() + " ▏ " + Texts.get(R.string.cmd_split_second)
                else -> Texts.get(R.string.cmd_open_not_found)
            },
            tone = when {
                firstName.isEmpty() -> Tone.IDLE
                first == null -> Tone.ERROR
                second != null -> Tone.OK
                secondName.isEmpty() -> Tone.IDLE
                else -> Tone.ERROR
            },
            action = if (first != null && second != null) CommandAction.SplitScreen(first, second) else null,
        )
    }

    private fun analyzeContact(
        arg: String,
        contacts: List<ContactEntry>,
        granted: Boolean,
        command: String,
        title: String,
        build: (ContactEntry) -> CommandAction,
    ): Analysis {
        if (!granted) return needsContactsAnalysis()
        val q = arg.trim().foldAccents()
        val matches = contacts.filter { c ->
            val name = c.name.foldAccents()
            name.startsWith(q) || name.split(' ').any { it.startsWith(q) }
        }
        val first = matches.firstOrNull()
        return Analysis(
            suggestions = matches.take(MAX_SUGGESTIONS).map {
                Suggestion("$command ${it.name.lowercase()}", it.name, Texts.get(R.string.cmd_kind_contact))
            },
            listTitle = Texts.get(R.string.cmd_list_contacts),
            needsContacts = false,
            previewTitle = title,
            preview = when {
                q.isEmpty() -> Texts.get(R.string.cmd_contact_empty)
                first != null -> first.name.uppercase()
                else -> Texts.get(R.string.cmd_contact_not_found)
            },
            tone = when {
                q.isEmpty() -> Tone.IDLE
                first != null -> Tone.OK
                else -> Tone.ERROR
            },
            action = if (q.isNotEmpty() && first != null) build(first) else null,
        )
    }

    private fun contactMatches(contacts: List<ContactEntry>, query: String, wholeNamePrefix: Boolean): List<ContactEntry> {
        val q = query.trim().foldAccents()
        return contacts.filter { c ->
            val name = c.name.foldAccents()
            name.startsWith(q) || (!wholeNamePrefix && name.split(' ').any { it.startsWith(q) })
        }
    }

    /**
     * Splits "ana souza chego logo" into the contact and the message. A ":" is an explicit
     * separator; otherwise the longest run of words that names a contact is the name and the rest
     * is the message.
     */
    fun splitNameAndMessage(arg: String, contacts: List<ContactEntry>): Pair<String, String> {
        val trimmed = arg.trim()
        // A ":" of a link ("https://") is part of the message, not the separator.
        val colon = Regex(":(?!//)").find(trimmed)?.range?.first
        if (colon != null) return trimmed.substring(0, colon).trim() to trimmed.substring(colon + 1).trim()
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (k in words.size downTo 1) {
            val name = words.take(k).joinToString(" ")
            if (contactMatches(contacts, name, wholeNamePrefix = k > 1).isNotEmpty()) {
                return name to words.drop(k).joinToString(" ")
            }
        }
        return trimmed to ""
    }

    /** The spoken lead-in of a message in the app language: "dizendo que" in "dizendo que chego logo". */
    private fun messageLeadIn(): Regex {
        fun words(id: Int) = Texts.get(id).split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { Regex.escape(it) }
        val verbs = words(R.string.cmd_voice_message_lead_in).ifEmpty { return Regex("^$") }
        val that = words(R.string.cmd_voice_message_that)
        val optional = if (that.isEmpty()) "" else "(\\s+(" + that.joinToString("|") + "))?"
        return Regex("^(" + verbs.joinToString("|") + ")" + optional + "\\s+", RegexOption.IGNORE_CASE)
    }

    /** Drops the spoken lead-in: "dizendo que chego logo" becomes "chego logo". */
    fun cleanMessage(message: String): String = message.trim().replace(messageLeadIn(), "").trim()

    private fun needsContactsAnalysis() = Analysis(
        suggestions = emptyList(),
        listTitle = Texts.get(R.string.cmd_list_contacts),
        needsContacts = true,
        previewTitle = Texts.get(R.string.cmd_preview_title),
        preview = Texts.get(R.string.cmd_contacts_pending),
        tone = Tone.WARN,
        action = null,
    )

    /** [action] with the variables of its template filled, encoded as the URI wants them. */
    private fun withVariables(action: CustomAction, extras: CommandExtras): CustomAction {
        if ('{' !in action.template) return action
        var template = action.template
        Regex("\\{([^{}\\s]+)\\}").findAll(action.template).forEach { m ->
            if (m.value in PLACEHOLDERS) return@forEach
            val value = Variables.expand(m.value, extras)
            if (value.missing.isEmpty() && value.text != m.value) template = template.replace(m.value, encode(value.text))
        }
        return action.copy(template = template)
    }

    private val PLACEHOLDERS = setOf("{number}", "{phone}", "{name}", "{text}")

    private fun analyzeCustom(
        unfilled: CustomAction,
        arg: String,
        contacts: List<ContactEntry>,
        granted: Boolean,
        extras: CommandExtras = CommandExtras(),
    ): Analysis = withVariables(unfilled, extras).let { action -> analyzeCustomFilled(action, arg, contacts, granted) }

    private fun analyzeCustomFilled(
        action: CustomAction,
        arg: String,
        contacts: List<ContactEntry>,
        granted: Boolean,
    ): Analysis = when (action.arg) {
        ArgKind.CONTACT_AND_TEXT -> analyzeContactAndText(action, arg, contacts, granted)

        ArgKind.CONTACT -> analyzeContact(
            arg,
            contacts,
            granted,
            action.letter,
            Texts.get(R.string.cmd_action_to, action.label.uppercase()),
        ) { CommandAction.Custom(action, it) }

        ArgKind.TEXT -> {
            val text = arg.trim()
            Analysis(
                suggestions = emptyList(),
                listTitle = action.label.uppercase(),
                needsContacts = false,
                previewTitle = action.label.uppercase(),
                preview = if (text.isEmpty()) Texts.get(R.string.cmd_text_empty) else text.uppercase(),
                tone = if (text.isEmpty()) Tone.IDLE else Tone.OK,
                action = text.takeIf { it.isNotEmpty() }?.let { CommandAction.Custom(action, text = it) },
            )
        }

        ArgKind.NONE -> Analysis(
            suggestions = emptyList(),
            listTitle = action.label.uppercase(),
            needsContacts = false,
            previewTitle = action.label.uppercase(),
            preview = Texts.get(R.string.cmd_enter_to_run),
            tone = Tone.OK,
            action = CommandAction.Custom(action),
        )
    }

    private fun analyzeContactAndText(
        action: CustomAction,
        arg: String,
        contacts: List<ContactEntry>,
        granted: Boolean,
    ): Analysis {
        if (!granted) return needsContactsAnalysis()
        val (name, rawMessage) = splitNameAndMessage(arg, contacts)
        val message = cleanMessage(rawMessage)
        val q = name.trim()
        val matches = contactMatches(contacts, q, wholeNamePrefix = false)
        val first = matches.firstOrNull()
        val tail = if (message.isEmpty()) "" else " $message"
        return Analysis(
            suggestions = matches.take(MAX_SUGGESTIONS).map {
                Suggestion("${action.letter} ${it.name.lowercase()}$tail", it.name, Texts.get(R.string.cmd_kind_contact))
            },
            listTitle = Texts.get(R.string.cmd_list_contacts),
            needsContacts = false,
            previewTitle = Texts.get(R.string.cmd_action_to, action.label.uppercase()),
            preview = when {
                q.isEmpty() -> Texts.get(R.string.cmd_contact_empty)
                first == null -> Texts.get(R.string.cmd_contact_not_found)
                message.isEmpty() -> first.name.uppercase()
                else -> first.name.uppercase() + " · “" + message + "”"
            },
            tone = when {
                q.isEmpty() -> Tone.IDLE
                first != null -> Tone.OK
                else -> Tone.ERROR
            },
            action = if (q.isNotEmpty() && first != null) CommandAction.Custom(action, first, message) else null,
        )
    }

    /** "630", "6:30", "0630" and "06 30" all give 06:30. Null when it is not a valid time. */
    fun parseTime(arg: String): Pair<Int, Int>? {
        val d = arg.filter { it.isDigit() }
        if (d.isEmpty() || d.length > 4) return null
        val hh: Int
        val mm: Int
        if (d.length >= 3) {
            hh = d.dropLast(2).toInt()
            mm = d.takeLast(2).toInt()
        } else {
            hh = d.toInt()
            mm = 0
        }
        return if (hh in 0..23 && mm in 0..59) hh to mm else null
    }

    /** "7h30 seg-sex": the time, and the days when the words after it name days. */
    fun splitAlarm(arg: String): Pair<String, List<Int>> {
        val words = arg.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (k in 1 until words.size) {
            val days = TimeParse.days(words.drop(k).joinToString(" ")) ?: continue
            return words.take(k).joinToString(" ") to days
        }
        return arg to emptyList()
    }

    private fun analyzeAlarm(arg: String): Analysis {
        val (timeText, days) = splitAlarm(arg)
        val time = parseTime(timeText)
        val clock = time?.let { "%02d:%02d".format(it.first, it.second) }
        val label = clock?.let { if (days.isEmpty()) it else it + " · " + TimeParse.formatDays(days) }
        return Analysis(
            suggestions = clock?.takeIf { days.isEmpty() }?.let { listOf(Suggestion(BuiltInCommand.ALARM.word + " " + it, it, Texts.get(R.string.cmd_kind_time))) }.orEmpty(),
            listTitle = Texts.get(R.string.cmd_list_format),
            needsContacts = false,
            previewTitle = Texts.get(R.string.cmd_alarm_title),
            preview = label?.let { Texts.get(R.string.cmd_alarm_next, it) } ?: Texts.get(R.string.cmd_alarm_empty),
            tone = if (label != null) Tone.OK else Tone.IDLE,
            action = time?.let { CommandAction.SetAlarm(it.first, it.second, days) },
        )
    }

    private fun analyzeCalc(arg: String, rates: CurrencyRates? = null): Analysis {
        val expression = arg.trim()
        val value = if (expression.isEmpty()) null else CalcEvaluator.evaluate(expression)
        if (value == null && expression.isNotEmpty()) {
            Conversions.convert(expression, rates)?.let { converted ->
                return Analysis(
                    suggestions = emptyList(),
                    listTitle = Texts.get(R.string.cmd_list_expression),
                    needsContacts = false,
                    previewTitle = Texts.get(R.string.cmd_calc_title),
                    preview = converted.shown,
                    tone = Tone.OK,
                    action = CommandAction.Calc(expression, converted.result),
                )
            }
            if (rates == null && Conversions.needsRates(expression)) {
                return Analysis(
                    suggestions = emptyList(),
                    listTitle = Texts.get(R.string.cmd_list_expression),
                    needsContacts = false,
                    previewTitle = Texts.get(R.string.cmd_calc_title),
                    preview = Conversions.ratesPending(),
                    tone = Tone.WARN,
                    action = null,
                )
            }
        }
        val result = value?.let { CalcEvaluator.format(it) }
        return Analysis(
            suggestions = emptyList(),
            listTitle = Texts.get(R.string.cmd_list_expression),
            needsContacts = false,
            previewTitle = Texts.get(R.string.cmd_calc_title),
            preview = when {
                result != null -> "$expression = $result"
                expression.isEmpty() -> Texts.get(R.string.cmd_calc_empty)
                else -> Texts.get(R.string.cmd_calc_incomplete)
            },
            tone = if (result != null) Tone.OK else Tone.IDLE,
            action = result?.let { CommandAction.Calc(expression, it) },
        )
    }

    /** [app] is the tasks app picked before; with none, the system list asks once. */
    private fun analyzeTask(arg: String, app: String?): Analysis {
        val title = arg.trim()
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

    private fun analyzeRoute(arg: String): Analysis {
        val place = arg.trim()
        return Analysis(
            suggestions = emptyList(),
            listTitle = Texts.get(R.string.cmd_list_destination),
            needsContacts = false,
            previewTitle = Texts.get(R.string.cmd_route_title),
            preview = if (place.isEmpty()) Texts.get(R.string.cmd_route_empty) else Texts.get(R.string.cmd_route_open, place.uppercase()),
            tone = if (place.isEmpty()) Tone.IDLE else Tone.OK,
            action = place.takeIf { it.isNotEmpty() }?.let { CommandAction.Route(it) },
        )
    }

    /** The part of the selected suggestion that is not typed yet, drawn dim after the cursor. */
    fun ghost(text: String, suggestion: Suggestion?): String {
        val completion = suggestion?.completion ?: return ""
        return if (completion.length > text.length && completion.foldAccents().startsWith(text.foldAccents())) {
            completion.substring(text.length)
        } else {
            ""
        }
    }

    /** Most recent first, no repeats, at most [MAX_HISTORY]. */
    fun pushHistory(history: List<String>, command: String): List<String> {
        val clean = command.trim()
        if (clean.isEmpty()) return history
        return (listOf(clean) + history.filter { it != clean }).take(MAX_HISTORY)
    }

    /** The index the "history" key moves to: one entry older each time, stopping at the oldest. */
    fun olderIndex(current: Int, size: Int): Int = if (size == 0) -1 else minOf(size - 1, current + 1)

    /** The index the "next suggestion" key moves to, wrapping around. */
    fun nextSuggestion(current: Int, size: Int): Int = if (size <= 0) 0 else (current + 1) % size
}
