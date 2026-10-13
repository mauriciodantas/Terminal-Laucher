package app.lawnchair.command

import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents

/** "abrir chrome": opens an app. A word that names no command but an app is "abrir" too. */
object OpenCommand : BuiltIn(BuiltInCommand.OPEN, R.string.cmd_usage_open, R.string.cmd_help_open) {

    override val requiresArgument = true

    override fun analyze(arg: String, ctx: CommandContext): Analysis = apps(arg, ctx.apps)

    fun apps(arg: String, apps: List<AppEntry>): Analysis {
        val q = arg.trim().foldAccents()
        val matches = matchApps(q, apps)
        val items = matches.take(CommandEngine.MAX_SUGGESTIONS).map {
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
    internal fun matchApps(query: String, apps: List<AppEntry>): List<AppEntry> = apps.filter { it.label.foldAccents().startsWith(query) }
        .ifEmpty { if (query.isEmpty()) emptyList() else apps.filter { it.label.foldAccents().contains(query) } }
}

/** "dividir chrome / maps": two apps side by side in split screen. */
object SplitCommand : BuiltIn(BuiltInCommand.SPLIT, R.string.cmd_usage_split, R.string.cmd_help_split) {

    override val requiresArgument = true

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
            if (OpenCommand.matchApps(first.foldAccents(), apps).isNotEmpty() && OpenCommand.matchApps(second.foldAccents(), apps).isNotEmpty()) {
                return first to second
            }
        }
        return trimmed.trim() to ""
    }

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val apps = ctx.apps
        val (firstName, secondName) = splitAppNames(arg, apps)
        val first = firstName.takeIf { it.isNotEmpty() }?.let { OpenCommand.matchApps(it.foldAccents(), apps).firstOrNull() }
        val secondMatches = if (secondName.isEmpty()) emptyList() else OpenCommand.matchApps(secondName.foldAccents(), apps).filter { it != first }
        val second = secondMatches.firstOrNull()
        val word = BuiltInCommand.SPLIT.word
        // While the first app is typed the list offers it; once it is picked, the list offers the second.
        val pickingSecond = first != null && (secondName.isNotEmpty() || splitSeparator.containsMatchIn(arg))
        val items = if (first != null && pickingSecond) {
            (if (secondName.isEmpty()) apps.filter { it != first } else secondMatches)
                .take(CommandEngine.MAX_SUGGESTIONS)
                .map { Suggestion("$word ${first.label.lowercase()} / ${it.label.lowercase()}", it.label, Texts.get(R.string.cmd_kind_program)) }
        } else {
            OpenCommand.matchApps(firstName.foldAccents(), apps).take(CommandEngine.MAX_SUGGESTIONS)
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
}

/** "rota casa": directions to a place in the maps app. */
object RouteCommand : BuiltIn(BuiltInCommand.ROUTE, R.string.cmd_usage_route, R.string.cmd_help_route) {

    override val requiresArgument = true

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
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
}
