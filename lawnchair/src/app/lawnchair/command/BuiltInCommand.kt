package app.lawnchair.command

import androidx.annotation.StringRes
import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents
import com.android.launcher3.R

/**
 * The commands the bar understands without any setup. Each one is typed with the word of the app
 * language ([word]), and the Portuguese and English words always work too, so aliases, shared
 * packs and agents keep working whatever the language.
 */
enum class BuiltInCommand(@StringRes private val wordRes: Int, val portuguese: String, val english: String) {
    OPEN(R.string.cmd_word_open, "abrir", "open"),
    ALARM(R.string.cmd_word_alarm, "alarme", "alarm"),
    CALC(R.string.cmd_word_calc, "calc", "calc"),
    CALL(R.string.cmd_word_call, "ligar", "call"),
    ROUTE(R.string.cmd_word_route, "rota", "route"),
    ;

    /** The word shown and suggested: "abrir" in Portuguese, "open" in English. */
    val word: String get() = Texts.get(wordRes)

    /** Every word that runs this command, accent-folded like the typed token. */
    fun words(): Set<String> = setOf(word, portuguese, english).map { it.foldAccents() }.toSet()

    companion object {
        /** One-letter shortcuts, the same in every language: a new task and calc. */
        const val TASK = "t"
        const val CALC_SHORT = "c"

        /** The command an accent-folded [token] names, or null. "c" is calc. */
        fun of(token: String): BuiltInCommand? = if (token == CALC_SHORT) CALC else entries.firstOrNull { token in it.words() }

        /** Words a custom letter or an alias cannot take. */
        fun reserved(): Set<String> = entries.flatMap { it.words() }.toSet() + TASK + CALC_SHORT
    }
}

/**
 * A character a command word, letter or alias may hold: letters and digits of any script, and the
 * vowel signs and accents that scripts like Devanagari write as separate marks.
 */
internal fun Char.isCommandWordChar(): Boolean = isLetterOrDigit() ||
    Character.getType(this).toByte().let { it == Character.NON_SPACING_MARK || it == Character.COMBINING_SPACING_MARK }
