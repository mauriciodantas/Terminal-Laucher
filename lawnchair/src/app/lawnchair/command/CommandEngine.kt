package app.lawnchair.command

import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents
import com.android.launcher3.R

data class AppEntry(val label: String, val id: String)

data class ContactEntry(val name: String, val number: String)

/** How the preview box is colored: neutral, a command that can run, or a problem. */
enum class Tone { IDLE, OK, WARN, ERROR }

data class Suggestion(val completion: String, val label: String, val kind: String)

/** What running the command does. */
sealed interface CommandAction {
    data class OpenApp(val app: AppEntry) : CommandAction
    data class SetAlarm(val hour: Int, val minute: Int) : CommandAction
    data class Calc(val expression: String, val result: String) : CommandAction
    data class Call(val contact: ContactEntry) : CommandAction

    /** A user-defined command: [action] filled with the contact and/or text that was typed. */
    data class Custom(
        val action: CustomAction,
        val contact: ContactEntry? = null,
        val text: String = "",
    ) : CommandAction
    data class Route(val query: String) : CommandAction
    data class NewTask(val title: String) : CommandAction
    data class WebSearch(val query: String) : CommandAction
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
            )

    private val splitter = Regex("^(\\S*)(\\s+(.*))?$", RegexOption.DOT_MATCHES_ALL)

    fun analyze(
        typed: String,
        apps: List<AppEntry>,
        contacts: List<ContactEntry>,
        contactsGranted: Boolean,
        custom: List<CustomAction> = CustomActions.DEFAULTS,
        aliases: List<CommandAlias> = emptyList(),
    ): Analysis {
        val analysis = analyzeText(typed, apps, contacts, contactsGranted, custom, aliases)
        return analysis.action?.let { analysis.copy(intent = describe(it)) } ?: analysis
    }

    private fun analyzeText(
        typed: String,
        apps: List<AppEntry>,
        contacts: List<ContactEntry>,
        contactsGranted: Boolean,
        custom: List<CustomAction>,
        aliases: List<CommandAlias>,
    ): Analysis {
        // An alias typed in full stands for its command; a partial one is only suggested.
        val text = Aliases.resolve(typed, aliases).trimStart()
        val m = splitter.find(text)
        val token = m?.groupValues?.get(1).orEmpty().foldAccents()
        val hasArg = m != null && m.groups[2] != null
        val arg = if (hasArg) m?.groups?.get(3)?.value.orEmpty() else ""

        if (!hasArg) {
            if (token == "?") return analyzeHelp(custom, aliases)
            val bare = custom.firstOrNull { it.letter == token && it.arg == ArgKind.NONE }
            if (bare != null) return analyzeCustom(bare, "", contacts, contactsGranted)
            return analyzeCommandName(text, token, apps, custom, aliases)
        }

        if (token == BuiltInCommand.TASK) return analyzeTask(arg)
        return when (BuiltInCommand.of(token)) {
            BuiltInCommand.OPEN -> analyzeOpen(arg, apps)

            BuiltInCommand.CALL -> analyzeContact(arg, contacts, contactsGranted, BuiltInCommand.CALL.word, Texts.get(R.string.cmd_call_title)) {
                CommandAction.Call(it)
            }

            BuiltInCommand.ALARM -> analyzeAlarm(arg)

            BuiltInCommand.CALC -> analyzeCalc(arg)

            BuiltInCommand.ROUTE -> analyzeRoute(arg)

            null -> custom.firstOrNull { it.letter == token }
                ?.let { analyzeCustom(it, arg, contacts, contactsGranted) }
                ?: analyzeOpen(text.trim(), apps).takeIf { it.action != null }
                ?: Analysis(
                    suggestions = emptyList(),
                    listTitle = Texts.get(R.string.cmd_list_suggestions),
                    needsContacts = false,
                    previewTitle = Texts.get(R.string.cmd_unknown_title),
                    preview = Texts.get(R.string.cmd_unknown_preview),
                    tone = Tone.ERROR,
                    action = text.trim().takeIf { it.isNotEmpty() }?.let { CommandAction.WebSearch(it) },
                )
        }
    }

    private fun analyzeCommandName(
        text: String,
        token: String,
        apps: List<AppEntry>,
        custom: List<CustomAction>,
        aliases: List<CommandAlias>,
    ): Analysis {
        val aliasItems = Aliases.matching(token, aliases).map { Suggestion(it.name, it.expansion, Texts.get(R.string.cmd_kind_alias)) }
        val all = COMMANDS + custom.map { it.letter to it.usage }
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
        val ordered = if (token.isEmpty()) commands + aliasItems + matchingApps else aliasItems + commands + matchingApps
        val limit = if (token.isEmpty()) EMPTY_SUGGESTIONS else MAX_SUGGESTIONS
        val items = ordered.take(limit)
        // Nothing is a command or alias: a bare word is taken as "abrir <word>" (open).
        if (commands.isEmpty() && aliasItems.isEmpty() && token.isNotEmpty()) {
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

    /** "?" lists everything the bar understands, without the usual cut. */
    private fun analyzeHelp(custom: List<CustomAction>, aliases: List<CommandAlias>): Analysis {
        val items = COMMANDS.map {
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
    fun quickChips(history: List<String>, aliases: List<CommandAlias>, custom: List<CustomAction>): List<QuickChip> {
        val recent = history.take(MAX_CHIPS).map { QuickChip(it, it, Texts.get(R.string.cmd_kind_recent)) }
        val pinned = aliases.take(MAX_CHIPS).map { QuickChip(it.name, it.name, Texts.get(R.string.cmd_kind_alias)) } +
            custom.take(MAX_CHIPS).map { QuickChip(it.letter + " ", it.letter, Texts.get(R.string.cmd_kind_action)) }
        return (recent + pinned).distinctBy { it.fill.trim() }
    }

    /** What [action] asks the system to do, as a short technical line shown under the preview. */
    fun describe(action: CommandAction): String = when (action) {
        is CommandAction.OpenApp -> "launcher · " + action.app.id.substringBefore('/')
        is CommandAction.SetAlarm -> "ACTION_SET_ALARM · %02d:%02d".format(action.hour, action.minute)
        is CommandAction.Calc -> Texts.get(R.string.cmd_desc_calc, action.result)
        is CommandAction.Call -> "tel:" + action.contact.number.filter { it.isDigit() || it == '+' }
        is CommandAction.Route -> "geo:0,0?q=" + encode(action.query)
        is CommandAction.NewTask -> Texts.get(R.string.cmd_desc_task)
        is CommandAction.WebSearch -> "ACTION_WEB_SEARCH · \"${action.query}\""
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
        val matches = apps.filter { it.label.foldAccents().startsWith(q) }
            .ifEmpty { if (q.isEmpty()) emptyList() else apps.filter { it.label.foldAccents().contains(q) } }
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
        if (':' in trimmed) return trimmed.substringBefore(':').trim() to trimmed.substringAfter(':').trim()
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

    private fun analyzeCustom(
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

    private fun analyzeAlarm(arg: String): Analysis {
        val time = parseTime(arg)
        val label = time?.let { "%02d:%02d".format(it.first, it.second) }
        return Analysis(
            suggestions = label?.let { listOf(Suggestion(BuiltInCommand.ALARM.word + " " + it, it, Texts.get(R.string.cmd_kind_time))) }.orEmpty(),
            listTitle = Texts.get(R.string.cmd_list_format),
            needsContacts = false,
            previewTitle = Texts.get(R.string.cmd_alarm_title),
            preview = label?.let { Texts.get(R.string.cmd_alarm_next, it) } ?: Texts.get(R.string.cmd_alarm_empty),
            tone = if (label != null) Tone.OK else Tone.IDLE,
            action = time?.let { CommandAction.SetAlarm(it.first, it.second) },
        )
    }

    private fun analyzeCalc(arg: String): Analysis {
        val expression = arg.trim()
        val value = if (expression.isEmpty()) null else CalcEvaluator.evaluate(expression)
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

    private fun analyzeTask(arg: String): Analysis {
        val title = arg.trim()
        return Analysis(
            suggestions = emptyList(),
            listTitle = Texts.get(R.string.cmd_task_list),
            needsContacts = false,
            previewTitle = Texts.get(R.string.cmd_task_title),
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
