package app.lawnchair.command

import androidx.annotation.StringRes
import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents

/** What an element of the theme takes after its word. */
enum class ThemeValue { COLOR, FONT, SWITCH, EFFECT, PERCENT, SHAPE, ANIMATION }

/**
 * Each part of the theme the command sets on its own: "tema fundo #000000", "tema fonte Space Mono",
 * "tema crt 40%". Like the command words, each is typed with the word of the app language, and the
 * Portuguese and English words always work too.
 */
enum class ThemeElement(
    @StringRes private val wordRes: Int,
    val portuguese: String,
    val english: String,
    @StringRes private val descRes: Int,
    val value: ThemeValue,
) {
    ACCENT(R.string.cmd_theme_el_accent, "destaque", "accent", R.string.cmd_theme_el_accent_desc, ThemeValue.COLOR),
    BACKGROUND(R.string.cmd_theme_el_background, "fundo", "background", R.string.cmd_theme_el_background_desc, ThemeValue.COLOR),
    SECONDARY(R.string.cmd_theme_el_secondary, "secundaria", "secondary", R.string.cmd_theme_el_secondary_desc, ThemeValue.COLOR),
    OUTLINE(R.string.cmd_theme_el_outline, "contorno", "outline", R.string.cmd_theme_el_outline_desc, ThemeValue.COLOR),
    DOCK(R.string.cmd_theme_el_dock, "dock", "dock", R.string.cmd_theme_el_dock_desc, ThemeValue.COLOR),
    FOLDER(R.string.cmd_theme_el_folder, "pasta", "folder", R.string.cmd_theme_el_folder_desc, ThemeValue.COLOR),
    FONT(R.string.cmd_theme_el_font, "fonte", "font", R.string.cmd_theme_el_font_desc, ThemeValue.FONT),
    CLOCK(R.string.cmd_theme_el_clock, "relogio", "clock", R.string.cmd_theme_el_clock_desc, ThemeValue.SWITCH),
    ICONS(R.string.cmd_theme_el_icons, "icones", "icons", R.string.cmd_theme_el_icons_desc, ThemeValue.SWITCH),
    WIDGETS(R.string.cmd_theme_el_widgets, "widgets", "widgets", R.string.cmd_theme_el_widgets_desc, ThemeValue.EFFECT),
    CRT(R.string.cmd_theme_el_crt, "crt", "crt", R.string.cmd_theme_el_crt_desc, ThemeValue.EFFECT),
    ANIMATION(R.string.cmd_theme_el_animation, "animacao", "animation", R.string.cmd_theme_el_animation_desc, ThemeValue.ANIMATION),
    WALLPAPER(R.string.cmd_theme_el_wallpaper, "papel", "wallpaper", R.string.cmd_theme_el_wallpaper_desc, ThemeValue.SWITCH),
    DIM(R.string.cmd_theme_el_dim, "escurecer", "dim", R.string.cmd_theme_el_dim_desc, ThemeValue.PERCENT),
    SHAPE(R.string.cmd_theme_el_shape, "formato", "shape", R.string.cmd_theme_el_shape_desc, ThemeValue.SHAPE),
    FOLDER_SHAPE(R.string.cmd_theme_el_foldershape, "formatopasta", "foldershape", R.string.cmd_theme_el_foldershape_desc, ThemeValue.SHAPE),
    PAINT(R.string.cmd_theme_el_paint, "pintar", "paint", R.string.cmd_theme_el_paint_desc, ThemeValue.SWITCH),
    ;

    val word: String get() = Texts.get(wordRes)

    /** What the element is, as the suggestion label and the preview title. */
    val desc: String get() = Texts.get(descRes)

    fun words(): Set<String> = setOf(word, portuguese, english).map { it.foldAccents() }.toSet()

    /** The element's value in [spec], as a short text; null when the theme leaves it alone. */
    fun valueIn(spec: ThemeSpec): String? = when (this) {
        ACCENT -> spec.accent?.let(ThemeCommand::colorText)
        BACKGROUND -> spec.background?.let(ThemeCommand::colorText)
        SECONDARY -> spec.secondary?.let(ThemeCommand::colorText)
        OUTLINE -> spec.outline?.let(ThemeCommand::colorText)
        DOCK -> spec.dock?.let(ThemeCommand::colorText)
        FOLDER -> spec.folder?.let(ThemeCommand::colorText)
        FONT -> spec.font?.label
        CLOCK -> spec.pixelClock?.let(ThemeCommand::stateText)
        ICONS -> spec.iconEffect?.let(ThemeCommand::stateText)
        WIDGETS -> ThemeCommand.effectText(spec.widgetEffect, spec.widgetIntensity)
        CRT -> ThemeCommand.effectText(spec.crtEffect, spec.crtIntensity)
        ANIMATION -> spec.animation ?: spec.crtEffect?.takeIf { !it }?.let { ThemeCommand.NO_ANIMATION }
        WALLPAPER -> spec.showWallpaper?.let(ThemeCommand::stateText)
        DIM -> spec.wallpaperDim?.let(ThemeCommand::percentText)
        PAINT -> spec.colorAsWallpaper?.let(ThemeCommand::stateText)
        SHAPE -> spec.iconShape
        FOLDER_SHAPE -> spec.folderShape
    }

    companion object {
        fun of(word: String): ThemeElement? = entries.firstOrNull { word in it.words() }
    }
}

/** What "tema" does with a whole theme instead of one of its parts. */
enum class ThemeVerb(
    @StringRes private val wordRes: Int,
    val portuguese: String,
    val english: String,
    @StringRes private val descRes: Int,
) {
    SAVE(R.string.cmd_theme_save, "salvar", "save", R.string.cmd_theme_save_desc),
    SHARE(R.string.cmd_theme_share, "compartilhar", "share", R.string.cmd_theme_share_desc),
    DELETE(R.string.cmd_theme_delete, "apagar", "delete", R.string.cmd_theme_delete_desc),
    ;

    val word: String get() = Texts.get(wordRes)
    val desc: String get() = Texts.get(descRes)

    fun words(): Set<String> = setOf(word, portuguese, english).map { it.foldAccents() }.toSet()

    companion object {
        fun of(word: String): ThemeVerb? = entries.firstOrNull { word in it.words() }
    }
}

/**
 * The "tema" command: the modes ("tema escuro"), each element on its own ("tema destaque #33ff66"),
 * a whole theme by name (the user's saved ones and the ready-made looks of Appearance), and saving,
 * sharing and deleting themes. Pure, so it can be unit tested.
 */
object ThemeCommand : BuiltIn(BuiltInCommand.THEME, null, R.string.cmd_help_theme) {

    override fun usages(): List<Pair<String, String>> = listOf(word to word + " " + ThemeChange.usage())

    override fun analyze(arg: String, ctx: CommandContext): Analysis = analyze(arg, ctx.extras)

    /** Families of the system fonts that every Android has. */
    val SYSTEM_FONTS = listOf("sans-serif", "sans-serif-condensed", "sans-serif-light", "serif", "monospace", "cursive", "casual")

    fun analyze(arg: String, extras: CommandExtras): Analysis {
        val text = arg.trim()
        val first = text.takeWhile { !it.isWhitespace() }
        val rest = text.substring(first.length).trim()
        val word = first.foldAccents()

        if (text.isEmpty()) {
            return result(Texts.get(R.string.cmd_theme_title), Texts.get(R.string.cmd_theme_empty), Tone.IDLE, null, options(extras))
        }
        // A theme by name comes first: "tema noite" may be called like an element or a mode.
        named(text, extras)?.let { (spec, builtIn) ->
            if (rest.isNotEmpty() || (ThemeChange.of(word) == null && ThemeElement.of(word) == null && ThemeVerb.of(word) == null)) {
                return apply(spec, builtIn, extras)
            }
        }
        ThemeChange.of(word)?.takeIf { rest.isEmpty() }?.let { change ->
            return result(Texts.get(R.string.cmd_theme_title), change.preview, Tone.OK, ThemeAction.Change(change))
        }
        ThemeElement.of(word)?.let { return element(it, rest, extras) }
        ThemeVerb.of(word)?.let { return verb(it, rest, extras) }

        // Still typing: every word and name that starts with what is there.
        val matches = options(extras).filter { it.completion.substringAfter(' ').foldAccents().startsWith(text.foldAccents()) }
        return result(
            Texts.get(R.string.cmd_theme_title),
            if (matches.isEmpty()) Texts.get(R.string.cmd_theme_not_found) else Texts.get(R.string.cmd_theme_empty),
            if (matches.isEmpty()) Tone.ERROR else Tone.IDLE,
            null,
            matches,
        )
    }

    /** The modes, the elements, the verbs and every theme by name, in that order. */
    private fun options(extras: CommandExtras): List<Suggestion> {
        val word = BuiltInCommand.THEME.word
        val option = Texts.get(R.string.cmd_kind_option)
        return ThemeChange.entries.map { Suggestion("$word ${it.word}", it.preview, option) } +
            ThemeElement.entries.map { Suggestion("$word ${it.word} ", it.desc, option) } +
            ThemeVerb.entries.map { Suggestion("$word ${it.word} ", it.desc, option) } +
            extras.themes.map { Suggestion("$word ${it.name}", summary(it), Texts.get(R.string.cmd_theme_kind_saved)) } +
            extras.themePresets.map { Suggestion("$word ${it.name}", summary(it), Texts.get(R.string.cmd_theme_kind_preset)) }
    }

    /** The saved theme or the ready-made look called [name], with whether it is a ready-made one. */
    fun named(name: String, extras: CommandExtras): Pair<ThemeSpec, Boolean>? {
        val key = name.trim().foldAccents()
        if (key.isEmpty()) return null
        extras.themes.firstOrNull { it.name.foldAccents() == key }?.let { return it to false }
        extras.themePresets.firstOrNull { it.name.foldAccents() == key }?.let { return it to true }
        return null
    }

    private fun apply(spec: ThemeSpec, builtIn: Boolean, extras: CommandExtras): Analysis {
        val title = Texts.get(R.string.cmd_theme_title) + " · " + spec.name.uppercase()
        val preview = Texts.get(R.string.cmd_theme_apply_preview, summary(spec))
        if (ThemeSpecs.unreadable(spec, extras.theme)) return result(title, Texts.get(R.string.cmd_theme_unreadable), Tone.ERROR, null)
        val tone = if (ThemeSpecs.weak(spec, extras.theme)) Tone.WARN else Tone.OK
        // A ready-made look says so; the user's own is just its name.
        val heading = if (builtIn) title + " · " + Texts.get(R.string.cmd_theme_kind_preset) else title
        return result(heading, preview + weakNote(spec, extras), tone, ThemeAction.Apply(spec))
    }

    // ---- one element ----

    private fun element(element: ThemeElement, rest: String, extras: CommandExtras): Analysis {
        val title = Texts.get(R.string.cmd_theme_title) + " · " + element.desc
        val current = extras.theme?.let(element::valueIn)
        return when (element.value) {
            ThemeValue.COLOR -> color(element, rest, title, current, extras)
            ThemeValue.FONT -> font(rest, title, current, extras)
            ThemeValue.SWITCH, ThemeValue.EFFECT -> switch(element, rest, title, current, extras)
            ThemeValue.PERCENT -> percent(element, rest, title, current)
            ThemeValue.SHAPE -> shape(element, rest, title, current)
            ThemeValue.ANIMATION -> animation(rest, title, current)
        }
    }

    /** "tema formato hexagon": the shape icons (or folders) are cut to, picked from the known ones. */
    private fun shape(element: ThemeElement, rest: String, title: String, current: String?): Analysis {
        val word = BuiltInCommand.THEME.word + " " + element.word
        val typed = rest.foldAccents().filter { it.isLetterOrDigit() }
        val suggestions = ThemeSpecs.ICON_SHAPES.filter { typed.isEmpty() || it.lowercase().startsWith(typed) }
            .take(CommandEngine.MAX_SUGGESTIONS * 3)
            .map { Suggestion("$word $it", it, Texts.get(R.string.cmd_kind_option)) }
        val key = ThemeSpecs.shapeKey(rest)
        if (key == null) {
            val hint = Texts.get(R.string.cmd_theme_shape_hint) + currentLine(current)
            return result(title, hint, if (rest.isEmpty()) Tone.IDLE else Tone.ERROR, null, suggestions)
        }
        val spec = if (element == ThemeElement.SHAPE) ThemeSpec(iconShape = key) else ThemeSpec(folderShape = key)
        return result(title, "→ $key", Tone.OK, ThemeAction.Apply(spec), suggestions.filter { it.label != key })
    }

    /** The word that turns the animation off. */
    const val NO_ANIMATION = "none"

    /** "tema animacao snow": which animation plays over the screen, picked from the known ones, or none. */
    private fun animation(rest: String, title: String, current: String?): Analysis {
        val word = BuiltInCommand.THEME.word + " " + ThemeElement.ANIMATION.word
        val typed = rest.foldAccents().trim()
        val suggestions = (ThemeSpecs.ANIMATIONS + NO_ANIMATION).filter { typed.isEmpty() || it.startsWith(typed) }
            .map { Suggestion("$word $it", it, Texts.get(R.string.cmd_kind_option)) }
        val spec = when {
            typed == NO_ANIMATION -> ThemeSpec(crtEffect = false)
            else -> ThemeSpecs.animationKey(typed)?.let { ThemeSpec(crtEffect = true, animation = it) }
        }
        if (spec == null) {
            val hint = Texts.get(R.string.cmd_theme_animation_hint) + currentLine(current)
            return result(title, hint, if (rest.isEmpty()) Tone.IDLE else Tone.ERROR, null, suggestions)
        }
        return result(title, "→ " + (spec.animation ?: NO_ANIMATION), Tone.OK, ThemeAction.Apply(spec), suggestions.filter { it.label != typed })
    }

    private fun currentLine(current: String?): String = current?.let { "\n" + Texts.get(R.string.cmd_theme_now, it) }.orEmpty()

    private fun color(element: ThemeElement, rest: String, title: String, current: String?, extras: CommandExtras): Analysis {
        val word = BuiltInCommand.THEME.word + " " + element.word
        val reset = ThemeChange.RESET
        if (rest.isEmpty()) {
            return result(
                title,
                Texts.get(R.string.cmd_theme_color_hint) + currentLine(current),
                Tone.IDLE,
                null,
                listOf(Suggestion("$word ${reset.word}", reset.word.uppercase(), Texts.get(R.string.cmd_kind_option))),
                listTitle = Texts.get(R.string.cmd_list_format),
            )
        }
        val color = (if (rest.foldAccents() in reset.words()) ThemeColor.Default else ThemeSpecs.parseColor(rest))
            ?: return result(title, Texts.get(R.string.cmd_theme_color_invalid), Tone.ERROR, null)
        val spec = when (element) {
            ThemeElement.ACCENT -> ThemeSpec(accent = color)
            ThemeElement.BACKGROUND -> ThemeSpec(background = color)
            ThemeElement.SECONDARY -> ThemeSpec(secondary = color)
            ThemeElement.OUTLINE -> ThemeSpec(outline = color)
            ThemeElement.DOCK -> ThemeSpec(dock = color)
            else -> ThemeSpec(folder = color)
        }
        if (ThemeSpecs.unreadable(spec, extras.theme)) return result(title, Texts.get(R.string.cmd_theme_unreadable), Tone.ERROR, null)
        val tone = if (ThemeSpecs.weak(spec, extras.theme)) Tone.WARN else Tone.OK
        return result(title, "→ " + colorText(color) + weakNote(spec, extras), tone, ThemeAction.Apply(spec))
    }

    private fun font(rest: String, title: String, current: String?, extras: CommandExtras): Analysis {
        val word = BuiltInCommand.THEME.word + " " + ThemeElement.FONT.word
        val typed = rest.foldAccents()
        val reset = ThemeChange.RESET
        val families = SYSTEM_FONTS + extras.fonts
        val matches = (listOf(reset.word) + families).filter { it.foldAccents().startsWith(typed) }
        val suggestions = matches.take(CommandEngine.MAX_SUGGESTIONS).map {
            Suggestion("$word $it", it, Texts.get(R.string.cmd_kind_option))
        }
        if (rest.isEmpty()) {
            return result(title, Texts.get(R.string.cmd_theme_font_hint) + currentLine(current), Tone.IDLE, null, suggestions)
        }
        val font = when {
            typed in reset.words() -> ThemeFont.Default

            else -> SYSTEM_FONTS.firstOrNull { it.foldAccents() == typed }?.let { ThemeFont.System(it) }
                ?: extras.fonts.firstOrNull { it.foldAccents() == typed }?.let { ThemeFont.Google(it) }
                // Before the list of Google Fonts loads, any name may be one.
                ?: if (extras.fonts.isEmpty()) ThemeSpecs.fontNamed(rest) else null
        }
        if (font == null) {
            return result(
                title,
                Texts.get(R.string.cmd_theme_font_unknown),
                if (matches.isEmpty()) Tone.ERROR else Tone.IDLE,
                null,
                suggestions,
            )
        }
        return result(title, "→ " + font.label, Tone.OK, ThemeAction.Apply(ThemeSpec(font = font)), suggestions.filter { it.label != font.label })
    }

    private fun switch(element: ThemeElement, rest: String, title: String, current: String?, extras: CommandExtras): Analysis {
        val word = BuiltInCommand.THEME.word + " " + element.word
        val on = Texts.get(R.string.cmd_state_on).split(',').first().trim()
        val off = Texts.get(R.string.cmd_state_off).split(',').first().trim()
        val suggestions = listOf(on, off).map { Suggestion("$word $it", it.uppercase(), Texts.get(R.string.cmd_kind_option)) }
            .filter { rest.isEmpty() || it.completion.foldAccents().startsWith("$word $rest".foldAccents()) }
        val state = ToggleCommand.state(rest)
        val percent = if (element.value == ThemeValue.EFFECT && state == null) percentOf(rest) else null
        val spec = when {
            percent != null -> intensity(element, percent / 100f)

            state != null -> switched(element, state)

            // Alone, it flips what is on screen now.
            rest.isEmpty() -> isOn(element, extras.theme)?.let { switched(element, !it) }

            else -> null
        }
        if (spec == null) {
            val hint = if (element.value == ThemeValue.EFFECT) R.string.cmd_theme_effect_hint else R.string.cmd_state_invalid
            return result(title, Texts.get(hint) + currentLine(current), if (rest.isEmpty()) Tone.IDLE else Tone.ERROR, null, suggestions)
        }
        return result(title, "→ " + element.valueIn(spec).orEmpty(), Tone.OK, ThemeAction.Apply(spec), suggestions)
    }

    private fun percent(element: ThemeElement, rest: String, title: String, current: String?): Analysis {
        if (rest.isEmpty()) return result(title, Texts.get(R.string.cmd_theme_percent_hint) + currentLine(current), Tone.IDLE, null)
        val percent = percentOf(rest) ?: return result(title, Texts.get(R.string.cmd_percent_invalid), Tone.ERROR, null)
        val spec = ThemeSpec(wallpaperDim = percent / 100f)
        return result(title, "→ " + element.valueIn(spec).orEmpty(), Tone.OK, ThemeAction.Apply(spec))
    }

    private fun percentOf(text: String): Int? = text.trim().removeSuffix("%").trim().toIntOrNull()?.takeIf { it in 0..100 }

    private fun switched(element: ThemeElement, on: Boolean): ThemeSpec = when (element) {
        ThemeElement.CLOCK -> ThemeSpec(pixelClock = on)
        ThemeElement.ICONS -> ThemeSpec(iconEffect = on)
        ThemeElement.WIDGETS -> ThemeSpec(widgetEffect = on)
        ThemeElement.CRT -> ThemeSpec(crtEffect = on)
        ThemeElement.WALLPAPER -> ThemeSpec(showWallpaper = on)
        else -> ThemeSpec(colorAsWallpaper = on)
    }

    /** "tema crt 40%": the intensity, and the effect turned on (0% turns it off). */
    private fun intensity(element: ThemeElement, value: Float): ThemeSpec {
        val on = value > 0f
        val level = value.coerceAtLeast(MIN_INTENSITY)
        return if (element == ThemeElement.WIDGETS) {
            ThemeSpec(widgetEffect = on, widgetIntensity = if (on) level else null)
        } else {
            ThemeSpec(crtEffect = on, crtIntensity = if (on) level else null)
        }
    }

    private const val MIN_INTENSITY = 0.05f

    private fun isOn(element: ThemeElement, spec: ThemeSpec?): Boolean? = when (element) {
        ThemeElement.CLOCK -> spec?.pixelClock
        ThemeElement.ICONS -> spec?.iconEffect
        ThemeElement.WIDGETS -> spec?.widgetEffect
        ThemeElement.CRT -> spec?.crtEffect
        ThemeElement.WALLPAPER -> spec?.showWallpaper
        ThemeElement.PAINT -> spec?.colorAsWallpaper
        else -> null
    }

    // ---- whole themes ----

    private fun verb(verb: ThemeVerb, rest: String, extras: CommandExtras): Analysis {
        val title = Texts.get(R.string.cmd_theme_title) + " · " + verb.desc
        val word = BuiltInCommand.THEME.word + " " + verb.word
        val typed = rest.foldAccents()
        val names = when (verb) {
            ThemeVerb.DELETE -> extras.themes.map { it.name }
            else -> extras.themes.map { it.name } + extras.themePresets.map { it.name }
        }
        val suggestions = names.filter { it.foldAccents().startsWith(typed) && it.foldAccents() != typed }
            .take(CommandEngine.MAX_SUGGESTIONS)
            .map { Suggestion("$word $it", it, Texts.get(R.string.cmd_theme_kind_saved)) }
        return when (verb) {
            ThemeVerb.SAVE -> {
                val name = ThemeSpecs.cleanName(rest)
                when {
                    name.isEmpty() -> result(title, Texts.get(R.string.cmd_theme_name_hint), Tone.IDLE, null, suggestions)

                    // A ready-made look keeps its name; the user's copy takes another one.
                    extras.themePresets.any { it.name.foldAccents() == name.foldAccents() } ->
                        result(title, Texts.get(R.string.cmd_theme_name_taken), Tone.ERROR, null)

                    extras.themes.any { it.name.foldAccents() == name.foldAccents() } ->
                        result(title, Texts.get(R.string.cmd_theme_save_replace, name), Tone.WARN, ThemeAction.Save(name), suggestions)

                    else -> result(title, Texts.get(R.string.cmd_theme_save_preview, name), Tone.OK, ThemeAction.Save(name), suggestions)
                }
            }

            ThemeVerb.SHARE -> {
                if (rest.isEmpty()) return result(title, Texts.get(R.string.cmd_theme_share_preview, Texts.get(R.string.cmd_theme_current)), Tone.OK, ThemeAction.Share(""), suggestions)
                val (spec, _) = named(rest, extras) ?: return result(title, Texts.get(R.string.cmd_theme_not_found), if (suggestions.isEmpty()) Tone.ERROR else Tone.IDLE, null, suggestions)
                result(title, Texts.get(R.string.cmd_theme_share_preview, spec.name), Tone.OK, ThemeAction.Share(spec.name))
            }

            ThemeVerb.DELETE -> {
                if (rest.isEmpty()) return result(title, Texts.get(R.string.cmd_theme_name_hint), Tone.IDLE, null, suggestions)
                val spec = extras.themes.firstOrNull { it.name.foldAccents() == typed }
                    ?: return result(
                        title,
                        Texts.get(if (extras.themePresets.any { it.name.foldAccents() == typed }) R.string.cmd_theme_delete_preset else R.string.cmd_theme_not_found),
                        if (suggestions.isEmpty()) Tone.ERROR else Tone.IDLE,
                        null,
                        suggestions,
                    )
                result(title, Texts.get(R.string.cmd_theme_delete_preview, spec.name), Tone.WARN, ThemeAction.Delete(spec.name))
            }
        }
    }

    // ---- texts ----

    private fun weakNote(spec: ThemeSpec, extras: CommandExtras): String = if (ThemeSpecs.weak(spec, extras.theme)) "\n" + Texts.get(R.string.cmd_theme_weak) else ""

    fun colorText(color: ThemeColor): String = when (color) {
        ThemeColor.Default -> ThemeChange.RESET.word
        ThemeColor.System -> ThemeChange.SYSTEM.word
        ThemeColor.Wallpaper -> ThemeElement.WALLPAPER.word
        is ThemeColor.Custom -> ThemeSpecs.hex(color.argb)
    }

    fun stateText(on: Boolean): String = Texts.get(if (on) R.string.cmd_state_on else R.string.cmd_state_off).split(',').first().trim()

    fun percentText(value: Float): String = "${Math.round(value * 100)}%"

    fun effectText(on: Boolean?, intensity: Float?): String? = when {
        on == null && intensity == null -> null
        on == false -> stateText(false)
        intensity != null -> percentText(intensity)
        else -> stateText(true)
    }

    /** "destaque #33ff66 · fundo #07090a · fonte Space Mono", what the theme sets, element by element. */
    fun summary(spec: ThemeSpec): String = ThemeElement.entries.mapNotNull { e -> e.valueIn(spec)?.let { e.word + " " + it } }
        .plus(listOfNotNull(spec.followSystem?.takeIf { it }?.let { ThemeChange.SYSTEM.word }))
        .joinToString(" · ")

    /** The technical line: the settings the theme writes. */
    fun describe(spec: ThemeSpec): String = org.json.JSONObject(ThemeSpecs.encode(spec, pretty = false)).let { o ->
        o.remove("format")
        o.remove("version")
        o.remove("name")
        // An image in base64 would fill the screen: its size stands for it.
        listOf("wallpaper", "lockWallpaper").forEach { key -> o.optJSONObject(key)?.optString("base64")?.takeIf { it.isNotEmpty() }?.let { o.optJSONObject(key).put("base64", "${it.length / 1024}KB") } }
        o.keys().asSequence().joinToString(" ") { key -> key + "=" + o.get(key).toString() }
    }

    private fun result(
        title: String,
        preview: String,
        tone: Tone,
        action: CommandAction?,
        suggestions: List<Suggestion> = emptyList(),
        listTitle: String = Texts.get(R.string.cmd_list_suggestions),
    ) = Analysis(
        suggestions = suggestions,
        listTitle = listTitle,
        needsContacts = false,
        previewTitle = title,
        preview = preview,
        tone = tone,
        action = action,
    )
}
