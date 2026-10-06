package app.lawnchair.command

/**
 * Turns what the speech recognizer heard into what the command bar understands: "chamar ana"
 * becomes "ligar ana", "calcular 12 vezes 8" becomes "calc 12*8". Pure, so it can be unit tested.
 */
object VoiceCommand {

    /** Spoken command words, and the command each one stands for. */
    private val verbs = mapOf(
        "abrir" to "abrir", "abre" to "abrir", "abra" to "abrir", "iniciar" to "abrir",
        "ligar" to "ligar", "liga" to "ligar", "chamar" to "ligar", "telefonar" to "ligar",
        "alarme" to "alarme", "despertador" to "alarme", "acordar" to "alarme",
        "calcular" to "calc", "calcule" to "calc", "calc" to "calc", "conta" to "calc",
        "rota" to "rota", "navegar" to "rota", "navegação" to "rota",
        "tarefa" to "t", "lembrar" to "t", "anotar" to "t",
        "mensagem" to "w", "whatsapp" to "w", "zap" to "w", "enviar" to "w",
        "t" to "t", "w" to "w", "c" to "calc",
    )

    /** Words that only glue the sentence together: "ligar PARA a ana", "abrir O chrome". */
    private val filler = setOf("para", "pra", "pro", "o", "a", "ao", "à", "o app", "app", "aplicativo", "de", "do", "da")

    private val mathWords = listOf(
        "dividido por" to "/", "dividido" to "/", "vezes" to "*", "multiplicado por" to "*",
        "mais" to "+", "menos" to "-", "por cento" to "%", "vírgula" to ",", "x" to "*",
    )

    /**
     * [aliases] are the user's own words: a sentence that starts with one is left as spoken, so the
     * command bar expands it instead of the built-in verbs ("zap" can be an alias, not only "w").
     */
    fun normalize(spoken: String, aliases: List<CommandAlias> = emptyList()): String {
        val words = spoken.trim().lowercase().replace(Regex("\\s+"), " ")
        if (words.isEmpty()) return ""
        if (Aliases.find(words.substringBefore(' '), aliases) != null) return words
        var first = words.substringBefore(' ')
        var rest = if (' ' in words) words.substringAfter(' ') else ""

        // "enviar mensagem para ana": the second word is the real command.
        if (first == "enviar" && rest.substringBefore(' ') in setOf("mensagem", "whatsapp", "zap")) {
            first = "w"
            rest = rest.substringAfter(' ', "")
        }
        val command = verbs[first] ?: return words

        rest = when (command) {
            "calc" -> normalizeMath(rest)
            "alarme" -> rest.replace(Regex("^(para as|pras|para|pra|às|as)\\s+"), "")
            else -> dropFiller(rest)
        }
        return if (rest.isEmpty()) "$command " else "$command $rest"
    }

    private fun dropFiller(text: String): String {
        var result = text
        while (true) {
            val head = result.substringBefore(' ')
            if (head in filler && ' ' in result) result = result.substringAfter(' ') else break
        }
        return result
    }

    private fun normalizeMath(text: String): String {
        var result = " $text "
        mathWords.forEach { (word, symbol) ->
            result = result.replace(Regex("(?<=\\s)" + Regex.escape(word) + "(?=\\s|\\d)"), symbol)
        }
        return result.trim().replace(Regex("\\s*([+\\-*/%,])\\s*"), "$1")
    }
}
