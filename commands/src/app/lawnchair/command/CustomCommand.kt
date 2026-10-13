package app.lawnchair.command

import app.lawnchair.util.Texts

/**
 * A custom action as a command: "w ana oi", "yt lofi". Typed alone, it takes the text its pattern
 * matches ("AB123456789BR" for a tracking action) and, when it takes a contact, a phone number, so
 * the number offers it next to the call.
 */
class CustomCommand(private val action: CustomAction) : Command {

    override val word: String get() = action.letter

    override fun names(): Set<String> = setOf(action.letter)

    override val requiresArgument: Boolean get() = action.arg != ArgKind.NONE

    /** A pattern is narrower than what the built-ins take: "01310-100" lists the CEP before calc's subtraction. */
    override val optionOrder: Int get() = if (action.pattern.isNotEmpty()) -1 else 0

    override fun isApplicable(text: String, ctx: CommandContext): Boolean = CommandPattern.takes(action.pattern, text) ||
        ((action.arg == ArgKind.CONTACT || action.arg == ArgKind.CONTACT_AND_TEXT) && PhoneNumber.of(text) != null)

    override fun analyze(arg: String, ctx: CommandContext): Analysis = CommandEngine.analyzeCustom(action, arg, ctx.contacts, ctx.contactsGranted, ctx.extras)

    override fun help(): CommandHelpText = CommandHelpText(action.letter.uppercase() + " · " + action.label.uppercase(), Texts.get(R.string.cmd_help_custom) + "\n› " + action.usage)

    override fun option(text: String, ctx: CommandContext): Suggestion = Suggestion("${action.letter} ${text.trim()}", action.label.uppercase(), CommandEngine.optionKind())
}
