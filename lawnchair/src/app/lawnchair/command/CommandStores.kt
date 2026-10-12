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

/** Reminders, scheduled commands and rules; [CommandScheduler] arms and runs them. */
object JobStore {
    private const val KEY = "command_jobs"
    private const val KEY_NEXT_ID = "command_jobs_next_id"

    fun load(context: Context): List<ScheduledJob> = prefs(context).getString(KEY, null)?.let { Jobs.decode(it) }.orEmpty()

    fun save(context: Context, jobs: List<ScheduledJob>) {
        prefs(context).edit().putString(KEY, Jobs.encode(jobs)).commit()
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
