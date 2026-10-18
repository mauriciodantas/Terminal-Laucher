package app.lawnchair.command

import androidx.annotation.StringRes
import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents

/**
 * What "tema" (theme) does to the accent and the background, or to the whole theme (see [ThemeCommand]). Like the command words, each option is
 * typed with the word of the app language, and the Portuguese and English words always work too.
 */
enum class ThemeChange(
    @StringRes private val wordRes: Int,
    val portuguese: String,
    val english: String,
    @StringRes private val previewRes: Int,
) {
    INVERT(R.string.cmd_theme_invert, "inverter", "invert", R.string.cmd_theme_invert_preview),
    LIGHT(R.string.cmd_theme_light, "claro", "light", R.string.cmd_theme_light_preview),
    DARK(R.string.cmd_theme_dark, "escuro", "dark", R.string.cmd_theme_dark_preview),
    SYSTEM(R.string.cmd_theme_system, "sistema", "system", R.string.cmd_theme_system_preview),

    /** Every element of the theme back to how the app comes. */
    RESET(R.string.cmd_theme_reset, "padrao", "default", R.string.cmd_theme_reset_preview),
    ;

    /** The word shown and suggested: "inverter" in Portuguese, "invert" in English. */
    val word: String get() = Texts.get(wordRes)

    /** What the option does, as the preview line. */
    val preview: String get() = Texts.get(previewRes)

    /** Every word that picks this option, accent-folded like the typed text. */
    fun words(): Set<String> = setOf(word, portuguese, english).map { it.foldAccents() }.toSet()

    companion object {
        /** The option an accent-folded [word] names, or null. */
        fun of(word: String): ThemeChange? = entries.firstOrNull { word in it.words() }

        /** "[inverter|claro|escuro|sistema|padrão|destaque #cor|…|nome]", for the usage line. */
        fun usage(): String = (entries.map { it.word } + Texts.get(R.string.cmd_usage_theme_more)).joinToString("|", "[", "]")
    }
}
