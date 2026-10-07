package app.lawnchair.command

import android.content.Context
import app.lawnchair.util.foldAccents
import org.json.JSONArray
import org.json.JSONObject

/**
 * A word the user made up for a whole command: "mae" for "ligar maria", "zap" for "w ana". It works
 * typed in the command bar and spoken, and whatever follows it is kept: "mae" runs "ligar maria",
 * "z oi" with `z` = "w ana" runs "w ana oi".
 */
data class CommandAlias(val name: String, val expansion: String)

/** Pure rules of aliases, free of Android types so they can be unit tested. */
object Aliases {

    /** The form two spellings are compared in: no accents, lower case. "Mãe" and "mae" are the same word. */
    fun key(word: String): String =
        word.trim().foldAccents()

    /** The alias that [word] spells, if any. */
    fun find(word: String, aliases: List<CommandAlias>): CommandAlias? {
        val k = key(word)
        return if (k.isEmpty()) null else aliases.firstOrNull { key(it.name) == k }
    }

    /** [typed] with its first word replaced by the alias it names; unchanged when it names none. */
    fun resolve(typed: String, aliases: List<CommandAlias>): String {
        if (aliases.isEmpty()) return typed
        val text = typed.trimStart()
        val first = text.takeWhile { !it.isWhitespace() }
        val alias = find(first, aliases) ?: return typed
        return alias.expansion.trim() + text.substring(first.length)
    }

    /** Null when the alias can be saved, otherwise why not. [ignore] is the alias being edited. */
    fun validate(
        name: String,
        expansion: String,
        existing: List<CommandAlias>,
        custom: List<CustomAction>,
        ignore: CommandAlias? = null,
    ): String? {
        val n = name.trim()
        val k = key(n)
        return when {
            n.isEmpty() -> "Informe o apelido"
            !n.all { it.isLetterOrDigit() } -> "Use uma palavra só, com letras e números"
            k in CustomActions.RESERVED -> "\"$k\" já é um comando do sistema"
            custom.any { it.letter == k } -> "\"$k\" já é a letra de uma ação"
            existing.any { it !== ignore && key(it.name) == k } -> "\"$k\" já está em uso"
            expansion.isBlank() -> "Informe o comando"
            key(expansion.trim().takeWhile { !it.isWhitespace() }) == k -> "O comando não pode começar pelo próprio apelido"
            else -> null
        }
    }

    fun matching(prefix: String, aliases: List<CommandAlias>): List<CommandAlias> {
        val k = key(prefix)
        return if (k.isEmpty()) aliases else aliases.filter { key(it.name).startsWith(k) }
    }
}

/** Persists the user's [CommandAlias]es next to the other command bar data. */
object AliasStore {

    private const val STORE = "nostromo"
    private const val KEY = "command_aliases"

    fun load(context: Context): List<CommandAlias> {
        val raw = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return emptyList()
        return runCatching { decode(raw) }.getOrDefault(emptyList())
    }

    fun save(context: Context, aliases: List<CommandAlias>) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
            .putString(KEY, encode(aliases)).apply()
    }

    fun encode(aliases: List<CommandAlias>): String = toJson(aliases).toString()

    fun toJson(aliases: List<CommandAlias>): JSONArray = JSONArray().also { array ->
        aliases.forEach { array.put(JSONObject().put("name", it.name).put("expansion", it.expansion)) }
    }

    fun decode(raw: String): List<CommandAlias> = fromJson(JSONArray(raw))

    fun fromJson(array: JSONArray): List<CommandAlias> {
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                CommandAlias(o.getString("name"), o.getString("expansion"))
            }.getOrNull()
        }
    }
}
