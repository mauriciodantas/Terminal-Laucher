package app.lawnchair.command

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
)

/** Pure rules of the command bar, free of Android types so they can be unit tested. */
object CommandEngine {

    const val MAX_SUGGESTIONS = 4
    const val MAX_HISTORY = 8

    /** Name, usage line. "c" is a shortcut of "calc". */
    val COMMANDS = listOf(
        "abrir" to "abrir [app]",
        "alarme" to "alarme HH:MM",
        "calc" to "calc [expressão]",
        "ligar" to "ligar [contato]",
        "rota" to "rota [lugar]",
        "t" to "t [tarefa] · nova tarefa",
        "c" to "c [conta] · atalho de calc",
    )

    private val splitter = Regex("^(\\S*)(\\s+(.*))?$", RegexOption.DOT_MATCHES_ALL)

    fun analyze(
        typed: String,
        apps: List<AppEntry>,
        contacts: List<ContactEntry>,
        contactsGranted: Boolean,
        custom: List<CustomAction> = CustomActions.DEFAULTS,
    ): Analysis {
        val text = typed.trimStart()
        val m = splitter.find(text)
        val token = m?.groupValues?.get(1).orEmpty().lowercase()
        val hasArg = m != null && m.groups[2] != null
        val arg = if (hasArg) m?.groups?.get(3)?.value.orEmpty() else ""

        if (!hasArg) {
            val bare = custom.firstOrNull { it.letter == token && it.arg == ArgKind.NONE }
            if (bare != null) return analyzeCustom(bare, "", contacts, contactsGranted)
            return analyzeCommandName(text, token, apps, custom)
        }

        return when (if (token == "c") "calc" else token) {
            "abrir" -> analyzeOpen(arg, apps)
            "ligar" -> analyzeContact(arg, contacts, contactsGranted, "ligar", "LIGAR PARA") {
                CommandAction.Call(it)
            }
            "alarme" -> analyzeAlarm(arg)
            "calc" -> analyzeCalc(arg)
            "t" -> analyzeTask(arg)
            "rota" -> analyzeRoute(arg)
            else -> custom.firstOrNull { it.letter == token }
                ?.let { analyzeCustom(it, arg, contacts, contactsGranted) }
                ?: Analysis(
                    suggestions = emptyList(),
                    listTitle = "SUGESTÕES",
                    needsContacts = false,
                    previewTitle = "COMANDO DESCONHECIDO",
                    preview = "ENTER PARA PESQUISAR NA REDE",
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
    ): Analysis {
        val all = COMMANDS + custom.map { it.letter to it.usage }
        val commands = all.filter { it.first.startsWith(token) }.map {
            Suggestion(it.first + " ", it.second, if (it.first.length == 1) "ATALHO" else "COMANDO")
        }
        val matchingApps = if (token.isEmpty()) {
            emptyList()
        } else {
            apps.filter { it.label.lowercase().startsWith(token) }.map {
                Suggestion("abrir " + it.label.lowercase(), it.label, "PROGRAMA")
            }
        }
        val items = (commands + matchingApps).take(MAX_SUGGESTIONS)
        val none = items.isEmpty() && text.isNotBlank()
        return Analysis(
            suggestions = items,
            listTitle = "SUGESTÕES",
            needsContacts = false,
            previewTitle = "PRÉ-VISUALIZAÇÃO",
            preview = if (none) "SEM CORRESPONDÊNCIA · ENTER PESQUISA NA REDE" else "COMPLETE PARA VER O RESULTADO",
            tone = Tone.IDLE,
            action = if (none) CommandAction.WebSearch(text.trim()) else null,
        )
    }

    private fun analyzeOpen(arg: String, apps: List<AppEntry>): Analysis {
        val q = arg.trim().lowercase()
        val matches = apps.filter { it.label.lowercase().startsWith(q) }
            .ifEmpty { if (q.isEmpty()) emptyList() else apps.filter { it.label.lowercase().contains(q) } }
        val items = matches.take(MAX_SUGGESTIONS).map {
            Suggestion("abrir " + it.label.lowercase(), it.label, "PROGRAMA")
        }
        val first = matches.firstOrNull()
        return Analysis(
            suggestions = items,
            listTitle = "PROGRAMAS",
            needsContacts = false,
            previewTitle = "ABRIR",
            preview = when {
                q.isEmpty() -> "INFORME O PROGRAMA"
                first != null -> first.label.uppercase()
                else -> "PROGRAMA NÃO ENCONTRADO"
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
        if (!granted) {
            return Analysis(
                suggestions = emptyList(),
                listTitle = "CONTATOS",
                needsContacts = true,
                previewTitle = "PRÉ-VISUALIZAÇÃO",
                preview = "ACESSO A CONTATOS PENDENTE",
                tone = Tone.WARN,
                action = null,
            )
        }
        val q = arg.trim().lowercase()
        val matches = contacts.filter { c ->
            val name = c.name.lowercase()
            name.startsWith(q) || name.split(' ').any { it.startsWith(q) }
        }
        val first = matches.firstOrNull()
        return Analysis(
            suggestions = matches.take(MAX_SUGGESTIONS).map {
                Suggestion("$command ${it.name.lowercase()}", it.name, "CONTATO")
            },
            listTitle = "CONTATOS",
            needsContacts = false,
            previewTitle = title,
            preview = when {
                q.isEmpty() -> "INFORME O CONTATO"
                first != null -> first.name.uppercase()
                else -> "CONTATO NÃO ENCONTRADO"
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
        val q = query.trim().lowercase()
        return contacts.filter { c ->
            val name = c.name.lowercase()
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

    private val messageLeadIn = Regex("^(dizendo|falando|avisando|escrevendo|perguntando)(\\s+que)?\\s+", RegexOption.IGNORE_CASE)

    /** Drops the spoken lead-in: "dizendo que chego logo" becomes "chego logo". */
    fun cleanMessage(message: String): String = message.trim().replace(messageLeadIn, "").trim()

    private fun needsContactsAnalysis() = Analysis(
        suggestions = emptyList(),
        listTitle = "CONTATOS",
        needsContacts = true,
        previewTitle = "PRÉ-VISUALIZAÇÃO",
        preview = "ACESSO A CONTATOS PENDENTE",
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
            arg, contacts, granted, action.letter, action.label.uppercase() + " PARA",
        ) { CommandAction.Custom(action, it) }
        ArgKind.TEXT -> {
            val text = arg.trim()
            Analysis(
                suggestions = emptyList(),
                listTitle = action.label.uppercase(),
                needsContacts = false,
                previewTitle = action.label.uppercase(),
                preview = if (text.isEmpty()) "DIGITE O TEXTO" else text.uppercase(),
                tone = if (text.isEmpty()) Tone.IDLE else Tone.OK,
                action = text.takeIf { it.isNotEmpty() }?.let { CommandAction.Custom(action, text = it) },
            )
        }
        ArgKind.NONE -> Analysis(
            suggestions = emptyList(),
            listTitle = action.label.uppercase(),
            needsContacts = false,
            previewTitle = action.label.uppercase(),
            preview = "ENTER PARA EXECUTAR",
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
                Suggestion("${action.letter} ${it.name.lowercase()}$tail", it.name, "CONTATO")
            },
            listTitle = "CONTATOS",
            needsContacts = false,
            previewTitle = action.label.uppercase() + " PARA",
            preview = when {
                q.isEmpty() -> "INFORME O CONTATO"
                first == null -> "CONTATO NÃO ENCONTRADO"
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
            suggestions = label?.let { listOf(Suggestion("alarme $it", it, "HORÁRIO")) }.orEmpty(),
            listTitle = "FORMATO",
            needsContacts = false,
            previewTitle = "DEFINIR ALARME",
            preview = label?.let { "$it · PRÓXIMA OCORRÊNCIA" } ?: "INFORME O HORÁRIO (EX.: 0630)",
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
            listTitle = "EXPRESSÃO",
            needsContacts = false,
            previewTitle = "RESULTADO",
            preview = when {
                result != null -> "$expression = $result"
                expression.isEmpty() -> "DIGITE UMA CONTA (EX.: 12*8)"
                else -> "EXPRESSÃO INCOMPLETA"
            },
            tone = if (result != null) Tone.OK else Tone.IDLE,
            action = result?.let { CommandAction.Calc(expression, it) },
        )
    }

    private fun analyzeTask(arg: String): Analysis {
        val title = arg.trim()
        return Analysis(
            suggestions = emptyList(),
            listTitle = "NOVA TAREFA",
            needsContacts = false,
            previewTitle = "ENVIAR PARA SEU APP DE TAREFAS",
            preview = if (title.isEmpty()) "DIGITE O TÍTULO DA TAREFA" else title.uppercase(),
            tone = if (title.isEmpty()) Tone.IDLE else Tone.OK,
            action = title.takeIf { it.isNotEmpty() }?.let { CommandAction.NewTask(it) },
        )
    }

    private fun analyzeRoute(arg: String): Analysis {
        val place = arg.trim()
        return Analysis(
            suggestions = emptyList(),
            listTitle = "DESTINO",
            needsContacts = false,
            previewTitle = "TRAÇAR ROTA",
            preview = if (place.isEmpty()) "INFORME O DESTINO" else place.uppercase() + " · ABRIR NO MAPA",
            tone = if (place.isEmpty()) Tone.IDLE else Tone.OK,
            action = place.takeIf { it.isNotEmpty() }?.let { CommandAction.Route(it) },
        )
    }

    /** The part of the selected suggestion that is not typed yet, drawn dim after the cursor. */
    fun ghost(text: String, suggestion: Suggestion?): String {
        val completion = suggestion?.completion ?: return ""
        return if (completion.length > text.length && completion.lowercase().startsWith(text.lowercase())) {
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
