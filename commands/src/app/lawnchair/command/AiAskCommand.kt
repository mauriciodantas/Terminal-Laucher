package app.lawnchair.command

import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents

/** "ia": talks to the AI set up in the settings. It is listed only once a key is saved. */
object AiAskCommand : BuiltIn(BuiltInCommand.AI, R.string.cmd_usage_ai, R.string.cmd_help_ai) {

    override fun isListed(extras: CommandExtras): Boolean = extras.ai != null

    override fun analyze(arg: String, ctx: CommandContext): Analysis = analyze(arg, ctx.extras)

    /**
     * "ia [pergunta]" talks, "ia fazer [pedido]" turns the request into a command and "ia criar
     * [descrição]" writes a new one. Until a key is saved Enter opens the settings where it goes.
     */
    fun analyze(arg: String, extras: CommandExtras): Analysis {
        val title = Texts.get(R.string.cmd_ai_title)
        // Setting the service up works while the AI is still locked: that is how the first key gets in.
        val head = arg.trim().takeWhile { !it.isWhitespace() }
        if (AiMode.of(head) == AiMode.CONFIG) return configure(arg.trim().substring(head.length).trim(), extras)
        val service = extras.ai ?: return commandResult(title, Texts.get(R.string.cmd_ai_locked), Tone.WARN, CommandAction.Ai(AiMode.SETUP, ""))
        val text = arg.trim()
        val first = text.takeWhile { !it.isWhitespace() }
        val mode = AiMode.of(first)
        var prompt = if (mode != null) text.substring(first.length).trim() else text
        var chosen = mode ?: AiMode.CHAT
        // "ia criar tema de halloween" asks for a theme, not for a command.
        val next = prompt.takeWhile { !it.isWhitespace() }
        val nextWord = next.foldAccents()
        if (chosen == AiMode.CREATE && AiMode.THEME in AppHost.aiModes && nextWord.isNotEmpty() && (nextWord in AiMode.THEME.words() || nextWord in BuiltInCommand.THEME.words())) {
            chosen = AiMode.THEME
            prompt = prompt.substring(next.length).trim()
        }
        val word = BuiltInCommand.AI.word
        // While nothing or only part of a mode word is typed, the modes are offered.
        val offers = if (mode == null && ' ' !in text) {
            AiMode.OFFERED.filter { it.word.foldAccents().startsWith(first.foldAccents()) }
                .map { Suggestion("$word ${it.word} ", modeLabel(it), Texts.get(R.string.cmd_kind_option)) }
        } else {
            emptyList()
        }
        val heading = title + " · " + modeLabel(chosen) + " · " + service.substringBefore(" · ").uppercase()
        if (chosen in AppHost.aiModes) AppHost.aiAnalyze(chosen, prompt, heading, offers)?.let { return it }
        return when {
            prompt.isEmpty() -> commandResult(heading, Texts.get(emptyHint(chosen)), Tone.IDLE, null, suggestions = offers)
            else -> commandResult(heading, prompt, Tone.OK, CommandAction.Ai(chosen, prompt), suggestions = offers)
        }
    }

    private fun modeLabel(mode: AiMode): String = Texts.get(
        when (mode) {
            AiMode.RUN -> R.string.cmd_ai_mode_run
            AiMode.CREATE -> R.string.cmd_ai_mode_create
            AiMode.FOLDERS -> R.string.cmd_ai_mode_folders
            AiMode.THEME -> R.string.cmd_ai_mode_theme
            AiMode.CONFIG -> R.string.cmd_ai_mode_config
            else -> R.string.cmd_ai_mode_chat
        },
    )

    private fun emptyHint(mode: AiMode): Int = when (mode) {
        AiMode.RUN -> R.string.cmd_ai_run_empty
        AiMode.CREATE -> R.string.cmd_ai_create_empty
        else -> R.string.cmd_ai_chat_empty
    }

    /** The word that names each service after "ia provedor". */
    private val providerWords = mapOf(
        "openai" to AiProvider.OPENAI,
        "gpt" to AiProvider.OPENAI,
        "chatgpt" to AiProvider.OPENAI,
        "claude" to AiProvider.ANTHROPIC,
        "anthropic" to AiProvider.ANTHROPIC,
        "gemini" to AiProvider.GEMINI,
        "google" to AiProvider.GEMINI,
    )
    private val removeWords = setOf("remover", "remove", "apagar", "limpar", "clear")

    /** What "ia provedor ..." asks for; [error] is set when the words do not make sense. */
    data class Setup(val provider: AiProvider?, val key: String?, val model: String?, val remove: Boolean, val error: Int? = null)

    /**
     * "[service] [key] [model]", each part optional: a key is told by its prefix or its length, or is
     * the word after the service when the saved key cannot belong to that service (none saved, or
     * another service); any other word is the model. A null part keeps what is saved.
     */
    fun parseSetup(rest: String, saved: AiProvider?): Setup {
        val words = rest.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.firstOrNull()?.foldAccents() in removeWords) return Setup(null, null, null, remove = true)
        var provider: AiProvider? = null
        var key: String? = null
        var model: String? = null
        words.forEachIndexed { i, word ->
            val named = if (i == 0) providerWords[word.foldAccents()] else null
            when {
                named != null -> provider = named
                key == null && model == null && (AiProvider.guess(word) != null || word.length >= LONG_KEY || (provider != null && provider != saved)) -> key = word
                model == null -> model = word
                else -> return Setup(provider, key, model, false, R.string.cmd_ai_setup_extra)
            }
        }
        return Setup(provider ?: key?.let { AiProvider.guess(it) }, key, model, false)
    }

    /** A word this long is a key: model names are short, and newer keys carry no known prefix. */
    private const val LONG_KEY = 32

    private fun mask(key: String): String = if (key.length > 8) key.take(4) + "…" + key.takeLast(2) else "…"

    private fun configure(rest: String, extras: CommandExtras): Analysis {
        val title = Texts.get(R.string.cmd_ai_title) + " · " + modeLabel(AiMode.CONFIG)
        val word = BuiltInCommand.AI.word
        val hasKey = extras.ai != null
        val first = rest.takeWhile { !it.isWhitespace() }.foldAccents()
        // Until a service is named the services are offered, with the one in use shown.
        val offers = if (' ' !in rest) {
            providerWords.entries.distinctBy { it.value }.map { it.key to it.value }
                .filter { (name, _) -> name.startsWith(first) }
                .map { (name, p) -> Suggestion("$word ${AiMode.CONFIG.word} $name ", p.label, Texts.get(R.string.cmd_kind_option)) }
        } else {
            emptyList()
        }
        if (rest.isEmpty()) {
            val current = extras.ai?.let { Texts.get(R.string.cmd_ai_setup_current, it.uppercase()) } ?: Texts.get(R.string.cmd_ai_setup_none)
            return commandResult(title, current, Tone.IDLE, null, suggestions = offers)
        }
        val saved = extras.ai?.let { label -> AiProvider.entries.firstOrNull { label.startsWith(it.label) } }
        val setup = parseSetup(rest, saved)
        if (setup.error != null) return commandResult(title, Texts.get(setup.error), Tone.ERROR, null, suggestions = offers)
        if (setup.remove) {
            if (!hasKey) return commandResult(title, Texts.get(R.string.cmd_ai_setup_nothing), Tone.WARN, null)
            return commandResult(title, Texts.get(R.string.cmd_ai_setup_remove), Tone.WARN, CommandAction.AiSetup(null, null, null, remove = true))
        }
        if (!hasKey && setup.key == null) {
            return commandResult(title, Texts.get(R.string.cmd_ai_setup_key_missing, (setup.provider?.keyHint ?: "…")), Tone.WARN, null, suggestions = offers)
        }
        val parts = listOfNotNull(
            setup.provider?.label?.uppercase(),
            setup.key?.let { Texts.get(R.string.cmd_ai_setup_key, mask(it)) },
            setup.model?.let { Texts.get(R.string.cmd_ai_setup_model, it) },
            Texts.get(R.string.cmd_ai_setup_save),
        )
        return commandResult(title, parts.joinToString(" · "), Tone.OK, CommandAction.AiSetup(setup.provider, setup.key, setup.model), suggestions = offers)
    }
}
