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

    /** No key saved yet: Enter opens the settings where it goes. */
    SETUP(0, "", ""),
    ;

    val word: String get() = if (wordRes == 0) "" else Texts.get(wordRes)

    fun words(): Set<String> = setOf(word, portuguese, english).filter { it.isNotEmpty() }.map { it.foldAccents() }.toSet()

    companion object {
        /** The modes chosen by a word after "ia". */
        val OFFERED get() = listOf(RUN, CREATE, FOLDERS, THEME)

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
    /** The drawer folders by name with the names of their apps, for [AiMode.FOLDERS]. */
    val folders: List<Pair<String, List<String>>> = emptyList(),
    /** "ia pastas --force": apps already in folders may be moved too. */
    val forceFolders: Boolean = false,
    /** The theme on screen now, for [AiMode.THEME] to start from. */
    val theme: ThemeSpec? = null,
)

/** A command the AI proposed for a request, or why it found none. */
data class AiCommand(val command: String?, val note: String)

/** The prompts sent with each mode and the reading of what comes back. Pure, so it can be unit tested. */
object AiPrompts {

    private const val MAX_APPS = 300

    fun system(mode: AiMode, context: AiContext): String = when (mode) {
        AiMode.RUN -> base(context) + "\n\n" + catalog(context) + "\n\n" + RUN_RULES
        AiMode.CREATE -> base(context) + "\n\n" + catalog(context) + "\n\n" + CREATE_RULES
        AiMode.FOLDERS -> base(context) + "\n\n" + drawer(context) + "\n\n" + if (context.forceFolders) FOLDERS_FORCE_RULES else FOLDERS_RULES
        AiMode.THEME -> base(context) + "\n\n" + currentTheme(context) + THEME_RULES
        else -> base(context) + "\n\n" + CHAT_RULES
    }

    private fun base(context: AiContext): String = buildString {
        append("You are the assistant built into the command bar of Terminal, an Android launcher.")
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

    private const val MAX_FOLDER_APPS = 600

    /** The apps still outside a folder (all of them with force), and the folders the user already has. */
    fun drawer(context: AiContext): String = buildString {
        val inFolders = context.folders.flatMap { it.second }.toSet()
        if (context.forceFolders) {
            append("ALL APPS: ")
            appendLine(context.apps.distinct().take(MAX_FOLDER_APPS).joinToString(", "))
        } else {
            append("APPS WITHOUT A FOLDER: ")
            appendLine(context.apps.filter { it !in inFolders }.distinct().take(MAX_FOLDER_APPS).joinToString(", "))
        }
        if (context.folders.isNotEmpty()) {
            appendLine(
                if (context.forceFolders) {
                    "EXISTING FOLDERS (you may reorganize them):"
                } else {
                    "EXISTING FOLDERS (keep them; their apps stay where they are):"
                },
            )
            context.folders.forEach { (name, apps) -> appendLine("- " + name + ": " + apps.joinToString(", ")) }
        }
    }.trim()

    private const val FOLDERS_RULES =
        "Sort the apps without a folder into app drawer folders that make sense to this user, by what the apps are for " +
            "(for example social, work, photos, money, travel, games, tools). Follow the user's request when it says how to group them. " +
            "Use app names exactly as listed, each app once. A new folder holds at least 2 apps; aim for 4 to 12 folders, " +
            "and leave out an app that fits no folder rather than forcing it in. To add apps to an existing folder, use its exact name. " +
            "Folder names are short (one or two words) and in the language of the user's request. " +
            "Reply with JSON only, no other text: " +
            "{\"folders\": [{\"name\": \"<folder>\", \"apps\": [\"<app>\", \"<app>\"]}], \"note\": \"<one short sentence>\"}"

    private const val FOLDERS_FORCE_RULES =
        "Reorganize ALL the apps into app drawer folders that make sense to this user, by what the apps are for " +
            "(for example social, work, photos, money, travel, games, tools), moving apps between the existing folders where it helps. " +
            "Follow the user's request when it says how to group them. Use app names exactly as listed, each app once. " +
            "List the whole content of every folder you keep or create: an existing folder you name gets exactly the apps you list, " +
            "so keep its name to keep it. An existing folder you leave out keeps only the apps you did not move, and is deleted once empty. " +
            "A new folder holds at least 2 apps; aim for 4 to 12 folders, and leave out an app that fits no folder rather than forcing it in. " +
            "Folder names are short (one or two words) and in the language of the user's request. " +
            "Reply with JSON only, no other text: " +
            "{\"folders\": [{\"name\": \"<folder>\", \"apps\": [\"<app>\", \"<app>\"]}], \"note\": \"<one short sentence>\"}"

    private fun currentTheme(context: AiContext): String = context.theme?.let { "CURRENT THEME (start from it when the request asks for changes to it): " + ThemeSpecs.encode(it.copy(name = ""), pretty = false) + "\n\n" }.orEmpty()

    /** The fields of a theme and how to fill them, for [AiMode.THEME] and for a theme asked of [AiMode.CREATE]. */
    private const val THEME_SHAPE =
        "{\"name\": \"<short name, 1 to 3 words, in the user's language>\", \"accent\": \"#rrggbb\", \"background\": \"#rrggbb\", \"secondary\": \"#rrggbb\", " +
            "\"outline\": \"#rrggbb\", \"dock\": \"#rrggbb\", \"folder\": \"#rrggbb\", \"followSystem\": false, " +
            "\"font\": {\"kind\": \"google\", \"family\": \"<Google Fonts family>\"}, \"pixelClock\": true, \"iconEffect\": true, " +
            "\"widgetEffect\": true, \"widgetIntensity\": 0.6, \"crtEffect\": true, \"crtIntensity\": 0.4, " +
            "\"showWallpaper\": false, \"wallpaperDim\": 0.5, \"colorAsWallpaper\": false, " +
            "\"wallpaper\": {\"svg\": \"<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1080 2400'>...</svg>\", \"lockScreen\": false}, " +
            "\"lockWallpaper\": {\"svg\": \"<svg ...>...</svg>\"}, \"note\": \"<one short sentence>\"}\n" +
            "Rules: the accent over the background must read well, a contrast ratio of at least 4.5 (WCAG). " +
            "secondary is a third color for secondary text, hints and lines (default keeps the accent dimmed); " +
            "it must read over the background too, at least 3.0, and works best a third of the color wheel away from the accent. " +
            "outline is the search bar border, dock the dock background, folder the folder background; " +
            "any color may be \"default\" to follow the accent. The font is an exact Google Fonts family name " +
            "(monospaced ones such as JetBrains Mono, Space Mono, IBM Plex Mono, VT323 or Share Tech Mono suit the look best), " +
            "or {\"kind\": \"default\"} for the terminal font. pixelClock keeps the pixel font for the clock. " +
            "iconEffect draws app icons in the accent color; widgetEffect tints widgets; crtEffect adds scanlines; " +
            "intensities go from 0.05 to 1. showWallpaper shows the system wallpaper behind the home screen, dimmed by wallpaperDim (0 to 1); " +
            "colorAsWallpaper sets the background color as the system wallpaper. Set followSystem only when asked to follow the light and dark mode. " +
            "wallpaper and lockWallpaper: include them ONLY when the user explicitly asks for a wallpaper or a picture " +
            "(wallpaper, papel de parede, fondo de pantalla, imagem de fundo, lock screen...); otherwise leave both out. " +
            "You draw the picture yourself as ONE self-contained SVG in the same reply: xmlns set, viewBox='0 0 1080 2400' (a portrait phone screen), " +
            "only shapes, paths, gradients, patterns and filters; no <image>, links, external fonts, <text>, scripts, CSS @import or <foreignObject>; " +
            "at most 20000 characters. Make it a real illustration of what was asked (a scene or motifs with depth, not a flat color), " +
            "built on the theme background as its dominant color and calm behind the top third and the middle, where the clock, icons and text are drawn over it. " +
            "Write the attributes with single quotes so the SVG fits in the JSON string as it is. " +
            "lockScreen true puts the same picture on the lock screen; lockWallpaper is a different picture just for it. " +
            "Use them only when the user asks for the lock screen or for more than one wallpaper. " +
            "With a wallpaper set showWallpaper true and wallpaperDim about 0.3. Never write image links or base64: only SVG. " +
            "The launcher looks like a terminal: the accent color draws all the text, lines and icons over the background color."

    private const val THEME_RULES =
        "Design a theme for the launcher from the user's description. " +
            "Reply with JSON only, no other text, in this shape (leave out a field to keep it as it is):\n" + THEME_SHAPE

    /**
     * The theme of a reply and its note, or null when the reply holds none or it could not be read.
     * [AiMode.THEME] replies with the theme itself; [AiMode.CREATE] wraps it in "theme" when it was asked for one.
     */
    fun theme(reply: String): Pair<ThemeSpec, String>? {
        val o = json(reply) ?: json(repairSvg(reply)) ?: return null
        val t = o.optJSONObject("theme") ?: o
        // A link or base64 the AI writes would be made up: only the SVG it draws stays, which the phone turns into
        // a PNG embedded in the theme. A wallpaper it adds is shown even if it left showWallpaper off.
        val read = ThemeSpecs.fromJson(t)
        val wallpaper = read.wallpaper?.takeIf { it.image is ThemeImage.Svg }
        val spec = read.copy(
            wallpaper = wallpaper,
            lockWallpaper = read.lockWallpaper as? ThemeImage.Svg,
            showWallpaper = if (wallpaper != null) true else read.showWallpaper,
        )
        if (spec.isEmpty || ThemeSpecs.unreadable(spec, null)) return null
        return spec to (o.optString("note").ifBlank { t.optString("note") }).trim()
    }

    /** The theme an [AiMode.CREATE] reply holds instead of commands ("ia criar um tema de halloween"), or null. */
    fun createdTheme(reply: String): Pair<ThemeSpec, String>? = if (json(reply)?.optJSONObject("theme") != null) theme(reply) else null

    private const val RUN_RULES =
        "Turn the user's request into ONE line the bar can run, using only the commands above. " +
            "Chain several commands with \";\" and schedule with the forms above when the request asks for it. " +
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
            "\"aliases\": [{\"name\": \"night\", \"expansion\": \"theme dark; dnd on\"}], \"note\": \"<one short sentence>\"}\n" +
            "Rules: \"arg\" is NONE, TEXT, CONTACT or CONTACT_AND_TEXT. Template placeholders: {text} (what is typed), " +
            "{name}, {phone} (number as stored) and {number} (digits with country code) of a contact. " +
            "\"pattern\" is optional: a regular expression that text typed alone, without the letter, must match in full " +
            "to run the action with that text as {text}. Use it only for text with a clear shape (a tracking code, a flight number), " +
            "never for ordinary words; leave it empty otherwise. " +
            "\"packages\" lists the apps that should open the link, best first; leave it empty to let the system choose. " +
            "Prefer https links that apps also open; never use file:, content: or intent: schemes. " +
            "Letters and alias names are lower case, one word, and must not be a command word above or one the user already has. " +
            "Leave out what is not needed: an empty list is fine.\n" +
            "When the user asks for a theme, a look, colors, a font or effects for the launcher instead of a command, " +
            "write no actions or aliases: reply with JSON only, {\"theme\": <theme>, \"note\": \"<one short sentence>\"}, " +
            "where <theme> has this shape:\n" + THEME_SHAPE

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
