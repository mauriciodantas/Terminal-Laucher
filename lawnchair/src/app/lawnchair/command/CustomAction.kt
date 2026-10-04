package app.lawnchair.command

/** What the user types after the letter of a [CustomAction]. */
enum class ArgKind { NONE, TEXT, CONTACT, CONTACT_AND_TEXT }

/** How a [CustomAction] reaches the app: a URI/intent template or a launcher shortcut. */
enum class ActionKind { INTENT, SHORTCUT }

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
) {
    /** "w [contato] [mensagem]" shown in the command list. */
    val usage: String
        get() = when (arg) {
            ArgKind.NONE -> letter
            ArgKind.TEXT -> "$letter [texto]"
            ArgKind.CONTACT -> "$letter [contato]"
            ArgKind.CONTACT_AND_TEXT -> "$letter [contato] [mensagem]"
        } + " · " + label.lowercase()
}

const val ACTION_VIEW = "android.intent.action.VIEW"

object CustomActions {

    private const val SEND = "android.intent.action.SEND"
    private const val SENDTO = "android.intent.action.SENDTO"
    private const val DIAL = "android.intent.action.DIAL"
    private const val EXTRA_TEXT = "android.intent.extra.TEXT"

    /**
     * What the scan of an app looks for. Android cannot list the filters of another app, so each
     * recipe is tested by asking whether the app resolves an intent of that shape.
     */
    val PROBES = listOf(
        CustomAction(
            "", "Compartilhar texto", ActionKind.INTENT, "", emptyList(), ArgKind.TEXT,
            intentAction = SEND, mimeType = "text/plain", textExtra = EXTRA_TEXT,
        ),
        CustomAction(
            "", "Enviar mensagem (SMS)", ActionKind.INTENT, "smsto:{phone}?body={text}",
            emptyList(), ArgKind.CONTACT_AND_TEXT, intentAction = SENDTO,
        ),
        CustomAction(
            "", "Discar número", ActionKind.INTENT, "tel:{phone}", emptyList(), ArgKind.CONTACT,
            intentAction = DIAL,
        ),
        CustomAction(
            "", "Novo e-mail", ActionKind.INTENT, "mailto:?subject={text}", emptyList(), ArgKind.TEXT,
            intentAction = SENDTO,
        ),
        CustomAction(
            "", "Pesquisar no mapa", ActionKind.INTENT, "geo:0,0?q={text}", emptyList(), ArgKind.TEXT,
        ),
    )

    /** Names the built-in commands answer to; a custom letter cannot reuse them. */
    val RESERVED = setOf("abrir", "alarme", "calc", "ligar", "rota", "t", "c")

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
    val CATALOG = listOf(
        WHATSAPP,
        CustomAction(
            "tg", "Telegram", ActionKind.INTENT, "tg://resolve?phone={number}",
            listOf("org.telegram.messenger", "org.telegram.messenger.web"), ArgKind.CONTACT,
        ),
        CustomAction(
            "sms", "SMS", ActionKind.INTENT, "smsto:{phone}?body={text}",
            emptyList(), ArgKind.CONTACT_AND_TEXT,
        ),
        CustomAction(
            "yt", "YouTube · pesquisar", ActionKind.INTENT,
            "https://www.youtube.com/results?search_query={text}",
            listOf("com.google.android.youtube"), ArgKind.TEXT,
        ),
        CustomAction(
            "sp", "Spotify · pesquisar", ActionKind.INTENT, "spotify:search:{text}",
            listOf("com.spotify.music"), ArgKind.TEXT,
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
            if (validateLetter(letter, list) == null) list = list + action.copy(letter = letter) else skipped++
        }
        return list to skipped
    }

    /** Null when [letter] can be used, otherwise why not. [ignore] is the action being edited. */
    fun validateLetter(letter: String, existing: List<CustomAction>, ignore: CustomAction? = null): String? {
        val l = letter.trim().lowercase()
        return when {
            l.isEmpty() -> "Informe uma letra"
            !l.all { it.isLetterOrDigit() } -> "Use apenas letras e números"
            l in RESERVED -> "\"$l\" já é um comando do sistema"
            existing.any { it !== ignore && it.letter == l } -> "\"$l\" já está em uso"
            else -> null
        }
    }
}
