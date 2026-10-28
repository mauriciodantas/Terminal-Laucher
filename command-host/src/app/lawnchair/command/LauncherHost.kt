package app.lawnchair.command

import app.lawnchair.util.Texts
import org.json.JSONArray
import org.json.JSONObject

/** What the launcher gives the commands besides what every app does (see [CommandExtras.host]). */
data class HostData(
    /** The theme on screen now, every element set; null when unknown. */
    val theme: ThemeSpec? = null,
    /** The themes the user saved, by name. */
    val themes: List<ThemeSpec> = emptyList(),
    /** The ready-made looks of Appearance, by name. */
    val themePresets: List<ThemeSpec> = emptyList(),
    /** Google Fonts families "tema fonte" can pick; empty until the list loads. */
    val fonts: List<String> = emptyList(),
)

val CommandExtras.theme: ThemeSpec? get() = host.theme
val CommandExtras.themes: List<ThemeSpec> get() = host.themes
val CommandExtras.themePresets: List<ThemeSpec> get() = host.themePresets
val CommandExtras.fonts: List<String> get() = host.fonts

/** What the launcher tells the AI besides what every app does (see [AiContext.host]). */
data class HostAiData(
    /** The drawer folders by name with the names of their apps, for [AiMode.FOLDERS]. */
    val folders: List<Pair<String, List<String>>> = emptyList(),
    /** "ia pastas --force": apps already in folders may be moved too. */
    val forceFolders: Boolean = false,
    /** The theme on screen now, for [AiMode.THEME] to start from. */
    val theme: ThemeSpec? = null,
)

/** What "tema" asks the launcher to do. */
sealed interface ThemeAction : CommandAction {
    /** Inverts the theme colors, lays them light or dark, or makes them follow the system mode. */
    data class Change(val change: ThemeChange) : ThemeAction

    /** Sets what [spec] has: one element ("tema fundo #000"), a saved theme or a ready-made look. */
    data class Apply(val spec: ThemeSpec) : ThemeAction

    /** Keeps the theme on screen now under [name]. */
    data class Save(val name: String) : ThemeAction

    /** Sends the theme called [name] as a file; an empty name sends the one on screen. */
    data class Share(val name: String) : ThemeAction
    data class Delete(val name: String) : ThemeAction
}

/** What a pasted theme or widget asks the launcher to do. */
sealed interface ImportAction : CommandAction {
    data class Theme(val spec: ThemeSpec, val json: String) : ImportAction
    data class Widget(val json: String, val name: String) : ImportAction
}

/** The JSON a pasted text can be besides commands: a theme or a widget. */
sealed interface LauncherImport : JsonImportTarget {
    data class Theme(val spec: ThemeSpec, val rawJson: String) : LauncherImport
    data class Widget(val name: String, val rawJson: String) : LauncherImport
}

/** The Terminal launcher around the engine: themes, widgets and the drawer's folders. */
object AppHost : CommandHost {

    override val aiIntro = "the command bar of Terminal, an Android launcher"

    override val commands: List<Command> = listOf(ThemeCommand)

    override val aiModes = listOf(AiMode.FOLDERS, AiMode.THEME)

    override fun aiAnalyze(mode: AiMode, prompt: String, heading: String, offers: List<Suggestion>): Analysis? = when (mode) {
        AiMode.THEME ->
            // A theme needs no request either: Enter alone asks for a surprise.
            commandResult(heading, prompt.ifEmpty { Texts.get(R.string.cmd_ai_theme_empty) }, Tone.OK, CommandAction.Ai(mode, prompt), suggestions = offers)

        AiMode.FOLDERS -> {
            // Folders need no request: Enter alone sorts the apps the AI's way.
            val (request, force) = AiFolders.splitForce(prompt)
            val hint = request.ifEmpty { Texts.get(R.string.cmd_ai_folders_empty) }
            val text = if (force) hint + " · " + Texts.get(R.string.cmd_ai_folders_force) else hint
            commandResult(heading, text, if (force) Tone.WARN else Tone.OK, CommandAction.Ai(mode, request, force), suggestions = offers)
        }

        else -> null
    }

    override fun aiSystem(mode: AiMode, context: AiContext): String? = when (mode) {
        AiMode.FOLDERS -> AiPrompts.base(context) + "\n\n" + LauncherAiPrompts.drawer(context) + "\n\n" +
            if (context.host.forceFolders) LauncherAiPrompts.FOLDERS_FORCE_RULES else LauncherAiPrompts.FOLDERS_RULES

        AiMode.THEME -> AiPrompts.base(context) + "\n\n" + LauncherAiPrompts.currentTheme(context) + LauncherAiPrompts.THEME_RULES

        else -> null
    }

    override val aiCreateTail = LauncherAiPrompts.CREATE_TAIL

    override fun classifyObject(o: JSONObject, raw: String, format: String): JsonImportTarget? {
        // Explicit formats
        if (format == ThemeSpecs.FORMAT) {
            val spec = runCatching { ThemeSpecs.decode(raw) }.getOrNull() ?: ThemeSpecs.fromJson(o)
            return LauncherImport.Theme(spec, raw)
        }
        if (format == "terminal-widget") {
            return LauncherImport.Widget(o.optString("name").ifBlank { "widget.json" }, raw)
        }
        // Widget (Lottie animation or widget with animation)
        val isLottie = o.has("layers") && (o.has("v") || (o.has("ip") && o.has("op")) || o.has("fr"))
        if (isLottie) {
            return LauncherImport.Widget(o.optString("nm").ifBlank { o.optString("name").ifBlank { "widget.json" } }, raw)
        }
        // A Lottie widget carries an animation of its own; a theme's "animation" is the name of one the launcher plays.
        val animation = o.opt("animation")
        val themeAnimation = animation is String && (ThemeSpecs.animationKey(animation) != null || ThemeSpecs.animationIsOff(animation))
        if (!themeAnimation && o.has("animation") && (o.optJSONObject("animation") != null || o.optString("animation").isNotBlank())) {
            return LauncherImport.Widget(o.optString("name").ifBlank { "widget.json" }, raw)
        }
        // Theme
        if (o.has("theme")) {
            val themeObj = o.optJSONObject("theme") ?: o
            val spec = runCatching { ThemeSpecs.fromJson(themeObj) }.getOrNull()
            if (spec != null && !spec.isEmpty) return LauncherImport.Theme(spec, raw)
        }
        val hasThemeProps = listOf(
            "accent", "background", "secondary", "outline", "dock", "folder",
            "followSystem", "pixelClock", "iconEffect", "widgetEffect", "crtEffect", "animation", "customAnimation",
            "showWallpaper", "colorAsWallpaper", "wallpaper", "lockWallpaper",
        ).any { o.has(it) }
        if (hasThemeProps) {
            val spec = runCatching { ThemeSpecs.fromJson(o) }.getOrNull()
            if (spec != null && !spec.isEmpty) return LauncherImport.Theme(spec, raw)
        }
        return null
    }

    override fun classifyArray(array: JSONArray, raw: String): JsonImportTarget? {
        val themes = runCatching { ThemeSpecs.decodeList(raw) }.getOrNull().orEmpty()
        return themes.firstOrNull()?.let { LauncherImport.Theme(it, raw) }
    }

    override fun importAnalysis(target: JsonImportTarget, arg: String): Analysis? = when (target) {
        is LauncherImport.Theme -> {
            val name = target.spec.name.ifBlank { "tema" }
            Analysis(
                suggestions = listOf(Suggestion(arg.trim(), Texts.get(R.string.cmd_import_action_theme), Texts.get(R.string.cmd_kind_import))),
                listTitle = Texts.get(R.string.cmd_list_suggestions),
                needsContacts = false,
                previewTitle = Texts.get(R.string.cmd_import_theme_title),
                preview = Texts.get(R.string.cmd_import_theme_preview, name),
                tone = Tone.OK,
                action = ImportAction.Theme(target.spec, target.rawJson),
            )
        }

        is LauncherImport.Widget -> Analysis(
            suggestions = listOf(Suggestion(arg.trim(), Texts.get(R.string.cmd_import_action_widget), Texts.get(R.string.cmd_kind_import))),
            listTitle = Texts.get(R.string.cmd_list_suggestions),
            needsContacts = false,
            previewTitle = Texts.get(R.string.cmd_import_widget_title),
            preview = Texts.get(R.string.cmd_import_widget_preview, target.name),
            tone = Tone.OK,
            action = ImportAction.Widget(target.rawJson, target.name),
        )

        else -> null
    }

    override fun describe(action: CommandAction): String? = when (action) {
        is ThemeAction.Change -> when (action.change) {
            ThemeChange.INVERT -> "accent ⇄ background"
            ThemeChange.LIGHT -> "background = lighter"
            ThemeChange.DARK -> "background = darker"
            ThemeChange.SYSTEM -> "follow_system_color_mode = true"
            ThemeChange.RESET -> "appearance = default"
        }

        is ThemeAction.Apply -> ThemeCommand.describe(action.spec)

        is ThemeAction.Save -> "themes += \"${action.name}\""

        is ThemeAction.Share -> "ACTION_SEND " + ThemeSpecs.MIME

        is ThemeAction.Delete -> "themes -= \"${action.name}\""

        is ImportAction.Theme -> "ImportThemeActivity · " + action.spec.name.ifBlank { "tema" }

        is ImportAction.Widget -> "ImportWidgetActivity · " + action.name

        else -> null
    }
}
