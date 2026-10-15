package app.lawnchair.command

import org.json.JSONArray
import org.json.JSONObject

sealed interface JsonImportTarget {
    data class Theme(val spec: ThemeSpec, val rawJson: String) : JsonImportTarget
    data class Widget(val name: String, val rawJson: String) : JsonImportTarget
    data class Commands(val pack: CommandPack, val rawJson: String) : JsonImportTarget
    data class Invalid(val incomplete: Boolean = false) : JsonImportTarget
}

object JsonImport {

    /** True when [text] looks like it starts a JSON object or array. */
    fun isPotentialJson(text: String): Boolean {
        val t = text.trim()
        return t.startsWith("{") || t.startsWith("[")
    }

    /**
     * Tries to classify [text] into a theme, widget, or commands pack.
     * Returns null if [text] does not start with JSON notation.
     */
    fun classify(text: String): JsonImportTarget? {
        val trimmed = text.trim()
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

        // 1. Explicit formats
        if (format == ThemeSpecs.FORMAT) {
            val spec = runCatching { ThemeSpecs.decode(raw) }.getOrNull()
                ?: ThemeSpecs.fromJson(o)
            return JsonImportTarget.Theme(spec, raw)
        }
        if (format == "terminal-widget") {
            val name = o.optString("name").ifBlank { "widget.json" }
            return JsonImportTarget.Widget(name, raw)
        }
        if (format == CommandPacks.FORMAT) {
            val pack = runCatching { CommandPacks.decode(raw) }.getOrNull()
            if (pack != null && !pack.isEmpty) {
                return JsonImportTarget.Commands(pack, raw)
            }
        }

        // 2. Widget (Lottie animation or widget with animation)
        val isLottie = o.has("layers") && (o.has("v") || (o.has("ip") && o.has("op")) || o.has("fr"))
        if (isLottie) {
            val name = o.optString("nm").ifBlank { o.optString("name").ifBlank { "widget.json" } }
            return JsonImportTarget.Widget(name, raw)
        }
        if (o.has("animation") && (o.optJSONObject("animation") != null || o.optString("animation").isNotBlank())) {
            val name = o.optString("name").ifBlank { "widget.json" }
            return JsonImportTarget.Widget(name, raw)
        }

        // 3. Theme
        if (o.has("theme")) {
            val themeObj = o.optJSONObject("theme") ?: o
            val spec = runCatching { ThemeSpecs.fromJson(themeObj) }.getOrNull()
            if (spec != null && !spec.isEmpty) {
                return JsonImportTarget.Theme(spec, raw)
            }
        }
        val hasThemeProps = listOf(
            "accent", "background", "secondary", "outline", "dock", "folder",
            "followSystem", "pixelClock", "iconEffect", "widgetEffect", "crtEffect",
            "showWallpaper", "colorAsWallpaper", "wallpaper", "lockWallpaper",
        ).any { o.has(it) }
        if (hasThemeProps) {
            val spec = runCatching { ThemeSpecs.fromJson(o) }.getOrNull()
            if (spec != null && !spec.isEmpty) {
                return JsonImportTarget.Theme(spec, raw)
            }
        }

        // 4. Commands (pack, actions, aliases, providers)
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

        val themes = runCatching { ThemeSpecs.decodeList(raw) }.getOrNull().orEmpty()
        if (themes.isNotEmpty()) {
            return JsonImportTarget.Theme(themes.first(), raw)
        }

        return JsonImportTarget.Invalid()
    }
}
