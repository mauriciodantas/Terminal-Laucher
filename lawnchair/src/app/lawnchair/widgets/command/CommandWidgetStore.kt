package app.lawnchair.widgets.command

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What each command widget runs and shows: the command line, when it runs again (a
 * [CommandWidgetSchedule]; by default only on a tap) and the last result with the time it was read. The widget, the configuration screen and
 * the provider all run in the launcher process, so [changes] tells the widgets on screen to reload.
 */
object CommandWidgetStore {

    private const val PREFS = "command_widgets"
    private const val KEY_COMMAND = "command_"
    private const val KEY_INTERVAL = "interval_"
    private const val KEY_SCHEDULE = "schedule_"
    private const val KEY_RESULT = "result_"
    private const val KEY_RESULT_AT = "result_at_"
    private const val KEY_FAILED = "failed_"
    private const val PENDING = "pending"
    private const val KEY_PENDING_AT = "pending_at"

    /** An imported widget waits this long for the home screen to add it; later it is left unused. */
    private const val PENDING_MAX_AGE = 30 * 60 * 1000L

    /** Minutes between runs the configuration offers for the interval schedule. */
    val INTERVALS = listOf(15, 30, 60, 180)

    private val _changes = MutableStateFlow(0)

    /** Bumped every time a widget's command or schedule changes. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun command(context: Context, id: Int): String = prefs(context).getString(KEY_COMMAND + id, null).orEmpty()

    /** The schedule; a widget saved when only an interval existed keeps it. */
    fun schedule(context: Context, id: Int): CommandWidgetSchedule {
        val prefs = prefs(context)
        prefs.getString(KEY_SCHEDULE + id, null)?.let { return CommandWidgetSchedule.decode(it) }
        val minutes = prefs.getInt(KEY_INTERVAL + id, 0)
        return if (minutes > 0) CommandWidgetSchedule(CommandWidgetSchedule.Kind.INTERVAL, intervalMinutes = minutes.coerceAtLeast(CommandWidgetSchedule.MIN_INTERVAL_MINUTES)) else CommandWidgetSchedule()
    }

    fun result(context: Context, id: Int): String? = prefs(context).getString(KEY_RESULT + id, null)

    /** When [result] was read, in ms; 0 when it never was. */
    fun resultAt(context: Context, id: Int): Long = prefs(context).getLong(KEY_RESULT_AT + id, 0L)

    /** Whether the last run failed, so the widget shows [result] as an error. */
    fun failed(context: Context, id: Int): Boolean = prefs(context).getBoolean(KEY_FAILED + id, false)

    fun saveResult(context: Context, id: Int, result: String, failed: Boolean) {
        prefs(context).edit {
            putString(KEY_RESULT + id, result)
            putLong(KEY_RESULT_AT + id, System.currentTimeMillis())
            putBoolean(KEY_FAILED + id, failed)
        }
    }

    /** Keeps the configuration; a different command drops the result of the old one. */
    fun save(context: Context, id: Int, command: String, schedule: CommandWidgetSchedule) {
        val line = command.trim()
        val changed = line != command(context, id)
        prefs(context).edit {
            if (line.isEmpty()) remove(KEY_COMMAND + id) else putString(KEY_COMMAND + id, line)
            putString(KEY_SCHEDULE + id, schedule.encode())
            remove(KEY_INTERVAL + id)
            if (changed) {
                remove(KEY_RESULT + id)
                remove(KEY_RESULT_AT + id)
                remove(KEY_FAILED + id)
            }
        }
        _changes.update { it + 1 }
    }

    /**
     * Keeps an imported [pack] for the next command widget added to the home screen, which takes it
     * in [applyPending] instead of asking for a command. A later import replaces it.
     */
    fun setPending(context: Context, pack: CommandWidgetPack) {
        prefs(context).edit {
            putString(KEY_COMMAND + PENDING, pack.command)
            putString(KEY_SCHEDULE + PENDING, pack.schedule.encode())
            putLong(KEY_PENDING_AT, System.currentTimeMillis())
        }
    }

    /**
     * Gives [id] the imported widget waiting in [setPending], when there is one and the widget has
     * no command yet. Returns true when it did. The home screen may report a new widget twice (its
     * configuration and the pin callback), and only the first one takes it.
     */
    @Synchronized
    fun applyPending(context: Context, id: Int): Boolean {
        if (command(context, id).isNotEmpty()) return false
        val prefs = prefs(context)
        val command = prefs.getString(KEY_COMMAND + PENDING, null)
        val fresh = System.currentTimeMillis() - prefs.getLong(KEY_PENDING_AT, 0) <= PENDING_MAX_AGE
        if (command.isNullOrEmpty() || !fresh) return false
        prefs.edit {
            putString(KEY_COMMAND + id, command)
            prefs.getString(KEY_SCHEDULE + PENDING, null)?.let { putString(KEY_SCHEDULE + id, it) }
            remove(KEY_COMMAND + PENDING)
            remove(KEY_SCHEDULE + PENDING)
            remove(KEY_PENDING_AT)
        }
        _changes.update { it + 1 }
        return true
    }

    fun delete(context: Context, ids: IntArray) {
        prefs(context).edit {
            ids.forEach {
                remove(KEY_COMMAND + it)
                remove(KEY_INTERVAL + it)
                remove(KEY_SCHEDULE + it)
                remove(KEY_RESULT + it)
                remove(KEY_RESULT_AT + it)
                remove(KEY_FAILED + it)
            }
        }
        _changes.update { it + 1 }
    }

    /** Backup restore hands out new widget ids, so each command follows its widget. */
    fun move(context: Context, oldIds: IntArray, newIds: IntArray) {
        val prefs = prefs(context)
        prefs.edit {
            oldIds.zip(newIds).forEach { (old, new) ->
                prefs.getString(KEY_COMMAND + old, null)?.let { putString(KEY_COMMAND + new, it) }
                if (prefs.contains(KEY_INTERVAL + old)) putInt(KEY_INTERVAL + new, prefs.getInt(KEY_INTERVAL + old, 0))
                prefs.getString(KEY_SCHEDULE + old, null)?.let { putString(KEY_SCHEDULE + new, it) }
                prefs.getString(KEY_RESULT + old, null)?.let { putString(KEY_RESULT + new, it) }
                if (prefs.contains(KEY_RESULT_AT + old)) putLong(KEY_RESULT_AT + new, prefs.getLong(KEY_RESULT_AT + old, 0L))
                if (prefs.contains(KEY_FAILED + old)) putBoolean(KEY_FAILED + new, prefs.getBoolean(KEY_FAILED + old, false))
                remove(KEY_COMMAND + old)
                remove(KEY_INTERVAL + old)
                remove(KEY_SCHEDULE + old)
                remove(KEY_RESULT + old)
                remove(KEY_RESULT_AT + old)
                remove(KEY_FAILED + old)
            }
        }
        _changes.update { it + 1 }
    }
}
