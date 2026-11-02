package app.lawnchair.command

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val STORE = "nostromo"

private fun prefs(context: Context) = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)

/** The notes taken with "nota", newest first. */
object NoteStore {
    private const val KEY = "command_notes"

    fun load(context: Context): List<NoteEntry> = prefs(context).getString(KEY, null)?.let { Notes.decode(it) }.orEmpty()

    fun add(context: Context, text: String) {
        save(context, Notes.add(load(context), text, System.currentTimeMillis()))
    }

    fun save(context: Context, notes: List<NoteEntry>) {
        prefs(context).edit().putString(KEY, Notes.encode(notes)).apply()
    }
}

/** The user's `{NAME}` texts, set with "export" or in the settings. */
object UserVariableStore {
    private const val KEY = "command_variables"

    fun load(context: Context): List<UserVariable> = prefs(context).getString(KEY, null)?.let { UserVariables.decode(it) }.orEmpty()

    fun save(context: Context, variables: List<UserVariable>) {
        prefs(context).edit().putString(KEY, UserVariables.encode(variables)).commit()
    }
}

/** Reminders, scheduled commands and rules; [CommandScheduler] arms and runs them. */
object JobStore {
    /** An alarm never comes early, but its clock and the job's may differ by a moment. */
    private const val DUE_SLACK_MS = 2_000L
    private const val KEY = "command_jobs"
    private const val KEY_NEXT_ID = "command_jobs_next_id"

    fun load(context: Context): List<ScheduledJob> = prefs(context).getString(KEY, null)?.let { Jobs.decode(it) }.orEmpty()

    fun save(context: Context, jobs: List<ScheduledJob>) {
        prefs(context).edit().putString(KEY, Jobs.encode(jobs)).commit()
        app.lawnchair.widgets.command.RoutinesFeed.changed()
    }

    /** Saves [job] with a fresh id and returns it. */
    @Synchronized
    fun add(context: Context, job: ScheduledJob): ScheduledJob {
        val id = prefs(context).getLong(KEY_NEXT_ID, 1)
        prefs(context).edit().putLong(KEY_NEXT_ID, id + 1).commit()
        val saved = job.copy(id = id)
        save(context, load(context) + saved)
        return saved
    }

    @Synchronized
    fun remove(context: Context, id: Long) {
        save(context, load(context).filter { it.id != id })
    }

    /**
     * The job [id] to run now. One that runs once is removed; one that repeats stays, moved to its
     * next run, and the job returned is the one that was due. Null when it is gone or not due yet,
     * so a job fired twice at once (the alarm and the start of the app) runs only once.
     */
    @Synchronized
    fun takeOrAdvance(context: Context, id: Long, nowMillis: Long): ScheduledJob? {
        val jobs = load(context)
        val job = jobs.firstOrNull { it.id == id } ?: return null
        val next = Repeats.advance(job, nowMillis, java.time.ZoneId.systemDefault()) ?: return take(context, id)
        if (job.at > nowMillis + DUE_SLACK_MS) return null
        save(context, jobs.map { if (it.id == id) next else it })
        return job
    }

    /** Removes the job [id] and returns it, so a job fired twice at once runs only once. */
    @Synchronized
    fun take(context: Context, id: Long): ScheduledJob? {
        val jobs = load(context)
        val job = jobs.firstOrNull { it.id == id } ?: return null
        save(context, jobs.filter { it.id != id })
        return job
    }
}

/** How often each command runs at each hour, for the quick starts. */
object StatsStore {
    private const val KEY = "command_stats"

    fun load(context: Context): Map<String, IntArray> = prefs(context).getString(KEY, null)?.let { CommandStats.decode(it) }.orEmpty()

    fun record(context: Context, command: String) {
        val hour = java.time.LocalTime.now().hour
        prefs(context).edit().putString(KEY, CommandStats.encode(CommandStats.record(load(context), command, hour))).apply()
    }

    fun save(context: Context, stats: Map<String, IntArray>) {
        prefs(context).edit().putString(KEY, CommandStats.encode(stats)).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).remove(KEY_TIPS).apply()
        ContextStore.clear(context)
    }

    private const val KEY_TIPS = "alias_tip_dismissed"

    /** The lines the user turned down as alias tips, so the same one does not come back. */
    fun dismissedTips(context: Context): Set<String> = prefs(context).getString(KEY_TIPS, null)?.split('\n')?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    fun dismissTip(context: Context, line: String) {
        prefs(context).edit().putString(KEY_TIPS, (dismissedTips(context) + line).joinToString("\n")).apply()
    }
}

/** The last result of each alias, scheduled command and rule, which "rotinas" lists. */
object RoutineStore {
    private const val KEY = "command_routine_runs"

    fun load(context: Context): List<RoutineRun> = prefs(context).getString(KEY, null)?.let { Routines.decode(it) }.orEmpty()

    @Synchronized
    fun record(context: Context, key: String, label: String, ok: Boolean, output: String = "") {
        val run = RoutineRun(key, label, System.currentTimeMillis(), ok, Routines.brief(output))
        prefs(context).edit().putString(KEY, Routines.encode(Routines.record(load(context), run))).apply()
        app.lawnchair.widgets.command.RoutinesFeed.changed()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }
}

/** The commands run while charging or with headphones on, which the bar offers first when that happens again. */
object ContextStore {
    private const val KEY = "command_context_stats"

    /** The situations the phone is in now. */
    fun tags(context: Context): Set<String> = buildSet {
        if (CommandSources.snapshot(context)?.charging == true) add(ContextStats.CHARGING)
        if (headphones(context)) add(ContextStats.HEADPHONES)
    }

    private fun headphones(context: Context): Boolean = runCatching {
        val audio = context.getSystemService(android.media.AudioManager::class.java) ?: return false
        val types = setOf(
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
        )
        audio.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).any { it.type in types }
    }.getOrDefault(false)

    fun load(context: Context): Map<String, Map<String, Int>> = runCatching {
        val root = org.json.JSONObject(prefs(context).getString(KEY, null) ?: return emptyMap())
        root.keys().asSequence().associateWith { tag ->
            val o = root.getJSONObject(tag)
            o.keys().asSequence().associateWith { o.getInt(it) }
        }
    }.getOrDefault(emptyMap())

    fun record(context: Context, command: String) {
        val tags = tags(context)
        if (tags.isEmpty()) return
        val updated = ContextStats.record(load(context), tags, command)
        val root = org.json.JSONObject()
        updated.forEach { (tag, counts) -> root.put(tag, org.json.JSONObject(counts as Map<*, *>)) }
        prefs(context).edit().putString(KEY, root.toString()).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }
}

/**
 * Exchange rates for calc, from a free service that needs no key. They are kept for a day and
 * refreshed in the background when the command bar opens.
 */
object RatesStore {
    private const val KEY = "command_rates"
    private const val URL_LATEST = "https://open.er-api.com/v6/latest/USD"
    private const val MAX_AGE_MS = 12 * 60 * 60 * 1000L

    fun load(context: Context): CurrencyRates? = prefs(context).getString(KEY, null)?.let { saved ->
        runCatching { JSONObject(saved).let { parse(it.getString("body"), it.getLong("fetched")) } }.getOrNull()
    }

    /** Fresh rates when the saved ones are old or missing; the saved ones when the network fails. */
    suspend fun refresh(context: Context): CurrencyRates? = withContext(Dispatchers.IO) {
        val saved = load(context)
        if (saved != null && System.currentTimeMillis() - saved.updated < MAX_AGE_MS) return@withContext saved
        runCatching {
            val connection = URL(URL_LATEST).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            try {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val now = System.currentTimeMillis()
                parse(body, now)?.also {
                    prefs(context).edit().putString(KEY, JSONObject().put("fetched", now).put("body", body).toString()).apply()
                }
            } finally {
                connection.disconnect()
            }
        }.getOrNull() ?: saved
    }

    private fun parse(body: String, fetched: Long): CurrencyRates? = runCatching {
        val o = JSONObject(body)
        if (o.optString("result") != "success") return null
        val rates = o.getJSONObject("rates")
        CurrencyRates(
            base = o.getString("base_code"),
            rates = rates.keys().asSequence().associateWith { rates.getDouble(it) },
            // The age is counted from when the rates were fetched, not from the service's own update.
            updated = fetched,
        )
    }.getOrNull()
}
