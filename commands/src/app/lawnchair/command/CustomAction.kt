package app.lawnchair.command

import app.lawnchair.util.Texts

/** What the user types after the letter of a [CustomAction]. */
enum class ArgKind { NONE, TEXT, CONTACT, CONTACT_AND_TEXT }

/**
 * How a [CustomAction] does its work: a URI/intent template, a launcher shortcut, or an https
 * address whose JSON answer is shown in the bar ([CustomAction.api] says how).
 */
enum class ActionKind { INTENT, SHORTCUT, API }

/**
 * A command the user bound to a letter. "w ana oi" is the WhatsApp preset: the letter is `w`, the
 * [template] is a `VIEW` URI and the argument is a contact plus a message.
 *
 * Template placeholders, all filled with encoded values: `{number}` (digits with country code),
 * `{phone}` (the number as stored), `{name}` and `{text}`. For [ActionKind.SHORTCUT] the template
 * is the launcher shortcut id and [packages] holds the single owning package.
 */
data class CustomAction(
    val letter: String,
    val label: String,
    val kind: ActionKind,
    val template: String,
    val packages: List<String>,
    val arg: ArgKind,
    /** Opens an SMS to the contact when none of [packages] is installed. */
    val smsFallback: Boolean = false,
    /** The intent action; `VIEW` for deep links, `SEND` to share text, `DIAL`, `SENDTO`... */
    val intentAction: String = ACTION_VIEW,
    val mimeType: String? = null,
    /** Extra that carries the typed text (e.g. `android.intent.extra.TEXT`) instead of the URI. */
    val textExtra: String? = null,
    /**
     * A regular expression for text typed alone, without the letter: a line it matches in full runs
     * the action with that line as what was typed (`{text}`). Empty for none; see [CommandPattern].
     */
    val pattern: String = "",
    /** For [ActionKind.API]: which parts of the answer are shown, and how. */
    val api: ApiSpec? = null,
    /**
     * For [ActionKind.API]: the [ApiProvider] the action goes through, and then [template] is a path
     * on it ("/ws/{text}/json/"). Empty for an action with a full https address of its own.
     */
    val provider: String = "",
    /** The built-in pack the action came with ("brasil"), so the pack can be removed; empty for the user's own. */
    val pack: String = "",
    /** What the user wrote about the command (how to use it, what it needs), shown in its help; empty for none. */
    val help: String = "",
) {
    /** "w [contato] [mensagem]" shown in the command list. */
    val usage: String
        get() = when (arg) {
            ArgKind.NONE -> letter
            ArgKind.TEXT -> "$letter " + Texts.get(R.string.cmd_arg_text)
            ArgKind.CONTACT -> "$letter " + Texts.get(R.string.cmd_arg_contact)
            ArgKind.CONTACT_AND_TEXT -> "$letter " + Texts.get(R.string.cmd_arg_contact) + " " + Texts.get(R.string.cmd_arg_message)
        } + " · " + label.lowercase()
}

const val ACTION_VIEW = "android.intent.action.VIEW"

object CustomActions {

    /** The longest help a command keeps, so a pack cannot fill the bar with text. */
    const val MAX_HELP = 1000

    private const val SEND = "android.intent.action.SEND"
    private const val SENDTO = "android.intent.action.SENDTO"
    private const val DIAL = "android.intent.action.DIAL"
    private const val EXTRA_TEXT = "android.intent.extra.TEXT"

    /**
     * What the scan of an app looks for. Android cannot list the filters of another app, so each
     * recipe is tested by asking whether the app resolves an intent of that shape.
     */
    val PROBES: List<CustomAction>
        get() = probes()

    private fun probes() = listOf(
        CustomAction(
            "", Texts.get(R.string.cmd_probe_share), ActionKind.INTENT, "", emptyList(), ArgKind.TEXT,
            intentAction = SEND, mimeType = "text/plain", textExtra = EXTRA_TEXT,
        ),
        CustomAction(
            "",
            Texts.get(R.string.cmd_probe_sms),
            ActionKind.INTENT,
            "smsto:{phone}?body={text}",
            emptyList(),
            ArgKind.CONTACT_AND_TEXT,
            intentAction = SENDTO,
        ),
        CustomAction(
            "",
            Texts.get(R.string.cmd_probe_dial),
            ActionKind.INTENT,
            "tel:{phone}",
            emptyList(),
            ArgKind.CONTACT,
            intentAction = DIAL,
        ),
        CustomAction(
            "",
            Texts.get(R.string.cmd_probe_email),
            ActionKind.INTENT,
            "mailto:?subject={text}",
            emptyList(),
            ArgKind.TEXT,
            intentAction = SENDTO,
        ),
        CustomAction(
            "",
            Texts.get(R.string.cmd_probe_map),
            ActionKind.INTENT,
            "geo:0,0?q={text}",
            emptyList(),
            ArgKind.TEXT,
        ),
    )

    /** Names the built-in commands answer to, in any language; a custom letter cannot reuse them. */
    val RESERVED: Set<String> get() = BuiltInCommand.reserved()

    val WHATSAPP = CustomAction(
        letter = "w",
        label = "WhatsApp",
        kind = ActionKind.INTENT,
        template = "https://wa.me/{number}?text={text}",
        packages = listOf("com.whatsapp", "com.whatsapp.w4b"),
        arg = ArgKind.CONTACT_AND_TEXT,
        smsFallback = true,
    )

    /** What a fresh install starts with, and what the editor offers as "restore". */
    val DEFAULTS = listOf(WHATSAPP)

    /** Ready-made recipes the editor offers, so common apps need no manual intent. */
    val CATALOG: List<CustomAction>
        get() = catalog()

    private fun catalog() = listOf(
        WHATSAPP,
        CustomAction(
            "tg",
            "Telegram",
            ActionKind.INTENT,
            "tg://resolve?phone={number}",
            listOf("org.telegram.messenger", "org.telegram.messenger.web"),
            ArgKind.CONTACT,
        ),
        CustomAction(
            "sms",
            "SMS",
            ActionKind.INTENT,
            "smsto:{phone}?body={text}",
            emptyList(),
            ArgKind.CONTACT_AND_TEXT,
        ),
        CustomAction(
            "yt",
            Texts.get(R.string.cmd_recipe_search, "YouTube"),
            ActionKind.INTENT,
            "https://www.youtube.com/results?search_query={text}",
            listOf("com.google.android.youtube"),
            ArgKind.TEXT,
        ),
        CustomAction(
            "sp",
            Texts.get(R.string.cmd_recipe_search, "Spotify"),
            ActionKind.INTENT,
            "spotify:search:{text}",
            listOf("com.spotify.music"),
            ArgKind.TEXT,
        ),
    )

    /**
     * Adds [imported] to [existing]. An action whose letter is invalid, reserved or already taken is
     * left out, so an import never replaces what the user has. Returns the list and how many were skipped.
     */
    fun merge(existing: List<CustomAction>, imported: List<CustomAction>): Pair<List<CustomAction>, Int> {
        var list = existing
        var skipped = 0
        for (action in imported) {
            val letter = action.letter.trim().lowercase()
            if (validateLetter(letter, list) == null) list = list + action.copy(letter = letter, pattern = CommandPattern.usable(action.pattern)) else skipped++
        }
        return list to skipped
    }

    /** Null when [letter] can be used, otherwise why not. [ignore] is the action being edited. */
    fun validateLetter(letter: String, existing: List<CustomAction>, ignore: CustomAction? = null): String? {
        val l = letter.trim().lowercase()
        return when {
            l.isEmpty() -> Texts.get(R.string.cmd_err_letter_empty)
            !l.all { it.isCommandWordChar() } -> Texts.get(R.string.cmd_err_letter_chars)
            l in RESERVED -> Texts.get(R.string.cmd_err_reserved, l)
            existing.any { it !== ignore && it.letter == l } -> Texts.get(R.string.cmd_err_in_use, l)
            else -> null
        }
    }
}
