package app.lawnchair.command

import app.lawnchair.util.Texts

/**
 * "alias [name [destination]]": lists commands, prepares a command for editing, or creates/updates a command.
 */
object AliasCommand : BuiltIn(BuiltInCommand.ALIAS, R.string.cmd_usage_alias, R.string.cmd_help_alias_command) {

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val trimmed = arg.trim()
        val aliasWord = BuiltInCommand.ALIAS.word
        if (trimmed.isEmpty()) {
            val items = ctx.custom.map { action ->
                val line = "$aliasWord " + CommandLine.serialize(action)
                val kind = if (action.kind == ActionKind.API) "API ›" else Texts.get(R.string.cmd_kind_action)
                Suggestion(line, "${action.letter}  ${action.template.ifEmpty { action.intentAction }}", kind)
            } + ctx.aliases.map { alias ->
                val line = "$aliasWord " + CommandLine.serialize(alias)
                Suggestion(line, "${alias.name}  ${alias.expansion}", Texts.get(R.string.cmd_kind_alias))
            }
            return Analysis(
                suggestions = items.take(CommandEngine.MAX_SUGGESTIONS * 2),
                listTitle = Texts.get(R.string.cmd_all_commands),
                needsContacts = false,
                previewTitle = aliasWord.uppercase(),
                preview = Texts.get(R.string.cmd_alias_list_preview),
                tone = Tone.IDLE,
                action = null,
                picksOnEnter = true,
            )
        }

        // Single word: "alias NOME"
        val words = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.size == 1 && '#' !in trimmed) {
            val name = words[0].lowercase()
            val existingAction = ctx.custom.firstOrNull { it.letter == name }
            val existingAlias = ctx.aliases.firstOrNull { it.name.lowercase() == name }
            if (existingAction != null) {
                val fullLine = "$aliasWord " + CommandLine.serialize(existingAction)
                return Analysis(
                    suggestions = listOf(Suggestion(fullLine, fullLine, Texts.get(R.string.cmd_kind_action))),
                    listTitle = Texts.get(R.string.cmd_list_suggestions),
                    needsContacts = false,
                    previewTitle = aliasWord.uppercase() + " · " + name.uppercase(),
                    preview = fullLine,
                    tone = Tone.OK,
                    action = CommandAction.FillLine(fullLine),
                )
            }
            if (existingAlias != null) {
                val fullLine = "$aliasWord " + CommandLine.serialize(existingAlias)
                return Analysis(
                    suggestions = listOf(Suggestion(fullLine, fullLine, Texts.get(R.string.cmd_kind_alias))),
                    listTitle = Texts.get(R.string.cmd_list_suggestions),
                    needsContacts = false,
                    previewTitle = aliasWord.uppercase() + " · " + name.uppercase(),
                    preview = fullLine,
                    tone = Tone.OK,
                    action = CommandAction.FillLine(fullLine),
                )
            }
        }

        // Contextual autocomplete triggers
        val contextual = contextualSuggestions(arg.trimStart(), ctx)
        if (contextual.isNotEmpty()) {
            return Analysis(
                suggestions = contextual.take(CommandEngine.MAX_SUGGESTIONS * 2),
                listTitle = Texts.get(R.string.cmd_list_suggestions),
                needsContacts = false,
                previewTitle = aliasWord.uppercase(),
                preview = contextual.first().label,
                tone = Tone.IDLE,
                action = null,
                picksOnEnter = true,
            )
        }

        // Parsing "alias NOME DESTINO [# RÓTULO]"
        val parseResult = CommandLine.parse(trimmed, ctx.custom, ctx.aliases)
        if (parseResult is ParseResult.Failure) {
            return Analysis(
                suggestions = emptyList(),
                listTitle = Texts.get(R.string.cmd_list_suggestions),
                needsContacts = false,
                previewTitle = aliasWord.uppercase(),
                preview = parseResult.error.message,
                tone = Tone.ERROR,
                action = null,
            )
        }

        val parsed = (parseResult as ParseResult.Success).command
        val name = parsed.name
        val existingAction = ctx.custom.firstOrNull { it.letter == name }
        val existingAlias = ctx.aliases.firstOrNull { it.name.lowercase() == name }
        val validation = CommandLine.validate(
            command = parsed,
            existingActions = ctx.custom,
            existingAliases = ctx.aliases,
            providers = ctx.extras.providers,
            ignore = existingAction ?: existingAlias,
        )
        if (!validation.isValid) {
            return Analysis(
                suggestions = emptyList(),
                listTitle = Texts.get(R.string.cmd_list_suggestions),
                needsContacts = false,
                previewTitle = aliasWord.uppercase(),
                preview = validation.errors.first(),
                tone = Tone.ERROR,
                action = null,
            )
        }

        val alreadyExists = existingAction != null || existingAlias != null
        val canonical = CommandLine.serialize(parsed)

        val action = (parsed as? ParsedCommand.Action)?.action
        val isWrite = action != null && action.kind == ActionKind.API &&
            action.api?.method?.uppercase() in listOf("POST", "PUT", "PATCH", "DELETE")

        if (alreadyExists) {
            val currentLine = when {
                existingAction != null -> CommandLine.serialize(existingAction)
                existingAlias != null -> CommandLine.serialize(existingAlias)
                else -> ""
            }
            return Analysis(
                suggestions = listOf(Suggestion(name + " ", parsed.label, Texts.get(R.string.cmd_kind_new_command))),
                listTitle = Texts.get(R.string.cmd_list_suggestions),
                needsContacts = false,
                previewTitle = aliasWord.uppercase() + " · " + Texts.get(R.string.cmd_alias_conflict_title),
                preview = Texts.get(R.string.cmd_alias_conflict_preview) + "\n" +
                    Texts.get(R.string.cmd_alias_conflict_current, currentLine) + "\n" +
                    Texts.get(R.string.cmd_alias_conflict_new, canonical),
                tone = Tone.WARN,
                action = CommandAction.SaveCommand(parsed, trimmed, isReplacement = true),
            )
        }

        if (isWrite && action != null) {
            val method = action.api?.method?.uppercase() ?: "POST"
            val target = action.provider
            return Analysis(
                suggestions = listOf(Suggestion(name + " ", parsed.label, Texts.get(R.string.cmd_kind_new_command))),
                listTitle = Texts.get(R.string.cmd_list_suggestions),
                needsContacts = false,
                previewTitle = aliasWord.uppercase() + " · " + name.uppercase(),
                preview = Texts.get(R.string.cmd_api_write_ready, method, target) + " · " + canonical,
                tone = Tone.WARN,
                action = CommandAction.SaveCommand(parsed, trimmed, isReplacement = false),
            )
        }

        if (validation.warnings.isNotEmpty()) {
            return Analysis(
                suggestions = listOf(Suggestion(name + " ", parsed.label, Texts.get(R.string.cmd_kind_new_command))),
                listTitle = Texts.get(R.string.cmd_list_suggestions),
                needsContacts = false,
                previewTitle = aliasWord.uppercase() + " · " + name.uppercase(),
                preview = validation.warnings.first() + " · " + canonical,
                tone = Tone.WARN,
                action = CommandAction.SaveCommand(parsed, trimmed, isReplacement = false),
            )
        }

        return Analysis(
            suggestions = listOf(Suggestion(name + " ", parsed.label, Texts.get(R.string.cmd_kind_new_command))),
            listTitle = Texts.get(R.string.cmd_list_suggestions),
            needsContacts = false,
            previewTitle = aliasWord.uppercase() + " · " + name.uppercase(),
            preview = Texts.get(R.string.cmd_enter_to_save) + " · " + canonical,
            tone = Tone.OK,
            action = CommandAction.SaveCommand(parsed, trimmed, isReplacement = false),
        )
    }

    internal fun contextualSuggestions(arg: String, ctx: CommandContext): List<Suggestion> {
        val aliasWord = BuiltInCommand.ALIAS.word

        // 1. Placeholder autocomplete: ends with "{}" or has unclosed "{"
        if (arg.endsWith("{}")) {
            val braceIndex = arg.lastIndexOf("{}")
            val prefix = arg.substring(0, braceIndex)
            return listOf(
                Suggestion(
                    completion = "$aliasWord $prefix{text}",
                    label = "{text}  " + Texts.get(R.string.cmd_sends_text),
                    kind = Texts.get(R.string.cmd_kind_action),
                ),
            )
        }
        val braceMatch = Regex("""\{([a-zA-Z0-9_]*)$""").find(arg)
        if (braceMatch != null) {
            val prefix = arg.substring(0, braceMatch.range.first)
            val filter = braceMatch.groupValues[1].lowercase()
            val placeholders = listOf(
                "{text}" to Texts.get(R.string.cmd_sends_text),
                "{1}" to "1ª palavra",
                "{2}" to "2ª palavra",
                "{3}" to "3ª palavra",
                "{number}" to "contato → nº com DDI",
                "{name}" to "nome do contato",
                "{phone}" to "telefone do contato",
                "{date}" to "data atual",
                "{time}" to "hora atual",
                "{clip}" to Texts.get(R.string.cmd_sends_clip),
                "{location}" to Texts.get(R.string.cmd_sends_location),
                "{maps}" to "coordenadas para mapa",
            )
            val filtered = placeholders.filter { it.first.removePrefix("{").removeSuffix("}").startsWith(filter) }
            if (filtered.isNotEmpty()) {
                return filtered.map { (ph, desc) ->
                    Suggestion(
                        completion = "$aliasWord $prefix$ph",
                        label = "$ph  $desc",
                        kind = Texts.get(R.string.cmd_kind_action),
                    )
                }
            }
        }

        // 2. Action from IntentCatalog after "am start -a " or "am broadcast -a "
        val actionMatch = Regex("""\bam\s+(?:start|broadcast)\b.*-a(?:\s+([a-zA-Z0-9._]*))?$""").find(arg)
        if (actionMatch != null) {
            val aIndex = arg.lastIndexOf("-a")
            val prefix = arg.substring(0, aIndex) + "-a "
            val filter = actionMatch.groups[1]?.value?.lowercase().orEmpty()
            val entries = IntentCatalog.ENTRIES.filter {
                it.action.lowercase().contains(filter) || it.name.lowercase().contains(filter)
            }
            if (entries.isNotEmpty()) {
                return entries.map { entry ->
                    Suggestion(
                        completion = "$aliasWord $prefix${entry.action} ",
                        label = "${entry.name} (${entry.action.substringAfterLast('.')})",
                        kind = Texts.get(R.string.cmd_kind_action),
                    )
                }
            }
        }

        // 3. Extras from IntentCatalog after "--e..."
        val extraMatch = Regex("""(--e[a-z]?)(?:\s*([a-zA-Z0-9._]*))?$""").find(arg)
        if (extraMatch != null) {
            val actionName = Regex("""-a\s+([a-zA-Z0-9._]+)""").find(arg)?.groupValues?.get(1)
            val entry = if (actionName != null) IntentCatalog.find(actionName) else null
            if (entry != null && entry.extras.isNotEmpty()) {
                val prefix = arg.substring(0, extraMatch.range.first)
                val filter = extraMatch.groups[2]?.value?.lowercase().orEmpty()
                val filtered = entry.extras.filter {
                    it.key.lowercase().contains(filter) || it.description.lowercase().contains(filter)
                }
                if (filtered.isNotEmpty()) {
                    return filtered.map { extra ->
                        val flag = when (extra.type) {
                            ExtraType.STRING -> "--es"
                            ExtraType.INT -> "--ei"
                            ExtraType.BOOL -> "--ez"
                            ExtraType.LONG -> "--el"
                            ExtraType.FLOAT -> "--ef"
                            ExtraType.URI -> "--eu"
                        }
                        Suggestion(
                            completion = "$aliasWord $prefix$flag ${extra.key} ",
                            label = "$flag ${extra.key}  ${extra.description}",
                            kind = Texts.get(R.string.cmd_kind_action),
                        )
                    }
                }
            }
        }

        // 4. Packages after "-p "
        val packageMatch = Regex("""-p(?:\s+([a-zA-Z0-9._]*))?$""").find(arg)
        if (packageMatch != null) {
            val pIndex = arg.lastIndexOf("-p")
            val prefix = arg.substring(0, pIndex) + "-p "
            val filter = packageMatch.groups[1]?.value?.lowercase().orEmpty()
            val matchingApps = ctx.apps.filter {
                it.id.lowercase().contains(filter) || it.label.lowercase().contains(filter)
            }
            if (matchingApps.isNotEmpty()) {
                return matchingApps.map { app ->
                    Suggestion(
                        completion = "$aliasWord $prefix${app.id} ",
                        label = "${app.label} (${app.id})",
                        kind = Texts.get(R.string.cmd_kind_action),
                    )
                }
            }
        }

        // 5. Providers after "@"
        val providerMatch = Regex("""@([a-zA-Z0-9._]*)$""").find(arg)
        if (providerMatch != null) {
            val atIndex = arg.lastIndexOf("@")
            val prefix = arg.substring(0, atIndex) + "@"
            val filter = providerMatch.groupValues[1].lowercase()
            val matchingProviders = ctx.extras.providers.filter {
                it.id.lowercase().startsWith(filter) || it.name.lowercase().contains(filter)
            }
            if (matchingProviders.isNotEmpty()) {
                return matchingProviders.map { p ->
                    Suggestion(
                        completion = "$aliasWord $prefix${p.id}/",
                        label = "@${p.id}  ${p.name}",
                        kind = "API ›",
                    )
                }
            }
        }

        // 6. Methods after "-X "
        val methodMatch = Regex("""-X(?:\s+([a-zA-Z]*))?$""").find(arg)
        if (methodMatch != null) {
            val xIndex = arg.lastIndexOf("-X")
            val prefix = arg.substring(0, xIndex) + "-X "
            val filter = methodMatch.groups[1]?.value?.uppercase().orEmpty()
            val methods = listOf("GET", "POST", "PUT", "DELETE", "PATCH").filter { it.startsWith(filter) }
            if (methods.isNotEmpty()) {
                return methods.map { m ->
                    Suggestion(
                        completion = "$aliasWord $prefix$m ",
                        label = "-X $m",
                        kind = "API ›",
                    )
                }
            }
        }

        return emptyList()
    }
}

/**
 * "unalias [name]": removes a custom action or alias.
 */
object UnaliasCommand : BuiltIn(BuiltInCommand.UNALIAS, R.string.cmd_usage_unalias, R.string.cmd_help_unalias) {

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val trimmed = arg.trim()
        val unaliasWord = BuiltInCommand.UNALIAS.word
        if (trimmed.isEmpty()) {
            val items = ctx.custom.map { action ->
                Suggestion("$unaliasWord ${action.letter}", action.letter, Texts.get(R.string.cmd_kind_action))
            } + ctx.aliases.map { alias ->
                Suggestion("$unaliasWord ${alias.name}", alias.name, Texts.get(R.string.cmd_kind_alias))
            }
            return Analysis(
                suggestions = items.take(CommandEngine.MAX_SUGGESTIONS),
                listTitle = Texts.get(R.string.cmd_list_suggestions),
                needsContacts = false,
                previewTitle = unaliasWord.uppercase(),
                preview = Texts.get(R.string.cmd_unalias_empty),
                tone = Tone.IDLE,
                action = null,
                picksOnEnter = true,
            )
        }

        val name = trimmed.lowercase().takeWhile { !it.isWhitespace() }
        val exists = ctx.custom.any { it.letter == name } || ctx.aliases.any { it.name.lowercase() == name }

        if (!exists) {
            return Analysis(
                suggestions = emptyList(),
                listTitle = Texts.get(R.string.cmd_list_suggestions),
                needsContacts = false,
                previewTitle = unaliasWord.uppercase(),
                preview = Texts.get(R.string.cmd_unalias_not_found, name),
                tone = Tone.ERROR,
                action = null,
            )
        }

        return Analysis(
            suggestions = emptyList(),
            listTitle = Texts.get(R.string.cmd_list_suggestions),
            needsContacts = false,
            previewTitle = unaliasWord.uppercase() + " · " + name.uppercase(),
            preview = Texts.get(R.string.cmd_unalias_preview, name),
            tone = Tone.OK,
            action = CommandAction.RemoveCommand(name),
        )
    }
}
