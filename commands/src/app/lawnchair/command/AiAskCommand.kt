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
        val service = extras.ai ?: return commandResult(title, Texts.get(R.string.cmd_ai_locked), Tone.WARN, CommandAction.Ai(AiMode.SETUP, ""))
        val text = arg.trim()
        val first = text.takeWhile { !it.isWhitespace() }
        val mode = AiMode.of(first)
        var prompt = if (mode != null) text.substring(first.length).trim() else text
        var chosen = mode ?: AiMode.CHAT
        // "ia criar tema de halloween" asks for a theme, not for a command.
        val next = prompt.takeWhile { !it.isWhitespace() }
        val nextWord = next.foldAccents()
        if (chosen == AiMode.CREATE && nextWord.isNotEmpty() && (nextWord in AiMode.THEME.words() || nextWord in BuiltInCommand.THEME.words())) {
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
        if (chosen == AiMode.THEME) {
            // A theme needs no request either: Enter alone asks for a surprise.
            return commandResult(heading, prompt.ifEmpty { Texts.get(R.string.cmd_ai_theme_empty) }, Tone.OK, CommandAction.Ai(chosen, prompt), suggestions = offers)
        }
        if (chosen == AiMode.FOLDERS) {
            // Folders need no request: Enter alone sorts the apps the AI's way.
            val (request, force) = AiFolders.splitForce(prompt)
            val hint = request.ifEmpty { Texts.get(R.string.cmd_ai_folders_empty) }
            val text = if (force) hint + " · " + Texts.get(R.string.cmd_ai_folders_force) else hint
            return commandResult(heading, text, if (force) Tone.WARN else Tone.OK, CommandAction.Ai(chosen, request, force), suggestions = offers)
        }
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
            else -> R.string.cmd_ai_mode_chat
        },
    )

    private fun emptyHint(mode: AiMode): Int = when (mode) {
        AiMode.RUN -> R.string.cmd_ai_run_empty
        AiMode.CREATE -> R.string.cmd_ai_create_empty
        else -> R.string.cmd_ai_chat_empty
    }
}
