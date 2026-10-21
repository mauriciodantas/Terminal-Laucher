package app.lawnchair.widgets.command

import android.content.Context
import app.lawnchair.command.CommandWidgetData
import app.lawnchair.command.CommandWidgetModel
import app.lawnchair.command.CommandWidgetPack
import app.lawnchair.command.CommandWidgetSchedule
import app.lawnchair.command.CommandWidgetSnapshot
import app.lawnchair.command.WidgetRunState
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
    private const val KEY_SCHEDULE = "schedule_"

    /** Minutes between runs the configuration offers for the interval schedule. */
    val INTERVALS = listOf(15, 30, 60, 180)

    private val _changes = MutableStateFlow(0)

    /** Bumped every time a widget's command or schedule changes. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    /** What each widget keeps is the shared rules over this app's own keys, so what was saved before still reads. */
    private val KEYS = CommandWidgetData.Keys(
        command = KEY_COMMAND,
        schedule = KEY_SCHEDULE,
        result = "result_",
        resultAt = "result_at_",
        interval = "interval_",
        failed = "failed_",
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun data(context: Context) = CommandWidgetData(prefs(context), KEYS)

    /** What widget [id] draws: its command, its body, when it was read and its schedule. */
    fun snapshot(context: Context, id: Int): CommandWidgetSnapshot = CommandWidgetModel.snapshot(data(context), id)

    fun command(context: Context, id: Int): String = data(context).command(id)

    /** The schedule; a widget saved when only an interval existed keeps it. */
    fun schedule(context: Context, id: Int): CommandWidgetSchedule = data(context).schedule(id)

    fun result(context: Context, id: Int): String? = data(context).result(id)

    /** When [result] was read, in ms; 0 when it never was. */
    fun resultAt(context: Context, id: Int): Long = data(context).resultAt(id)

    /** Whether the last run failed, so the widget shows [result] as an error. */
    fun failed(context: Context, id: Int): Boolean = data(context).state(id) == WidgetRunState.ERROR

    fun saveResult(context: Context, id: Int, result: String, failed: Boolean) {
        data(context).saveResult(id, result, failed)
    }

    /** Keeps the configuration; a different command drops the result of the old one. */
    fun save(context: Context, id: Int, command: String, schedule: CommandWidgetSchedule) {
        data(context).save(id, command, schedule)
        _changes.update { it + 1 }
    }

    /**
     * Keeps an imported [pack] for the next command widget added to the home screen, which takes it
     * in [applyPending] instead of asking for a command. A later import replaces it.
     */
    fun setPending(context: Context, pack: CommandWidgetPack) {
        data(context).setPending(pack.command, pack.schedule)
    }

    /** Gives [id] the imported widget waiting in [setPending]; true when it did (see [CommandWidgetData.applyPending]). */
    @Synchronized
    fun applyPending(context: Context, id: Int): Boolean {
        if (!data(context).applyPending(id)) return false
        _changes.update { it + 1 }
        return true
    }

    fun delete(context: Context, ids: IntArray) {
        data(context).delete(ids)
        _changes.update { it + 1 }
    }

    /** Backup restore hands out new widget ids, so each command follows its widget. */
    fun move(context: Context, oldIds: IntArray, newIds: IntArray) {
        data(context).move(oldIds, newIds)
        _changes.update { it + 1 }
    }
}
