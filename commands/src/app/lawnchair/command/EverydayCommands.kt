package app.lawnchair.command

import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents

/** "qr texto" or "qr wifi casa senha": a QR code drawn in the bar, for somebody else's camera. */
object QrCommand : BuiltIn(BuiltInCommand.QR, R.string.cmd_usage_qr, R.string.cmd_help_qr) {

    override val requiresArgument = true

    /** QR codes hold about 2900 bytes at most; past this one gets too dense to read off a screen. */
    const val MAX_LENGTH = 800

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val title = "QR"
        val text = arg.trim()
        if (text.isEmpty()) return commandResult(title, Texts.get(R.string.cmd_qr_empty), Tone.IDLE, null)
        val wifi = wifi(text)
        val content = wifi?.first ?: text
        if (content.length > MAX_LENGTH) return commandResult(title, Texts.get(R.string.cmd_qr_long, MAX_LENGTH), Tone.ERROR, null)
        val preview = if (wifi != null) Texts.get(R.string.cmd_qr_wifi, wifi.second) else text.lineSequence().first().take(60).uppercase()
        return commandResult(title, preview + " · " + Texts.get(R.string.cmd_qr_ready), Tone.OK, CommandAction.ShowQr(content))
    }

    private val wifiWords = setOf("wifi", "wi-fi")

    /**
     * "wifi casa senha123" as the text phones read to join a network, with the network name; null
     * when [text] is not about Wi-Fi. The last word is the password, unless the name is in quotes
     * ("wifi \"casa da ana\" senha") or there is only the name, which is an open network.
     */
    fun wifi(text: String): Pair<String, String>? {
        val first = text.substringBefore(' ').foldAccents()
        if (first !in wifiWords) return null
        val rest = text.substringAfter(' ', "").trim()
        if (rest.isEmpty()) return null
        val (ssid, password) = if (rest.startsWith('"') && rest.indexOf('"', 1) > 0) {
            val end = rest.indexOf('"', 1)
            rest.substring(1, end) to rest.substring(end + 1).trim()
        } else {
            val words = rest.split(Regex("\\s+"))
            if (words.size == 1) words[0] to "" else words.dropLast(1).joinToString(" ") to words.last()
        }
        if (ssid.isEmpty()) return null
        val security = if (password.isEmpty()) "T:nopass;" else "T:WPA;"
        val pass = if (password.isEmpty()) "" else "P:" + escape(password) + ";"
        return "WIFI:${security}S:${escape(ssid)};$pass;" to ssid
    }

    /** The Wi-Fi QR format escapes these with a backslash. */
    private fun escape(value: String): String = value.replace(Regex("([\\\\;,:\"])"), "\\\\$1")
}

/** "perto farmácia": the places of that kind around you, in the maps app. */
object NearbyCommand : BuiltIn(BuiltInCommand.NEARBY, R.string.cmd_usage_nearby, R.string.cmd_help_nearby) {

    /** The kinds of place offered while nothing is typed, in the app language. */
    fun places(): List<String> = Texts.get(R.string.cmd_nearby_places).split(',').map { it.trim() }.filter { it.isNotEmpty() }

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val place = arg.trim()
        val title = Texts.get(R.string.cmd_nearby_title)
        val q = place.foldAccents()
        val suggestions = places().filter { it.foldAccents().startsWith(q) && it.foldAccents() != q }
            .take(if (q.isEmpty()) CommandEngine.EMPTY_SUGGESTIONS else CommandEngine.MAX_SUGGESTIONS)
            .map { Suggestion("$word $it", it, Texts.get(R.string.cmd_kind_place)) }
        if (place.isEmpty()) {
            return commandResult(title, Texts.get(R.string.cmd_nearby_empty), Tone.IDLE, null, suggestions, Texts.get(R.string.cmd_list_places))
        }
        return commandResult(
            title,
            Texts.get(R.string.cmd_nearby_open, place.uppercase()),
            Tone.OK,
            CommandAction.Nearby(place),
            suggestions,
            Texts.get(R.string.cmd_list_places),
        )
    }
}

/** "clima" for where you are, "clima recife" for a city: now, today and the next two days. */
object WeatherCommand : BuiltIn(BuiltInCommand.WEATHER, R.string.cmd_usage_weather, R.string.cmd_help_weather) {

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val extras = ctx.extras
        val title = Texts.get(R.string.cmd_weather_title)
        val place = arg.trim()
        if (extras.network?.kind == NetKind.NONE) return commandResult(title, Texts.get(R.string.cmd_net_offline), Tone.ERROR, null)
        if (place.isNotEmpty()) {
            return commandResult(title, Texts.get(R.string.cmd_weather_place, place.uppercase()), Tone.OK, CommandAction.Weather(place, null))
        }
        val location = extras.location
        return when {
            location != null -> commandResult(title, Texts.get(R.string.cmd_weather_here), Tone.OK, CommandAction.Weather("", location))
            !extras.locationGranted -> commandResult(title, Texts.get(R.string.cmd_weather_need_location), Tone.WARN, null, need = Need.LOCATION)
            else -> commandResult(title, Texts.get(R.string.cmd_weather_no_location), Tone.WARN, null)
        }
    }
}

/** "liberar" or "liberar espaço": the system's own screen to free up storage. */
object FreeSpaceCommand : BuiltIn(BuiltInCommand.FREE_SPACE, R.string.cmd_usage_free_space, R.string.cmd_help_free_space) {

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val info = ctx.extras.info
        val free = info?.let { Texts.get(R.string.cmd_storage_free, InfoCommand.bytes(it.storageFree), InfoCommand.bytes(it.storageTotal)) + "\n" }.orEmpty()
        return commandResult(
            Texts.get(R.string.cmd_free_space_title),
            free + Texts.get(R.string.cmd_free_space_open),
            Tone.OK,
            CommandAction.OpenSettings(SettingsPage.FREE_SPACE),
        )
    }
}

/** "desinstalar jogo": asks the system to uninstall an app, which confirms it first. */
object UninstallCommand : BuiltIn(BuiltInCommand.UNINSTALL, R.string.cmd_usage_uninstall, R.string.cmd_help_uninstall) {

    override val requiresArgument = true

    override fun analyze(arg: String, ctx: CommandContext): Analysis = appCommand(
        word,
        arg,
        ctx.apps,
        Texts.get(R.string.cmd_uninstall_title),
        Texts.get(R.string.cmd_uninstall_empty),
        R.string.cmd_uninstall_open,
    ) { CommandAction.Uninstall(it) }
}

/** "atualizar whatsapp": the app's page in the Play Store; "atualizar" alone opens the store. */
object UpdateCommand : BuiltIn(BuiltInCommand.UPDATE, R.string.cmd_usage_update, R.string.cmd_help_update) {

    override fun analyze(arg: String, ctx: CommandContext): Analysis {
        val title = Texts.get(R.string.cmd_update_title)
        if (arg.isBlank()) return commandResult(title, Texts.get(R.string.cmd_update_store), Tone.OK, CommandAction.UpdateApp(null))
        return appCommand(word, arg, ctx.apps, title, "", R.string.cmd_update_open) { CommandAction.UpdateApp(it) }
    }
}

/** The analysis of a command that acts on one app, completed from the app list like "abrir". */
private fun appCommand(
    word: String,
    arg: String,
    apps: List<AppEntry>,
    title: String,
    empty: String,
    open: Int,
    build: (AppEntry) -> CommandAction,
): Analysis {
    val q = arg.trim().foldAccents()
    // One entry per package: an app with two launcher icons is still one app to uninstall.
    val matches = OpenCommand.matchApps(q, apps).distinctBy { it.id.substringBefore('/') }
    val first = matches.firstOrNull()
    val suggestions = matches.take(CommandEngine.MAX_SUGGESTIONS).map {
        Suggestion("$word ${it.label.lowercase()}", it.label, Texts.get(R.string.cmd_kind_program))
    }
    return commandResult(
        title,
        when {
            q.isEmpty() -> empty
            first != null -> Texts.get(open, first.label.uppercase())
            else -> Texts.get(R.string.cmd_open_not_found)
        },
        when {
            q.isEmpty() -> Tone.IDLE
            first != null -> Tone.OK
            else -> Tone.ERROR
        },
        if (q.isNotEmpty() && first != null) build(first) else null,
        suggestions,
        Texts.get(R.string.cmd_list_programs),
    )
}
