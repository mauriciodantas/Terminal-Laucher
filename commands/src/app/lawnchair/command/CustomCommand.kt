package app.lawnchair.command

import app.lawnchair.util.Texts

/**
 * A custom action as a command: "w ana oi", "yt lofi". An action that takes a contact also takes a
 * phone number typed alone, so the number offers it next to the call.
 */
class CustomCommand(private val action: CustomAction) : Command {

    override val word: String get() = action.letter

    override fun names(): Set<String> = setOf(action.letter)

    override val requiresArgument: Boolean get() = action.arg != ArgKind.NONE

    override fun isApplicable(text: String, ctx: CommandContext): Boolean = (action.arg == ArgKind.CONTACT || action.arg == ArgKind.CONTACT_AND_TEXT) && PhoneNumber.of(text) != null

    override fun analyze(arg: String, ctx: CommandContext): Analysis = CommandEngine.analyzeCustom(action, arg, ctx.contacts, ctx.contactsGranted, ctx.extras)

    override fun help(): CommandHelpText = CommandHelpText(action.letter.uppercase() + " · " + action.label.uppercase(), Texts.get(R.string.cmd_help_custom) + "\n› " + action.usage)

    override fun option(text: String, ctx: CommandContext): Suggestion = Suggestion("${action.letter} ${text.trim()}", action.label.uppercase(), CommandEngine.optionKind())
}
