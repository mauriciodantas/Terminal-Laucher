package app.lawnchair.command

import androidx.annotation.StringRes
import app.lawnchair.util.Texts

/**
 * A command that comes with the bar. Its words come from [id], so aliases, shared packs and voice
 * keep working whatever the language; the usage line and the help are its own.
 */
abstract class BuiltIn(
    val id: BuiltInCommand,
    @StringRes private val usageRes: Int?,
    @StringRes private val helpRes: Int,
) : Command {

    override val word: String get() = id.word

    override fun names(): Set<String> = id.words()

    override fun usages(): List<Pair<String, String>> = usageRes?.let { listOf(word to "$word " + Texts.get(it)) }.orEmpty()

    override fun help(): CommandHelpText? = CommandHelpText(word.uppercase(), Texts.get(helpRes))
}

/** The [Analysis] most commands give: a title, a preview line and, when it can run, an action. */
internal fun commandResult(
    title: String,
    preview: String,
    tone: Tone,
    action: CommandAction?,
    suggestions: List<Suggestion> = emptyList(),
    listTitle: String = Texts.get(R.string.cmd_list_suggestions),
    need: Need? = null,
) = Analysis(
    suggestions = suggestions,
    listTitle = listTitle,
    needsContacts = false,
    previewTitle = title,
    preview = preview,
    tone = tone,
    action = action,
    need = need,
)
