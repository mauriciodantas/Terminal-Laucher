package app.lawnchair.command

import org.json.JSONArray
import org.json.JSONObject

sealed interface JsonImportTarget {
    data class Commands(val pack: CommandPack, val rawJson: String) : JsonImportTarget
    data class Invalid(val incomplete: Boolean = false) : JsonImportTarget
}

object JsonImport {

    /**
     * [text] with the spaces JSON does not take made plain: copied from a chat on the phone, the
     * indentation often comes as no-break spaces, and a BOM or zero-width marks come along.
     */
    fun clean(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF' -> Unit
                else -> append(if (c != '\n' && Character.isSpaceChar(c)) ' ' else c)
            }
        }
    }

    /** True when [text] looks like it starts a JSON object or array. */
    fun isPotentialJson(text: String): Boolean {
        val t = clean(text).trim()
        return t.startsWith("{") || t.startsWith("[")
    }

    /**
     * Tries to classify [text] into a commands pack, or into what only the app has (see [CommandHost]).
     * Returns null if [text] does not start with JSON notation.
     */
    fun classify(text: String): JsonImportTarget? {
        val trimmed = clean(text).trim()
        if (!isPotentialJson(trimmed)) return null

        val (obj, array) = try {
            if (trimmed.startsWith("{")) {
                JSONObject(trimmed) to null
            } else {
                null to JSONArray(trimmed)
            }
        } catch (_: Exception) {
            val incomplete = !(
                (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                    (trimmed.startsWith("[") && trimmed.endsWith("]"))
                )
            return JsonImportTarget.Invalid(incomplete = incomplete)
        }

        if (obj != null) {
            return classifyObject(obj, trimmed)
        }
        if (array != null) {
            return classifyArray(array, trimmed)
        }
        return JsonImportTarget.Invalid()
    }

    private fun classifyObject(o: JSONObject, raw: String): JsonImportTarget {
        val format = o.optString("format")

        // What only the app has (a theme, a widget) comes first: its formats are explicit.
        if (format != CommandPacks.FORMAT) AppHost.classifyObject(o, raw, format)?.let { return it }
        if (format == CommandPacks.FORMAT) {
            val pack = runCatching { CommandPacks.decode(raw) }.getOrNull()
            if (pack != null && !pack.isEmpty) {
                return JsonImportTarget.Commands(pack, raw)
            }
        }

        // Commands (pack, actions, aliases, providers)
        val hasCommandPackProps = o.has("actions") || o.has("aliases") || o.has("providers")
        if (hasCommandPackProps) {
            val actions = o.optJSONArray("actions")?.let { CustomActionStore.fromJson(it) }.orEmpty()
            val aliases = o.optJSONArray("aliases")?.let { AliasStore.fromJson(it) }.orEmpty()
            val providers = o.optJSONArray("providers")?.let { ProviderCodec.fromJson(it) }.orEmpty()
            if (actions.isNotEmpty() || aliases.isNotEmpty() || providers.isNotEmpty()) {
                return JsonImportTarget.Commands(CommandPack(actions, aliases, 0, providers), raw)
            }
        }
        // Single CustomAction
        if (o.has("letter") && (o.has("label") || o.has("template") || o.has("kind") || o.has("action"))) {
            val action = runCatching {
                CustomActionStore.fromJson(JSONArray().put(o)).firstOrNull()
            }.getOrNull()
            if (action != null) {
                return JsonImportTarget.Commands(CommandPack(listOf(action), emptyList()), raw)
            }
        }
        // Single CommandAlias
        if (o.has("name") && o.has("expansion")) {
            val name = o.optString("name").trim()
            val expansion = o.optString("expansion").trim()
            if (name.isNotEmpty() && expansion.isNotEmpty()) {
                return JsonImportTarget.Commands(CommandPack(emptyList(), listOf(CommandAlias(name, expansion))), raw)
            }
        }

        return JsonImportTarget.Invalid()
    }

    private fun classifyArray(array: JSONArray, raw: String): JsonImportTarget {
        val actions = runCatching { CustomActionStore.fromJson(array) }.getOrNull().orEmpty()
        if (actions.isNotEmpty()) {
            return JsonImportTarget.Commands(CommandPack(actions, emptyList()), raw)
        }

        val aliases = runCatching { AliasStore.fromJson(array) }.getOrNull().orEmpty()
        if (aliases.isNotEmpty()) {
            return JsonImportTarget.Commands(CommandPack(emptyList(), aliases), raw)
        }

        AppHost.classifyArray(array, raw)?.let { return it }

        return JsonImportTarget.Invalid()
    }
}
