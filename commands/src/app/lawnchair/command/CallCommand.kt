package app.lawnchair.command

import app.lawnchair.util.Texts

/** "ligar ana" calls a contact, "ligar 99999-9999" a number. A phone number typed alone offers the call. */
object CallCommand : Command {

    override val word: String get() = BuiltInCommand.CALL.word

    override fun names(): Set<String> = BuiltInCommand.CALL.words()

    override val requiresArgument = true

    override fun isApplicable(text: String, ctx: CommandContext): Boolean = PhoneNumber.of(text) != null

    override fun analyze(arg: String, ctx: CommandContext): Analysis = CommandEngine.analyzeContact(arg, ctx.contacts, ctx.contactsGranted, word, Texts.get(R.string.cmd_call_title)) {
        CommandAction.Call(it)
    }
}
