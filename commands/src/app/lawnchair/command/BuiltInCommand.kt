package app.lawnchair.command

import androidx.annotation.StringRes
import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents

/**
 * The commands the bar understands without any setup. Each one is typed with the word of the app
 * language ([word]), and the Portuguese and English words always work too, so aliases, shared
 * packs and agents keep working whatever the language.
 */
enum class BuiltInCommand(
    @StringRes private val wordRes: Int,
    val portuguese: String,
    val english: String,
    /** A shorter word that works in every language too: "vol" for volume, "bt" for bluetooth. */
    val short: String = "",
) {
    OPEN(R.string.cmd_word_open, "abrir", "open"),
    ALARM(R.string.cmd_word_alarm, "alarme", "alarm"),
    CALC(R.string.cmd_word_calc, "calc", "calc"),
    CALL(R.string.cmd_word_call, "ligar", "call"),
    ROUTE(R.string.cmd_word_route, "rota", "route"),
    SPLIT(R.string.cmd_word_split, "dividir", "split"),
    THEME(R.string.cmd_word_theme, "tema", "theme"),
    TIMER(R.string.cmd_word_timer, "timer", "timer"),
    REMIND(R.string.cmd_word_remind, "lembrar", "remind"),
    WIFI(R.string.cmd_word_wifi, "wifi", "wifi"),
    BLUETOOTH(R.string.cmd_word_bluetooth, "bluetooth", "bluetooth", short = "bt"),
    TORCH(R.string.cmd_word_torch, "lanterna", "flashlight", short = "torch"),
    DND(R.string.cmd_word_dnd, "silencio", "dnd"),
    BRIGHTNESS(R.string.cmd_word_brightness, "brilho", "brightness"),
    ROTATION(R.string.cmd_word_rotation, "rotacao", "rotation"),
    VOLUME(R.string.cmd_word_volume, "volume", "volume", short = "vol"),
    PLAY(R.string.cmd_word_play, "tocar", "play"),
    PAUSE(R.string.cmd_word_pause, "pausar", "pause"),
    NEXT(R.string.cmd_word_next, "proxima", "next"),
    PREVIOUS(R.string.cmd_word_previous, "anterior", "previous"),
    COPY(R.string.cmd_word_copy, "copiar", "copy"),
    PASTE(R.string.cmd_word_paste, "colar", "paste"),
    SETTINGS(R.string.cmd_word_settings, "config", "settings"),
    AGENDA(R.string.cmd_word_agenda, "agenda", "agenda"),
    NOTE(R.string.cmd_word_note, "nota", "note"),
    SCHEDULED(R.string.cmd_word_scheduled, "agendados", "scheduled"),
    CANCEL(R.string.cmd_word_cancel, "cancelar", "cancel"),
    BATTERY(R.string.cmd_word_battery, "bateria", "battery"),
    STORAGE(R.string.cmd_word_storage, "armazenamento", "storage"),
    IP(R.string.cmd_word_ip, "ip", "ip"),
    UPTIME(R.string.cmd_word_uptime, "uptime", "uptime"),
    PING(R.string.cmd_word_ping, "ping", "ping"),
    NETWORK(R.string.cmd_word_network, "rede", "network", short = "net"),
    FAST(R.string.cmd_word_fast, "fast", "fast", short = "speedtest"),
    AI(R.string.cmd_word_ai, "ia", "ai"),
    QR(R.string.cmd_word_qr, "qr", "qr"),
    PIX(R.string.cmd_word_pix, "pix", "pix"),
    NEARBY(R.string.cmd_word_nearby, "perto", "nearby"),
    WEATHER(R.string.cmd_word_weather, "clima", "weather", short = "tempo"),
    FREE_SPACE(R.string.cmd_word_free_space, "liberar", "cleanup"),
    UNINSTALL(R.string.cmd_word_uninstall, "desinstalar", "uninstall"),
    UPDATE(R.string.cmd_word_update, "atualizar", "update"),
    PACK(R.string.cmd_word_pack, "pacote", "pack"),
    IMPORT(R.string.cmd_word_import, "importar", "import"),
    ;

    /** The word shown and suggested: "abrir" in Portuguese, "open" in English. */
    val word: String get() = Texts.get(wordRes)

    /** Every word that runs this command, accent-folded like the typed token. */
    fun words(): Set<String> = setOf(word, portuguese, english, short).filter { it.isNotEmpty() }.map { it.foldAccents() }.toSet()

    companion object {
        /** One-letter shortcuts, the same in every language: a new task and calc. */
        const val TASK = "t"
        const val CALC_SHORT = "c"

        /** Typed twice, the last command runs again. */
        const val REPEAT = "!!"

        /** The command an accent-folded [token] names, or null. "c" is calc. */
        fun of(token: String): BuiltInCommand? = if (token == CALC_SHORT) CALC else entries.firstOrNull { token in it.words() }

        /** Words a custom letter or an alias cannot take. */
        fun reserved(): Set<String> = entries.flatMap { it.words() }.toSet() + TASK + CALC_SHORT
    }
}

/**
 * A character a command word, letter or alias may hold: letters and digits of any script, and the
 * vowel signs and accents that scripts like Devanagari write as separate marks.
 */
internal fun Char.isCommandWordChar(): Boolean = isLetterOrDigit() ||
    Character.getType(this).toByte().let { it == Character.NON_SPACING_MARK || it == Character.COMBINING_SPACING_MARK }
