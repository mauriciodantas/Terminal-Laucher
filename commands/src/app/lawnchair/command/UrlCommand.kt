package app.lawnchair.command

import app.lawnchair.util.Texts

/** A web address typed alone ("github.com", "192.168.0.1:8080") opens the site. It has no word of its own. */
object UrlCommand : Command {

    override val word: String = ""

    override fun names(): Set<String> = emptySet()

    override fun isApplicable(text: String, ctx: CommandContext): Boolean = WebAddress.of(text) != null

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val url = WebAddress.of(arg) ?: return CommandEngine.unknown(arg)
        return Analysis(
            suggestions = emptyList(),
            listTitle = Texts.get(R.string.cmd_list_suggestions),
            needsContacts = false,
            previewTitle = Texts.get(R.string.cmd_url_title),
            preview = url.substringAfter("://").removeSuffix("/"),
            tone = Tone.OK,
            action = CommandAction.OpenUrl(url),
        )
    }

    override fun option(text: String, ctx: CommandContext): Suggestion = Suggestion(text.trim(), Texts.get(R.string.cmd_url_title), CommandEngine.optionKind())
}
