package app.lawnchair.command

import androidx.annotation.StringRes
import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents
import org.json.JSONObject

/**
 * What "ia" does with the text after it: talk ("ia quanto é um pé em metros"), turn a request into a
 * command of the bar ("ia fazer silêncio até as 8"), write a new command ("ia criar busca no imdb")
 * sort the apps into drawer folders ("ia pastas", "ia pastas só jogos e trabalho") or design a
 * theme ("ia tema noite de neon com fonte de máquina de escrever").
 */
enum class AiMode(@StringRes private val wordRes: Int, val portuguese: String, val english: String) {
    CHAT(0, "", ""),
    RUN(R.string.cmd_ai_word_run, "fazer", "do"),
    CREATE(R.string.cmd_ai_word_create, "criar", "create"),
    FOLDERS(R.string.cmd_ai_word_folders, "pastas", "folders"),
    THEME(R.string.cmd_ai_word_theme, "tema", "theme"),

    /** "ia provedor claude CHAVE": sets up the service and key from the bar, no chat. */
    CONFIG(R.string.cmd_ai_word_config, "provedor", "provider"),

    /** No key saved yet: Enter opens the settings where it goes. */
    SETUP(0, "", ""),
    ;

    val word: String get() = if (wordRes == 0) "" else Texts.get(wordRes)

    fun words(): Set<String> = setOf(word, portuguese, english).filter { it.isNotEmpty() }.map { it.foldAccents() }.toSet()

    companion object {
        /** The modes chosen by a word after "ia". */
        val OFFERED get() = listOf(RUN, CREATE) + AppHost.aiModes + CONFIG

        /** The mode an accent-folded first word names; null when it is part of a question. */
        fun of(word: String): AiMode? = word.foldAccents().let { w -> OFFERED.firstOrNull { w in it.words() } }
    }
}

/**
 * What the AI may know about the bar: the commands, the user's letters and aliases by name, and the
 * apps by name. Contacts, notes, the calendar, the clipboard, the location and the history are never
 * part of it; only what the user types in the request itself goes out.
 */
data class AiContext(
    val custom: List<CustomAction> = emptyList(),
    val aliases: List<CommandAlias> = emptyList(),
    val apps: List<String> = emptyList(),
    val now: String = "",
    /** What only this app's modes need (see [CommandHost]). */
    val host: HostAiData = HostAiData(),
)

/** A command the AI proposed for a request, or why it found none. */
data class AiCommand(val command: String?, val note: String)

/** The prompts sent with each mode and the reading of what comes back. Pure, so it can be unit tested. */
object AiPrompts {

    private const val MAX_APPS = 300

    fun system(mode: AiMode, context: AiContext): String = when (mode) {
        AiMode.RUN -> base(context) + "\n\n" + catalog(context) + "\n\n" + RUN_RULES
        AiMode.CREATE -> base(context) + "\n\n" + catalog(context) + "\n\n" + CREATE_RULES + AppHost.aiCreateTail
        else -> AppHost.aiSystem(mode, context) ?: (base(context) + "\n\n" + CHAT_RULES)
    }

    fun base(context: AiContext): String = buildString {
        append("You are the assistant built into ").append(AppHost.aiIntro).append(".")
        append(" Reply in the language of the user's message.")
        if (context.now.isNotEmpty()) append(" It is now ").append(context.now).append(".")
    }

    private const val CHAT_RULES =
        "Answer briefly and directly, in plain text without Markdown: the answer is shown in a small monospaced box."

    /** Everything the bar understands, with the help text of each command, in the app language. */
    fun catalog(context: AiContext): String = buildString {
        appendLine("COMMANDS OF THE BAR (one command per line; the first word is the command):")
        CommandEngine.COMMANDS.filter { it.first != BuiltInCommand.AI.word }.forEach { (name, usage) ->
            val help = CommandHelp.of(name, emptyList(), emptyList())?.text?.replace("\n", " ")
            append("- ").append(usage)
            if (help != null) append(" — ").append(help)
            appendLine()
        }
        appendLine("- " + Texts.get(R.string.cmd_help_general).replace("\n", " "))
        if (context.custom.isNotEmpty()) {
            appendLine("USER'S OWN COMMANDS:")
            context.custom.forEach { appendLine("- " + it.usage) }
        }
        if (context.aliases.isNotEmpty()) {
            appendLine("USER'S ALIASES (a word that runs a saved command): " + context.aliases.joinToString(", ") { it.name })
        }
        if (context.apps.isNotEmpty()) {
            append("INSTALLED APPS: ").append(context.apps.take(MAX_APPS).joinToString(", "))
        }
    }.trim()

    private const val RUN_RULES =
        "Turn the user's request into ONE line the bar can run, using only the commands above. " +
            "Chain several commands with \";\" (or \"&&\" when the next one needs the previous to work) and schedule with the forms above when the request asks for it. " +
            "Keep contact names, texts and places exactly as the user wrote them; the bar finds the contact itself. " +
            "Reply with JSON only, no other text: {\"command\": \"<line>\", \"note\": \"<one short sentence of what it does>\"}. " +
            "When no command fits, reply {\"command\": null, \"note\": \"<why, in one short sentence>\"}."

    private const val CREATE_RULES =
        "Write new commands for the bar from the user's description. A command is either an alias " +
            "(a new word for a line of existing commands) or an action (a letter or short word bound to an Android intent). " +
            "Reply with JSON only, no other text, in this shape:\n" +
            "{\"actions\": [{\"letter\": \"imdb\", \"label\": \"IMDb\", \"kind\": \"INTENT\", " +
            "\"template\": \"https://www.imdb.com/find/?q={text}\", \"packages\": [\"com.imdb.mobile\"], \"arg\": \"TEXT\", " +
            "\"action\": \"android.intent.action.VIEW\", \"mime\": null, \"extra\": null, \"sms\": false, \"pattern\": \"\"}], " +
            "\"aliases\": [{\"name\": \"night\", \"expansion\": \"dnd on; vol 0\"}], \"note\": \"<one short sentence>\"}\n" +
            "Rules: \"arg\" is NONE, TEXT, CONTACT or CONTACT_AND_TEXT. Template placeholders: {text} (what is typed), " +
            "{name}, {phone} (number as stored) and {number} (digits with country code) of a contact. " +
            "\"pattern\" is optional: a regular expression that text typed alone, without the letter, must match in full " +
            "to run the action with that text as {text}. Use it only for text with a clear shape (a tracking code, a flight number), " +
            "never for ordinary words; leave it empty otherwise. " +
            "\"packages\" lists the apps that should open the link, best first; leave it empty to let the system choose. " +
            "Prefer https links that apps also open; never use file:, content: or intent: schemes. " +
            "Letters and alias names are lower case, one word, and must not be a command word above or one the user already has. " +
            "Leave out what is not needed: an empty list is fine."

    /**
     * [reply] with each SVG made fit for a JSON string: a model that writes the drawing with double
     * quotes or line breaks inside it breaks the whole JSON, so those become single quotes and spaces.
     * Only the text between `"svg": "` and the `</svg>` that closes it is touched.
     */
    fun repairSvg(reply: String): String {
        val out = StringBuilder()
        var at = 0
        val open = Regex("\"svg\"\\s*:\\s*\"")
        while (true) {
            val start = open.find(reply, at) ?: break
            val body = start.range.last + 1
            val close = reply.indexOf("</svg>", body, ignoreCase = true)
            if (close < 0) break
            val end = close + "</svg>".length
            out.append(reply, at, body)
            reply.substring(body, end).forEachIndexed { i, c ->
                val escaped = i > 0 && reply[body + i - 1] == '\\'
                when {
                    c == '"' && !escaped -> out.append('\'')
                    c == '\n' || c == '\r' || c == '\t' -> out.append(' ')
                    else -> out.append(c)
                }
            }
            at = end
        }
        return out.append(reply, at, reply.length).toString()
    }

    /** The JSON object in a reply, without the code fences or words some models wrap it in. */
    fun json(reply: String): JSONObject? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(reply.substring(start, end + 1)) }.getOrNull()
    }

    fun command(reply: String): AiCommand {
        val o = json(reply) ?: return AiCommand(null, reply.trim())
        val command = if (o.isNull("command")) null else o.optString("command").trim().takeIf { it.isNotEmpty() }
        return AiCommand(command?.lineSequence()?.first()?.trim(), o.optString("note").trim())
    }

    /**
     * The commands of a [AiMode.CREATE] reply as a pack file, so it goes through the same import
     * screen and the same checks as a pack received from somebody. Null when the reply holds none.
     */
    fun pack(reply: String): Pair<String, String>? {
        val o = json(reply) ?: return null
        val note = o.optString("note").trim()
        o.remove("note")
        o.put("format", CommandPacks.FORMAT).put("version", CommandPacks.VERSION)
        val text = o.toString()
        return runCatching { CommandPacks.decode(text) }.getOrNull()?.takeUnless { it.isEmpty }?.let { text to note }
    }
}
