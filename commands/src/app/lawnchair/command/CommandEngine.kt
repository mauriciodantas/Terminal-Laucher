package app.lawnchair.command

import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents

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

    /** "qr ...": the code is drawn in the bar for another phone's camera. */
    data class ShowQr(val content: String) : CommandAction

    /** "perto farmácia": places of that kind around, in the maps app. */
    data class Nearby(val query: String) : CommandAction

    /** "clima recife", or "clima" at [location] ("lat,long"): shown in the bar. */
    data class Weather(val place: String, val location: String?) : CommandAction

    /** Asks the system to uninstall [app]; it confirms first. */
    data class Uninstall(val app: AppEntry) : CommandAction

    /** Opens the system settings page of [app] (permissions, notifications, defaults). */
    data class AppInfo(val app: AppEntry) : CommandAction

    /** The Play Store page of [app], or the store itself when null. */
    data class UpdateApp(val app: AppEntry?) : CommandAction

    /** Adds or takes away a [BundledPack] by its id. */
    data class InstallPack(val id: String) : CommandAction
    data class RemovePack(val id: String) : CommandAction

    /** "fast": latency, download and upload, shown in the bar as they are measured. */
    data object SpeedTest : CommandAction

    /**
     * "ia ...": talks to the AI the user set up; the bar shows the answer or fills in the command.
     * [force] is for "ia pastas --force" only: apps already in folders may move too.
     */
    data class Ai(val mode: AiMode, val prompt: String, val force: Boolean = false) : CommandAction

    /** "tema escuro; silencio on; abrir kindle": [steps] run in order. */
    data class Chain(val steps: List<CommandAction>, val lines: List<String>) : CommandAction

    data class ImportTheme(val spec: ThemeSpec, val json: String) : CommandAction
    data class ImportWidget(val json: String, val name: String) : CommandAction
    data class ImportCommands(val pack: CommandPack, val json: String) : CommandAction

    /** Saves a new or updated command or alias created via the command bar. */
    data class SaveCommand(val command: ParsedCommand, val rawLine: String, val isReplacement: Boolean = false) : CommandAction

    /** Removes a command or alias by name. */
    data class RemoveCommand(val name: String) : CommandAction
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
    /** With no [action] of its own, Enter picks the highlighted suggestion: the options several commands offer. */
    val picksOnEnter: Boolean = false,
    /** With [Need.SECRET], the provider whose secret is missing. */
    val needProvider: String? = null,
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

    /** Name, usage line, in the app language, of every built-in command; "c" is a shortcut of calc. */
    val COMMANDS: List<Pair<String, String>>
        get() = CommandRegistry.BUILT_IN.flatMap { it.usages() } + repeatUsage()

    private fun repeatUsage() = BuiltInCommand.REPEAT to BuiltInCommand.REPEAT + " " + Texts.get(R.string.cmd_usage_repeat)

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
        val ctx = CommandContext(apps, contacts, contactsGranted, custom, aliases, extras)
        val analysis = analyzeLine(typed, ctx, 0).copy(help = CommandHelp.of(typed, custom, aliases))
        return analysis.action?.let { analysis.copy(intent = describe(it, extras.providers)) } ?: analysis
    }

    /** How deep aliases that hold chains may nest before the bar stops expanding them. */
    private const val MAX_DEPTH = 4

    /**
     * A whole line: "!!" repeats the last command, an alias stands for its command, "em 30m: ..."
     * schedules what follows and ";" chains several commands.
     */
    private fun analyzeLine(typed: String, ctx: CommandContext, depth: Int): Analysis {
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
        if (depth == 0) Scheduling.schedule(text, ctx.extras) { analyzeLine(it, ctx, depth + 1) }?.let { return it }
        val parts = splitChain(text)
        if (parts.size > 1 && depth < MAX_DEPTH) return analyzeChain(text, parts, ctx, depth)
        return analyzeText(text, ctx)
    }

    /** The commands of "a; b; c", keeping an empty last one while it is being typed. */
    fun splitChain(text: String): List<String> {
        if (';' !in text) return listOf(text)
        if (JsonImport.isPotentialJson(text)) return listOf(text)
        val parts = text.split(';').map { it.trim() }
        return parts.dropLast(1).filter { it.isNotEmpty() } + parts.last()
    }

    private fun analyzeChain(text: String, parts: List<String>, ctx: CommandContext, depth: Int): Analysis {
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

    private fun analyzeText(typed: String, ctx: CommandContext): Analysis {
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

        // Text no command word starts: the commands that recognize it, one runs and several are listed.
        val registry = CommandRegistry.of(custom)
        val named = registry.named(token)
        if (named == null) {
            val applicable = registry.applicable(text, ctx)
            if (applicable.size == 1) return applicable.single().analyzeMatch(text, ctx)
            if (applicable.size > 1) return analyzeOptions(text, applicable, ctx)
        }
        if (!hasArg) {
            if (token == "?") return analyzeHelp(custom, aliases, extras)
            if (token.length > 1 && token.endsWith("?")) {
                contactInfo(text.trim().dropLast(1), contacts, contactsGranted)?.let { return it }
                if (!contactsGranted) return needsContactsAnalysis()
            }
            if (named != null && !named.requiresArgument) return named.analyze("", ctx)
            return analyzeCommandName(text, token, apps, custom, aliases, extras)
        }
        return named?.analyze(arg, ctx)
            ?: OpenCommand.apps(text.trim(), apps).takeIf { it.action != null }
            ?: unknown(text)
    }

    /** Several commands take the same text ("99999-9999": call, WhatsApp): the bar lists them and runs none. */
    private fun analyzeOptions(text: String, commands: List<Command>, ctx: CommandContext): Analysis = Analysis(
        suggestions = commands.map { it.option(text, ctx) },
        listTitle = Texts.get(R.string.cmd_list_options),
        needsContacts = false,
        previewTitle = Texts.get(R.string.cmd_options_title),
        preview = text.trim(),
        tone = Tone.OK,
        action = null,
        picksOnEnter = true,
    )

    /** The kind shown next to each option when several commands take the same text. */
    fun optionKind(): String = Texts.get(R.string.cmd_kind_option)

    /** A line the bar does not understand: Enter searches it on the web. */
    internal fun unknown(text: String): Analysis = Analysis(
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
            val open = OpenCommand.apps(text.trim(), apps)
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

    /** [COMMANDS] of the commands that can be used now: "ia" only shows up once a key is saved. */
    private fun commands(extras: CommandExtras): List<Pair<String, String>> = CommandRegistry.BUILT_IN.filter { it.isListed(extras) }.flatMap { it.usages() } + repeatUsage()

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
    fun describe(action: CommandAction, providers: List<ApiProvider> = Providers.BUILT_IN): String = when (action) {
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

        is CommandAction.ShowQr -> "QR · " + action.content.length + " chars"

        is CommandAction.Nearby -> "geo:0,0?q=" + encode(action.query)

        is CommandAction.Weather -> "GET api.open-meteo.com" + if (action.place.isEmpty()) " · {location}" else " · \"" + action.place + "\""

        is CommandAction.Uninstall -> "ACTION_DELETE · package:" + action.app.id.substringBefore('/')

        is CommandAction.AppInfo -> "ACTION_APPLICATION_DETAILS_SETTINGS · package:" + action.app.id.substringBefore('/')

        is CommandAction.UpdateApp -> action.app?.let { "market://details?id=" + it.id.substringBefore('/') } ?: "com.android.vending"

        is CommandAction.InstallPack -> "actions += " + action.id

        is CommandAction.RemovePack -> "actions -= " + action.id

        is CommandAction.SpeedTest -> "GET/POST speed.cloudflare.com · ≤ ${Speed.MAX_MB} MB"

        is CommandAction.Ai -> when (action.mode) {
            AiMode.SETUP -> "launcher settings · " + Texts.get(R.string.cmd_ai_title).lowercase()
            else -> "POST · " + action.mode.name.lowercase()
        }

        is CommandAction.Chain -> action.steps.joinToString(" ; ") { describe(it, providers).substringBefore(" · ") }

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

        is CommandAction.ImportTheme -> "ImportThemeActivity · " + action.spec.name.ifBlank { "tema" }

        is CommandAction.ImportWidget -> "ImportWidgetActivity · " + action.name

        is CommandAction.ImportCommands -> "ImportCommandsActivity · " + action.pack.actions.size + " actions, " + action.pack.aliases.size + " aliases"

        is CommandAction.SaveCommand -> "alias " + CommandLine.serialize(action.command)

        is CommandAction.RemoveCommand -> "unalias " + action.name

        is CommandAction.Custom -> describeCustom(action, providers)
    }

    private fun describeCustom(action: CommandAction.Custom, providers: List<ApiProvider>): String {
        val spec = action.action
        if (spec.kind == ActionKind.SHORTCUT) return Texts.get(R.string.cmd_desc_shortcut, spec.packages.firstOrNull().orEmpty())
        if (spec.kind == ActionKind.API) {
            val api = spec.api
            val type = if (api != null && api.writes && api.body.isNotBlank()) " · " + api.bodyType.name.lowercase() else ""
            return (api?.method ?: HttpMethods.GET) + " " + (ApiCall.url(spec, action.text, java.time.LocalDate.now(), providers) ?: spec.template) + type
        }
        if (spec.template.isEmpty()) {
            return (spec.intentAction.substringAfterLast('.') + " " + spec.mimeType.orEmpty() + " · " + (spec.extraFor(action.text) ?: action.text)).trim()
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

    internal fun analyzeContact(
        arg: String,
        contacts: List<ContactEntry>,
        granted: Boolean,
        command: String,
        title: String,
        build: (ContactEntry) -> CommandAction,
    ): Analysis {
        // A number typed in place of a name is used as it is, without the contacts permission.
        PhoneNumber.of(arg)?.let { number ->
            return Analysis(
                suggestions = emptyList(),
                listTitle = Texts.get(R.string.cmd_list_contacts),
                needsContacts = false,
                previewTitle = title,
                preview = number,
                tone = Tone.OK,
                action = build(ContactEntry(number, number)),
            )
        }
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

    /** "ana?": the number and e-mail of a contact; Enter copies the number. */
    private fun contactInfo(query: String, contacts: List<ContactEntry>, granted: Boolean): Analysis? {
        if (!granted) return null
        val q = query.trim().foldAccents()
        if (q.isEmpty()) return null
        val match = contacts.firstOrNull { c -> c.name.foldAccents().let { it.startsWith(q) || it.split(' ').any { w -> w.startsWith(q) } } }
            ?: return null
        val lines = listOf(match.name.uppercase(), match.number) + listOfNotNull(match.email.takeIf { it.isNotEmpty() })
        return commandResult(Texts.get(R.string.cmd_contact_info_title), lines.joinToString("\n"), Tone.OK, CommandAction.Copy(match.number))
    }

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
        val api = action.api
        val filled = action.copy(template = fillVariables(action.template, extras, ::encode))
        // A request body takes them too, escaped for its own type instead of for an address.
        if (api == null || '{' !in api.body) return filled
        return filled.copy(api = api.copy(body = fillVariables(api.body, extras) { ApiCall.escape(it, api.bodyType) }))
    }

    private fun fillVariables(text: String, extras: CommandExtras, escape: (String) -> String): String {
        if ('{' !in text) return text
        var result = text
        Regex("\\{([^{}\\s]+)\\}").findAll(text).forEach { m ->
            if (m.value in PLACEHOLDERS) return@forEach
            val value = Variables.expand(m.value, extras)
            if (value.missing.isEmpty() && value.text != m.value) result = result.replace(m.value, escape(value.text))
        }
        return result
    }

    private val PLACEHOLDERS = setOf("{number}", "{phone}", "{name}", "{text}")

    /** "ENTER ASKS viacep.com.br", or for a write "ENTER SENDS POST TO api.x.com", so it is not a surprise. */
    private fun apiReady(action: CustomAction, providers: List<ApiProvider>): String {
        val host = ApiCall.host(action, providers)
        val spec = action.api ?: return Texts.get(R.string.cmd_api_ready, host)
        return when {
            spec.writes -> Texts.get(R.string.cmd_api_write_ready, spec.method, host)
            else -> Texts.get(R.string.cmd_api_ready, host)
        }
    }

    internal fun analyzeCustom(
        unfilled: CustomAction,
        arg: String,
        contacts: List<ContactEntry>,
        granted: Boolean,
        extras: CommandExtras = CommandExtras(),
    ): Analysis = providerProblem(unfilled, extras)
        ?: withVariables(unfilled, extras).let { action -> analyzeCustomFilled(action, arg, contacts, granted, extras.providers) }

    /**
     * Why an API action cannot run yet: its provider is gone, or waits for a secret. The bar then
     * offers to set it up instead of running a request bound to fail.
     */
    private fun providerProblem(action: CustomAction, extras: CommandExtras): Analysis? {
        if (action.kind != ActionKind.API || action.provider.isEmpty()) return null
        val title = action.label.uppercase()
        val provider = Providers.of(action.provider, extras.providers)
            ?: return commandResult(title, Texts.get(R.string.cmd_provider_missing, action.provider), Tone.ERROR, null)
        if (!provider.auth.needsSecret || provider.id in extras.configuredProviders) return null
        return commandResult(title, Texts.get(R.string.cmd_provider_no_key, provider.name.uppercase()), Tone.WARN, null, need = Need.SECRET)
            .copy(needProvider = provider.id)
    }

    private fun analyzeCustomFilled(
        action: CustomAction,
        arg: String,
        contacts: List<ContactEntry>,
        granted: Boolean,
        providers: List<ApiProvider>,
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
            val pixKey = if (action.takesPixKey) PixKey.of(text) else null
            if (action.takesPixKey && text.isNotEmpty() && pixKey == null) {
                return commandResult(action.label.uppercase(), Texts.get(R.string.cmd_pix_invalid), Tone.ERROR, null)
            }
            Analysis(
                suggestions = emptyList(),
                listTitle = action.label.uppercase(),
                needsContacts = false,
                previewTitle = action.label.uppercase(),
                preview = when {
                    text.isEmpty() -> Texts.get(R.string.cmd_text_empty)
                    action.kind == ActionKind.API -> text.uppercase() + " · " + apiReady(action, providers)
                    pixKey != null -> pixKey.kind.label + " " + pixKey.shown
                    else -> text.uppercase()
                },
                tone = when {
                    text.isEmpty() -> Tone.IDLE
                    action.api?.writes == true -> Tone.WARN
                    else -> Tone.OK
                },
                action = text.takeIf { it.isNotEmpty() }?.let { CommandAction.Custom(action, text = it) },
            )
        }

        ArgKind.NONE -> Analysis(
            suggestions = emptyList(),
            listTitle = action.label.uppercase(),
            needsContacts = false,
            previewTitle = action.label.uppercase(),
            preview = if (action.kind == ActionKind.API) apiReady(action, providers) else Texts.get(R.string.cmd_enter_to_run),
            tone = if (action.api?.writes == true) Tone.WARN else Tone.OK,
            action = CommandAction.Custom(action),
        )
    }

    private fun analyzeContactAndText(
        action: CustomAction,
        arg: String,
        contacts: List<ContactEntry>,
        granted: Boolean,
    ): Analysis {
        // A number typed in place of a name is used as it is, without the contacts permission.
        PhoneNumber.split(arg)?.let { (number, rawMessage) ->
            val message = cleanMessage(rawMessage)
            return Analysis(
                suggestions = emptyList(),
                listTitle = Texts.get(R.string.cmd_list_contacts),
                needsContacts = false,
                previewTitle = Texts.get(R.string.cmd_action_to, action.label.uppercase()),
                preview = if (message.isEmpty()) number else "$number · “$message”",
                tone = Tone.OK,
                action = CommandAction.Custom(action, ContactEntry(number, number), message),
            )
        }
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
