package app.lawnchair.command

import androidx.annotation.StringRes
import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents

/** "lanterna", "wifi on", "silencio off": flips a switch, or turns it on or off. */
class ToggleCommand(
    id: BuiltInCommand,
    private val target: SystemToggle,
    @StringRes usageRes: Int,
    @StringRes helpRes: Int,
) : BuiltIn(id, usageRes, helpRes) {

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val title = when (target) {
            SystemToggle.WIFI -> "WI-FI"
            SystemToggle.BLUETOOTH -> "BLUETOOTH"
            SystemToggle.TORCH -> Texts.get(R.string.cmd_torch_title)
            SystemToggle.DND -> Texts.get(R.string.cmd_dnd_title)
            SystemToggle.ROTATION -> Texts.get(R.string.cmd_rotation_title)
        }
        val on = state(arg)
        if (arg.isNotBlank() && on == null) {
            return commandResult(title, Texts.get(R.string.cmd_state_invalid), Tone.ERROR, null, stateSuggestions(id))
        }
        val preview = when {
            target == SystemToggle.WIFI || target == SystemToggle.BLUETOOTH -> Texts.get(R.string.cmd_panel_preview, title)
            on == true -> Texts.get(R.string.cmd_state_turn_on)
            on == false -> Texts.get(R.string.cmd_state_turn_off)
            else -> Texts.get(R.string.cmd_state_toggle)
        }
        val suggestions = if (target == SystemToggle.WIFI || target == SystemToggle.BLUETOOTH) emptyList() else stateSuggestions(id)
        return commandResult(title, preview, Tone.OK, CommandAction.Toggle(target, on), suggestions.filter { arg.isBlank() || it.completion.foldAccents().startsWith((id.word + " " + arg).foldAccents()) })
    }

    companion object {
        /** True for "on", false for "off", null for neither, in Portuguese, English and the app language. */
        fun state(word: String): Boolean? {
            val w = word.trim().foldAccents()
            if (w.isEmpty()) return null
            return when (w) {
                in TimeParse.words(R.string.cmd_state_on) + setOf("on", "liga", "ligar", "ligado", "ativar", "1") -> true
                in TimeParse.words(R.string.cmd_state_off) + setOf("off", "desliga", "desligar", "desligado", "desativar", "0") -> false
                else -> null
            }
        }

        private fun stateSuggestions(command: BuiltInCommand): List<Suggestion> {
            val on = Texts.get(R.string.cmd_state_on).split(',').first().trim()
            val off = Texts.get(R.string.cmd_state_off).split(',').first().trim()
            return listOf(
                Suggestion("${command.word} $on", on.uppercase(), Texts.get(R.string.cmd_kind_option)),
                Suggestion("${command.word} $off", off.uppercase(), Texts.get(R.string.cmd_kind_option)),
            )
        }
    }
}

/** "brilho 40", "brilho auto". */
object BrightnessCommand : BuiltIn(BuiltInCommand.BRIGHTNESS, R.string.cmd_usage_brightness, R.string.cmd_help_brightness) {

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val title = Texts.get(R.string.cmd_brightness_title)
        val t = arg.trim().foldAccents()
        if (t.isEmpty()) return commandResult(title, Texts.get(R.string.cmd_brightness_empty), Tone.IDLE, null, listTitle = Texts.get(R.string.cmd_list_format))
        if (t in setOf("auto", "automatico", "automatic") + TimeParse.words(R.string.cmd_word_auto)) {
            return commandResult(title, Texts.get(R.string.cmd_brightness_auto), Tone.OK, CommandAction.Brightness(null))
        }
        val percent = t.removeSuffix("%").trim().toIntOrNull()?.takeIf { it in 0..100 }
            ?: return commandResult(title, Texts.get(R.string.cmd_percent_invalid), Tone.ERROR, null)
        return commandResult(title, "$percent%", Tone.OK, CommandAction.Brightness(percent))
    }
}

/** "vol 30", "vol mudo". */
object VolumeCommand : BuiltIn(BuiltInCommand.VOLUME, R.string.cmd_usage_volume, R.string.cmd_help_volume) {

    override val requiresArgument = true

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val title = Texts.get(R.string.cmd_volume_title)
        val t = arg.trim().foldAccents()
        if (t.isEmpty()) return commandResult(title, Texts.get(R.string.cmd_volume_empty), Tone.IDLE, null, listTitle = Texts.get(R.string.cmd_list_format))
        val percent = if (t in setOf("mudo", "mute") + TimeParse.words(R.string.cmd_word_mute)) {
            0
        } else {
            t.removeSuffix("%").trim().toIntOrNull()?.takeIf { it in 0..100 }
                ?: return commandResult(title, Texts.get(R.string.cmd_percent_invalid), Tone.ERROR, null)
        }
        return commandResult(title, "$percent%", Tone.OK, CommandAction.Volume(percent))
    }
}

/** "tocar", "pausar", "proxima", "anterior": the media key, for whatever is playing. */
class MediaCommand(id: BuiltInCommand, private val key: MediaKey) : BuiltIn(id, R.string.cmd_usage_media, R.string.cmd_help_media) {

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val preview = when (key) {
            MediaKey.PLAY -> "▶"
            MediaKey.PAUSE -> "❚❚"
            MediaKey.NEXT -> "▶▶❙"
            MediaKey.PREVIOUS -> "❙◀◀"
        }
        return commandResult(Texts.get(R.string.cmd_media_title), preview, Tone.OK, CommandAction.Media(key))
    }
}

/** "config wifi": a page of the system settings. */
object SettingsCommand : BuiltIn(BuiltInCommand.SETTINGS, R.string.cmd_usage_settings, R.string.cmd_help_settings) {

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val title = Texts.get(R.string.cmd_settings_title)
        val page = SettingsPage.of(arg)
        val word = BuiltInCommand.SETTINGS.word
        val matches = SettingsPage.matching(arg)
        return commandResult(
            title,
            page?.word?.uppercase() ?: if (matches.isEmpty()) Texts.get(R.string.cmd_settings_not_found) else Texts.get(R.string.cmd_settings_empty),
            when {
                page != null -> Tone.OK
                matches.isEmpty() -> Tone.ERROR
                else -> Tone.IDLE
            },
            page?.let { CommandAction.OpenSettings(it) },
            suggestions = (if (page != null) emptyList() else matches).map { Suggestion("$word ${it.word}", it.word, Texts.get(R.string.cmd_kind_option)) },
        )
    }
}
