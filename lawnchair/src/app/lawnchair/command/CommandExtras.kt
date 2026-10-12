package app.lawnchair.command

import androidx.annotation.StringRes
import app.lawnchair.util.Texts
import app.lawnchair.util.foldAccents
import com.android.launcher3.R
import java.time.LocalDateTime
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the command bar knows besides apps, contacts, actions and aliases: the history, the
 * clipboard, the calendar, the rates... Each is loaded by the screen when it opens; whatever is
 * missing is null or empty, and the commands that need it say so.
 */
data class CommandExtras(
    val history: List<String> = emptyList(),
    val clipboard: String? = null,
    /** Text selected in another app and sent with "Terminal" from its menu. */
    val selection: String? = null,
    /** "lat,long" of the last known location; null when unknown or not allowed. */
    val location: String? = null,
    val locationGranted: Boolean = false,
    val rates: CurrencyRates? = null,
    /** Events of today and tomorrow; null when the calendar permission is missing. */
    val events: List<CalendarEntry>? = null,
    val calendarGranted: Boolean = false,
    val notes: List<NoteEntry> = emptyList(),
    val jobs: List<ScheduledJob> = emptyList(),
    val info: SystemSnapshot? = null,
    /** Name of the app "t" sends tasks to; null until one is picked. */
    val taskApp: String? = null,
    /** "Claude · claude-opus-5-5" once an AI key is saved; null keeps "ia" locked. */
    val ai: String? = null,
    /** The connection in use; null while it is not read yet. */
    val network: NetworkSnapshot? = null,
    /** The theme on screen now, every element set; null when unknown. */
    val theme: ThemeSpec? = null,
    /** The themes the user saved, by name. */
    val themes: List<ThemeSpec> = emptyList(),
    /** The ready-made looks of Appearance, by name. */
    val themePresets: List<ThemeSpec> = emptyList(),
    /** Google Fonts families "tema fonte" can pick; empty until the list loads. */
    val fonts: List<String> = emptyList(),
    val now: LocalDateTime? = null,
    val zone: ZoneId? = null,
) {
    fun clock(): LocalDateTime = now ?: LocalDateTime.now(zone())

    fun zone(): ZoneId = zone ?: ZoneId.systemDefault()

    fun epochMillis(time: LocalDateTime): Long = time.atZone(zone()).toInstant().toEpochMilli()

    fun timeOf(epochMillis: Long): LocalDateTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), zone())
}

/** Something the command needs before it can run. The screen offers to grant it. */
enum class Need { CALENDAR, LOCATION }

data class CalendarEntry(val title: String, val begin: Long, val end: Long, val allDay: Boolean)

data class NoteEntry(val text: String, val time: Long)

/** A snapshot of the device for battery, storage, ip and uptime. */
data class SystemSnapshot(
    val batteryPercent: Int,
    val charging: Boolean,
    val batteryCelsius: Double?,
    val storageFree: Long,
    val storageTotal: Long,
    val addresses: List<String>,
    val uptimeMillis: Long,
)

/** How the phone is connected. */
enum class NetKind { WIFI, CELLULAR, ETHERNET, NONE }

/**
 * The connection in use, for "rede". A field the system does not give (the Wi-Fi name needs precise
 * location; a mobile network has no link speed) is null and left out of the text.
 */
data class NetworkSnapshot(
    val kind: NetKind,
    /** The system checked that this network reaches the internet. */
    val internet: Boolean = false,
    /** Charged by the data used (mobile data, a hotspot). */
    val metered: Boolean = false,
    val vpn: Boolean = false,
    val ssid: String? = null,
    val signalDbm: Int? = null,
    val frequencyMhz: Int? = null,
    /** 4, 5, 6 or 7 for Wi-Fi 4 to 7; null when unknown. */
    val wifiStandard: Int? = null,
    val linkMbps: Int? = null,
    val carrier: String? = null,
    /** The system's estimate of what the link can carry, in kbps. */
    val downKbps: Int? = null,
    val upKbps: Int? = null,
    val addresses: List<String> = emptyList(),
    val gateway: String? = null,
    val dns: List<String> = emptyList(),
    val privateDns: String? = null,
    val interfaceName: String? = null,
)

/** A media key the command bar can press. */
enum class MediaKey { PLAY, PAUSE, NEXT, PREVIOUS }

/** Switches the command bar can flip: a few directly, the others by opening their system panel. */
enum class SystemToggle { WIFI, BLUETOOTH, TORCH, DND, ROTATION }

/** What a "quando" (when) rule waits for. */
enum class TriggerEvent(@StringRes private val wordRes: Int, val portuguese: String, val english: String) {
    CHARGING(R.string.cmd_event_charging, "carregar", "charging"),
    UNPLUGGED(R.string.cmd_event_unplugged, "descarregar", "unplugged"),
    HEADPHONES(R.string.cmd_event_headphones, "fone", "headphones"),
    ;

    val word: String get() = Texts.get(wordRes)

    fun words(): Set<String> = setOf(word, portuguese, english).map { it.foldAccents() }.toSet()

    companion object {
        fun of(word: String): TriggerEvent? = word.trim().foldAccents().let { w -> entries.firstOrNull { w in it.words() } }
    }
}

enum class JobKind { REMIND, COMMAND, RULE }

/**
 * Something that runs later: a reminder ("lembrar 18h pão"), a command at a time ("em 30m: tema
 * escuro") or a rule that runs a command each time an event happens ("quando carregar: wifi").
 * [at] is epoch millis for the first two, 0 for a rule.
 */
data class ScheduledJob(
    val id: Long,
    val kind: JobKind,
    val text: String,
    val at: Long = 0,
    val event: TriggerEvent? = null,
)

/** Pure rules of scheduled jobs, so they can be unit tested. */
object Jobs {

    /** Jobs in the order they are listed: by time, rules last. */
    fun sorted(jobs: List<ScheduledJob>): List<ScheduledJob> = jobs.sortedWith(compareBy({ it.kind == JobKind.RULE }, { it.at }, { it.id }))

    /** "18:00 · lembrete: comprar pão", "quando carregar: wifi". */
    fun describe(job: ScheduledJob, extras: CommandExtras): String = when (job.kind) {
        JobKind.REMIND -> TimeParse.formatClock(extras.timeOf(job.at), extras.clock()) + " · " + Texts.get(R.string.cmd_remind_label, job.text)
        JobKind.COMMAND -> TimeParse.formatClock(extras.timeOf(job.at), extras.clock()) + " · " + job.text
        JobKind.RULE -> Texts.get(R.string.cmd_word_when).split(',').first().trim() + " " + job.event?.word.orEmpty() + ": " + job.text
    }

    fun encode(jobs: List<ScheduledJob>): String = JSONArray().also { array ->
        jobs.forEach { job ->
            array.put(
                JSONObject()
                    .put("id", job.id)
                    .put("kind", job.kind.name)
                    .put("text", job.text)
                    .put("at", job.at)
                    .put("event", job.event?.name ?: ""),
            )
        }
    }.toString()

    fun decode(raw: String): List<ScheduledJob> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            runCatching {
                val o = array.getJSONObject(i)
                ScheduledJob(
                    id = o.getLong("id"),
                    kind = JobKind.valueOf(o.getString("kind")),
                    text = o.getString("text"),
                    at = o.optLong("at"),
                    event = o.optString("event").takeIf { it.isNotEmpty() }?.let { TriggerEvent.valueOf(it) },
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    /** Jobs whose time passed, which are dropped; rules stay. */
    fun due(jobs: List<ScheduledJob>, nowMillis: Long): List<ScheduledJob> = jobs.filter { it.kind != JobKind.RULE && it.at <= nowMillis }
}

/** Pure rules of the notes taken with "nota". */
object Notes {
    const val MAX = 200

    fun add(notes: List<NoteEntry>, text: String, time: Long): List<NoteEntry> = (listOf(NoteEntry(text.trim(), time)) + notes).take(MAX)

    fun encode(notes: List<NoteEntry>): String = JSONArray().also { array ->
        notes.forEach { array.put(JSONObject().put("text", it.text).put("time", it.time)) }
    }.toString()

    fun decode(raw: String): List<NoteEntry> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            runCatching { array.getJSONObject(i).let { NoteEntry(it.getString("text"), it.getLong("time")) } }.getOrNull()
        }
    }.getOrDefault(emptyList())

    /** All notes as one text to share, newest first. */
    fun export(notes: List<NoteEntry>): String = notes.joinToString("\n") { "• " + it.text }
}

/**
 * How often each command ran at each hour of the day, so the quick starts show what is usually
 * run at this time: at 8h, the app opened every morning comes first.
 */
object CommandStats {
    const val MAX_COMMANDS = 60

    fun record(stats: Map<String, IntArray>, command: String, hour: Int): Map<String, IntArray> {
        val clean = command.trim()
        if (clean.isEmpty()) return stats
        val counts = (stats[clean]?.copyOf() ?: IntArray(24))
        counts[hour.coerceIn(0, 23)]++
        val updated = LinkedHashMap(stats)
        updated[clean] = counts
        // Forget the least used commands when there are too many.
        return if (updated.size <= MAX_COMMANDS) {
            updated
        } else {
            updated.entries.sortedByDescending { e -> e.value.sum() }.take(MAX_COMMANDS).associate { it.key to it.value }
        }
    }

    /** Commands by how much they are run around [hour]; those never run near it are left out. */
    fun rank(stats: Map<String, IntArray>, hour: Int): List<String> = stats.entries
        .map { (command, counts) ->
            val near = (-1..1).sumOf { d -> counts[(hour + d + 24) % 24] * if (d == 0) 2 else 1 }
            command to near * 10 + counts.sum()
        }
        .filter { (command, _) -> (-1..1).any { d -> (stats[command]?.get((hour + d + 24) % 24) ?: 0) > 0 } }
        .sortedByDescending { it.second }
        .map { it.first }

    fun encode(stats: Map<String, IntArray>): String = JSONObject().also { o ->
        stats.forEach { (command, counts) -> o.put(command, JSONArray(counts.toList())) }
    }.toString()

    fun decode(raw: String): Map<String, IntArray> = runCatching {
        val o = JSONObject(raw)
        o.keys().asSequence().associateWith { key ->
            val array = o.getJSONArray(key)
            IntArray(24) { if (it < array.length()) array.getInt(it) else 0 }
        }
    }.getOrDefault(emptyMap())
}
