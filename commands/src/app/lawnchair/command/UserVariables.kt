package app.lawnchair.command

import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents
import org.json.JSONArray
import org.json.JSONObject

/** A word the user made up for a text: `export CASA="Rua X, 123"`, then `{CASA}` in any command. */
data class UserVariable(val name: String, val value: String)

/** Pure rules of the user's variables, free of Android types so they can be unit tested. */
object UserVariables {

    const val MAX = 100
    const val MAX_NAME = 32
    const val MAX_VALUE = 1000

    /** The form two spellings are compared in: "Casa" and "casa" are the same variable. */
    fun key(name: String): String = name.trim().foldAccents()

    fun find(name: String, variables: List<UserVariable>): UserVariable? {
        val k = key(name)
        return if (k.isEmpty()) null else variables.firstOrNull { key(it.name) == k }
    }

    /** Null when [name] can be a variable, otherwise why not. */
    fun validateName(name: String): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> Texts.get(R.string.cmd_err_var_empty)

            n.length > MAX_NAME || !n[0].let { it.isLetter() || it == '_' } || !n.all { it.isLetterOrDigit() || it == '_' } ->
                Texts.get(R.string.cmd_err_var_chars, MAX_NAME)

            Variables.isBuiltInName(n) -> Texts.get(R.string.cmd_err_var_reserved, n)

            else -> null
        }
    }

    /** Null when the variable can be saved, otherwise why not. */
    fun validate(name: String, value: String, existing: List<UserVariable>): String? = validateName(name)
        ?: when {
            value.isEmpty() -> Texts.get(R.string.cmd_err_var_value_empty)
            value.length > MAX_VALUE -> Texts.get(R.string.cmd_err_var_value_long, MAX_VALUE)
            find(name, existing) == null && existing.size >= MAX -> Texts.get(R.string.cmd_err_var_many, MAX)
            else -> null
        }

    /** [variables] with [name] set to [value], replacing a variable of the same name where it was. */
    fun set(variables: List<UserVariable>, name: String, value: String): List<UserVariable> {
        val clean = UserVariable(name.trim(), value)
        val k = key(name)
        return if (variables.any { key(it.name) == k }) {
            variables.map { if (key(it.name) == k) clean else it }
        } else {
            variables + clean
        }
    }

    fun remove(variables: List<UserVariable>, name: String): List<UserVariable> {
        val k = key(name)
        return variables.filter { key(it.name) != k }
    }

    /**
     * What follows "export": `CASA=Rua X`, `CASA="Rua X, 123"` or `CASA Rua X`. The value is null
     * while only the name is typed. A quote around the whole value is dropped.
     */
    fun parse(arg: String): Pair<String, String?>? {
        val text = arg.trim()
        if (text.isEmpty()) return null
        val equals = Regex("^([^\\s=]+)\\s*=\\s*(.*)$", RegexOption.DOT_MATCHES_ALL).find(text)
        val spaced = Regex("^(\\S+)\\s+(.*)$", RegexOption.DOT_MATCHES_ALL).find(text)
        val m = equals ?: spaced ?: return text to null
        val value = unquote(m.groupValues[2].trim())
        return m.groupValues[1] to value.ifEmpty { null }
    }

    private fun unquote(value: String): String {
        if (value.length >= 2 && (value.first() == '"' && value.last() == '"' || value.first() == '\'' && value.last() == '\'')) {
            return value.substring(1, value.length - 1)
        }
        return value
    }

    /** `export NAME="value"`, the line that sets a variable again. */
    fun line(variable: UserVariable): String = BuiltInCommand.EXPORT.word + " " + variable.name + "=\"" + variable.value + "\""

    fun encode(variables: List<UserVariable>): String = JSONArray().also { array ->
        variables.forEach { array.put(JSONObject().put("name", it.name).put("value", it.value)) }
    }.toString()

    fun decode(raw: String): List<UserVariable> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            runCatching { array.getJSONObject(i).let { UserVariable(it.getString("name"), it.getString("value")) } }.getOrNull()
        }
    }.getOrDefault(emptyList())
}

/** "export NAME=value": saves a text under a name; `{NAME}` then stands for it in any command. */
object ExportCommand : BuiltIn(BuiltInCommand.EXPORT, R.string.cmd_usage_export, R.string.cmd_help_export) {

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val title = BuiltInCommand.EXPORT.word.uppercase()
        val variables = ctx.extras.variables
        val parsed = UserVariables.parse(arg)
        if (parsed == null) {
            return Analysis(
                suggestions = variables.take(CommandEngine.MAX_SUGGESTIONS * 2).map { listed(it) },
                listTitle = Texts.get(R.string.cmd_list_variables),
                needsContacts = false,
                previewTitle = title,
                preview = Texts.get(if (variables.isEmpty()) R.string.cmd_export_empty else R.string.cmd_export_hint),
                tone = Tone.IDLE,
                action = null,
                picksOnEnter = variables.isNotEmpty(),
            )
        }
        val (name, value) = parsed
        UserVariables.validateName(name)?.let { return commandResult(title, it, Tone.ERROR, null) }
        val current = UserVariables.find(name, variables)
        if (value == null) {
            // Only the name so far: shows what it holds, or asks for the value.
            val others = variables.filter { it.name.foldAccents().startsWith(name.foldAccents()) && it !== current }
            return Analysis(
                suggestions = (listOfNotNull(current) + others).take(CommandEngine.MAX_SUGGESTIONS).map { listed(it) },
                listTitle = Texts.get(R.string.cmd_list_variables),
                needsContacts = false,
                previewTitle = title + " · " + name.uppercase(),
                preview = current?.let { Texts.get(R.string.cmd_export_current, it.name, it.value) } ?: Texts.get(R.string.cmd_export_value_missing),
                tone = Tone.IDLE,
                action = null,
            )
        }
        UserVariables.validate(name, value, variables)?.let { return commandResult(title, it, Tone.ERROR, null) }
        return commandResult(
            title + " · " + name.uppercase(),
            Texts.get(if (current == null) R.string.cmd_export_ready else R.string.cmd_export_replace, name, value),
            if (current == null) Tone.OK else Tone.WARN,
            CommandAction.SetVariable(name, value),
        )
    }

    private fun listed(v: UserVariable) = Suggestion(UserVariables.line(v), "{${v.name}}  ${v.value}", Texts.get(R.string.cmd_kind_variable))
}

/** "unset NAME": forgets a variable. */
object UnsetCommand : BuiltIn(BuiltInCommand.UNSET, R.string.cmd_usage_unset, R.string.cmd_help_unset) {

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val word = BuiltInCommand.UNSET.word
        val title = word.uppercase()
        val name = arg.trim().removeSurrounding("{", "}")
        val variables = ctx.extras.variables
        if (name.isEmpty()) {
            return Analysis(
                suggestions = variables.take(CommandEngine.MAX_SUGGESTIONS).map { Suggestion("$word ${it.name}", "{${it.name}}  ${it.value}", Texts.get(R.string.cmd_kind_variable)) },
                listTitle = Texts.get(R.string.cmd_list_variables),
                needsContacts = false,
                previewTitle = title,
                preview = Texts.get(R.string.cmd_unset_empty),
                tone = Tone.IDLE,
                action = null,
                picksOnEnter = variables.isNotEmpty(),
            )
        }
        val found = UserVariables.find(name, variables) ?: return commandResult(title, Texts.get(R.string.cmd_unset_not_found, name), Tone.ERROR, null)
        return commandResult(title + " · " + found.name.uppercase(), Texts.get(R.string.cmd_unset_ready, found.name), Tone.OK, CommandAction.UnsetVariable(found.name))
    }
}
