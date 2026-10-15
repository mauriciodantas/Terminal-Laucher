package app.lawnchair.command

import app.lawnchair.util.Texts

/**
 * Imports a theme, a widget, or a command pack from a JSON string typed or pasted into the command bar.
 * Also runs on "importar {...}" or "import {...}".
 */
object ImportCommand : BuiltIn(
    id = BuiltInCommand.IMPORT,
    usageRes = R.string.cmd_usage_import,
    helpRes = R.string.cmd_help_import,
) {

    override val requiresArgument: Boolean = false

    override fun isApplicable(text: String, ctx: CommandContext): Boolean = JsonImport.isPotentialJson(text)

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val target = JsonImport.classify(arg)
        if (target == null) {
            return if (arg.isBlank()) {
                Analysis(
                    suggestions = emptyList(),
                    listTitle = Texts.get(R.string.cmd_list_suggestions),
                    needsContacts = false,
                    previewTitle = Texts.get(R.string.cmd_import_title),
                    preview = Texts.get(R.string.cmd_usage_import),
                    tone = Tone.IDLE,
                    action = null,
                )
            } else {
                CommandEngine.unknown(arg)
            }
        }

        return when (target) {
            is JsonImportTarget.Theme -> {
                val name = target.spec.name.ifBlank { "tema" }
                Analysis(
                    suggestions = listOf(
                        Suggestion(arg.trim(), Texts.get(R.string.cmd_import_action_theme), Texts.get(R.string.cmd_kind_import)),
                    ),
                    listTitle = Texts.get(R.string.cmd_list_suggestions),
                    needsContacts = false,
                    previewTitle = Texts.get(R.string.cmd_import_theme_title),
                    preview = Texts.get(R.string.cmd_import_theme_preview, name),
                    tone = Tone.OK,
                    action = CommandAction.ImportTheme(target.spec, target.rawJson),
                )
            }

            is JsonImportTarget.Widget -> {
                Analysis(
                    suggestions = listOf(
                        Suggestion(arg.trim(), Texts.get(R.string.cmd_import_action_widget), Texts.get(R.string.cmd_kind_import)),
                    ),
                    listTitle = Texts.get(R.string.cmd_list_suggestions),
                    needsContacts = false,
                    previewTitle = Texts.get(R.string.cmd_import_widget_title),
                    preview = Texts.get(R.string.cmd_import_widget_preview, target.name),
                    tone = Tone.OK,
                    action = CommandAction.ImportWidget(target.rawJson, target.name),
                )
            }

            is JsonImportTarget.Commands -> {
                val count = target.pack.actions.size + target.pack.aliases.size
                Analysis(
                    suggestions = listOf(
                        Suggestion(arg.trim(), Texts.get(R.string.cmd_import_action_commands), Texts.get(R.string.cmd_kind_import)),
                    ),
                    listTitle = Texts.get(R.string.cmd_list_suggestions),
                    needsContacts = false,
                    previewTitle = Texts.get(R.string.cmd_import_commands_title),
                    preview = Texts.get(R.string.cmd_import_commands_preview, count),
                    tone = Tone.OK,
                    action = CommandAction.ImportCommands(target.pack, target.rawJson),
                )
            }

            is JsonImportTarget.Invalid -> {
                val preview = if (target.incomplete) {
                    Texts.get(R.string.cmd_import_incomplete_json)
                } else {
                    Texts.get(R.string.cmd_import_unknown_json)
                }
                Analysis(
                    suggestions = emptyList(),
                    listTitle = Texts.get(R.string.cmd_list_suggestions),
                    needsContacts = false,
                    previewTitle = Texts.get(R.string.cmd_import_title),
                    preview = preview,
                    tone = if (target.incomplete) Tone.IDLE else Tone.WARN,
                    action = null,
                )
            }
        }
    }

    override fun option(text: String, ctx: CommandContext): Suggestion = Suggestion(text.trim(), Texts.get(R.string.cmd_import_title), CommandEngine.optionKind())
}
