package app.lawnchair.command

import androidx.annotation.StringRes
import app.lawnchair.util.Texts
import com.android.launcher3.R

/**
 * Turns what the speech recognizer heard into what the command bar understands: "chamar ana"
 * becomes "ligar ana", "calcular 12 vezes 8" becomes "calc 12*8". The spoken words come from the
 * app language; the command words of [BuiltInCommand] are understood in Portuguese and English too.
 * Pure, so it can be unit tested.
 */
object VoiceCommand {

    /** The command bar letter of the default WhatsApp action. */
    private const val MESSAGE = "w"

    private fun words(@StringRes id: Int): List<String> = Texts.get(id).split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    /** Spoken command words, and the command each one stands for. */
    private fun verbs(): Map<String, String> {
        val verbs = LinkedHashMap<String, String>()
        BuiltInCommand.entries.forEach { command ->
            listOf(command.word, command.portuguese, command.english).forEach { verbs[it.lowercase()] = command.word }
        }
        listOf(
            BuiltInCommand.OPEN to R.string.cmd_voice_open,
            BuiltInCommand.CALL to R.string.cmd_voice_call,
            BuiltInCommand.ALARM to R.string.cmd_voice_alarm,
            BuiltInCommand.CALC to R.string.cmd_voice_calc,
            BuiltInCommand.ROUTE to R.string.cmd_voice_route,
        ).forEach { (command, id) -> words(id).forEach { verbs[it] = command.word } }
        words(R.string.cmd_voice_task).forEach { verbs[it] = BuiltInCommand.TASK }
        words(R.string.cmd_voice_message).forEach { verbs[it] = MESSAGE }
        verbs[BuiltInCommand.TASK] = BuiltInCommand.TASK
        verbs[MESSAGE] = MESSAGE
        verbs[BuiltInCommand.CALC_SHORT] = BuiltInCommand.CALC.word
        return verbs
    }

    /** "dividido por" to "/": spoken math, longest phrases listed first. */
    private fun mathWords(): List<Pair<String, String>> = Texts.get(R.string.cmd_voice_math).split(';')
        .mapNotNull { entry -> entry.substringBefore('=', "").trim().lowercase().takeIf { it.isNotEmpty() }?.let { it to entry.substringAfter('=') } }

    /**
     * [aliases] are the user's own words: a sentence that starts with one is left as spoken, so the
     * command bar expands it instead of the built-in verbs ("zap" can be an alias, not only "w").
     */
    fun normalize(spoken: String, aliases: List<CommandAlias> = emptyList()): String {
        val words = spoken.trim().lowercase().replace(Regex("\\s+"), " ")
        if (words.isEmpty()) return ""
        if (Aliases.find(words.substringBefore(' '), aliases) != null) return words
        val verbs = verbs()
        var first = words.substringBefore(' ')
        var rest = if (' ' in words) words.substringAfter(' ') else ""

        // "enviar mensagem para ana": the second word is the real command.
        if (verbs[first] == MESSAGE && verbs[rest.substringBefore(' ')] == MESSAGE) {
            rest = rest.substringAfter(' ', "")
        }
        val command = verbs[first] ?: return words

        rest = when (command) {
            BuiltInCommand.CALC.word -> normalizeMath(rest)
            BuiltInCommand.ALARM.word -> rest.replace(alarmLeadIn(), "")
            else -> dropFiller(rest)
        }
        return if (rest.isEmpty()) "$command " else "$command $rest"
    }

    /** "às" in "alarme às 6:30". */
    private fun alarmLeadIn(): Regex {
        val words = words(R.string.cmd_voice_alarm_lead_in).sortedByDescending { it.length }
        return if (words.isEmpty()) Regex("^$") else Regex("^(" + words.joinToString("|") { Regex.escape(it) } + ")\\s+")
    }

    private fun dropFiller(text: String): String {
        val filler = words(R.string.cmd_voice_filler).toSet()
        var result = text
        while (true) {
            val head = result.substringBefore(' ')
            if (head in filler && ' ' in result) result = result.substringAfter(' ') else break
        }
        return result
    }

    private fun normalizeMath(text: String): String {
        var result = " $text "
        mathWords().forEach { (word, symbol) ->
            result = result.replace(Regex("(?<=\\s)" + Regex.escape(word) + "(?=\\s|\\d)"), Regex.escapeReplacement(symbol))
        }
        return result.trim().replace(Regex("\\s*([+\\-*/%,.])\\s*"), "$1")
    }
}
