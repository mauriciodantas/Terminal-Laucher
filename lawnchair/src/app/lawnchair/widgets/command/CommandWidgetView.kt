package app.lawnchair.widgets.command

import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.lawnchair.command.CommandLineRunner
import app.lawnchair.theme.color.tokens.PhosphorColorToken
import app.lawnchair.util.repeatOnAttached
import com.android.launcher3.R
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Content of the command widget, drawn like the command bar's result box: a dark rounded panel with
 * an accent outline and monospace text in the accent color. The first line is the command
 * ("$ clima recife"), under it the result and, at the bottom, when it was read. While the widget is
 * on screen it runs the command again every interval chosen for it, and a tap runs it now.
 */
@SuppressLint("ViewConstructor")
class CommandWidgetView(context: Context, private val appWidgetId: Int) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val accent = PhosphorColorToken(1f).resolveColor(context)
    private val dim = PhosphorColorToken(0.6f).resolveColor(context)

    private val header = text(11f, dim).apply { maxLines = 1 }
    private val body = text(13f, accent).apply { maxLines = 12 }
    private val footer = text(10f, dim).apply { maxLines = 1 }

    init {
        orientation = VERTICAL
        val pad = (12 * density).toInt()
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            setColor(Color.argb(225, 14, 14, 14))
            cornerRadius = 16 * density
            setStroke((2 * density).toInt(), PhosphorColorToken(0.4f).resolveColor(context))
        }
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = (6 * density).toInt() })
        addView(footer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = (6 * density).toInt() })
        setOnClickListener { taps.trySend(Unit) }
        repeatOnAttached {
            CommandWidgetStore.changes.collectLatest { loop() }
        }
    }

    /** A tap: [loop] stops waiting and runs the command now. */
    private val taps = Channel<Unit>(Channel.CONFLATED)
    private var running = false

    private fun text(sizeSp: Float, color: Int) = TextView(context).apply {
        setTextColor(color)
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.START
    }

    /**
     * Shows what is saved, then runs the command whenever its schedule says: at once when never
     * read or when a scheduled time passed unseen, and then at each one. A widget without a schedule
     * only runs on a tap (or once, when it has no result yet).
     */
    private suspend fun loop() {
        val command = CommandWidgetStore.command(context, appWidgetId)
        val schedule = CommandWidgetStore.schedule(context, appWidgetId)
        render(command)
        if (command.isBlank()) return
        var tapped = false
        while (true) {
            val last = CommandWidgetStore.resultAt(context, appWidgetId)
            if (tapped || schedule.isDue(System.currentTimeMillis(), last)) run(command)
            tapped = false
            // Waits for the next run or a tap; the footer's age is refreshed each minute along the way.
            val until = schedule.nextRun(System.currentTimeMillis(), CommandWidgetStore.resultAt(context, appWidgetId)) ?: Long.MAX_VALUE
            while (!tapped && System.currentTimeMillis() < until) {
                val step = minOf(60_000L, until - System.currentTimeMillis()).coerceAtLeast(250L)
                tapped = withTimeoutOrNull(step) { taps.receive() } != null
                if (!tapped) render(command)
            }
        }
    }

    private suspend fun run(command: String) {
        if (running) return
        running = true
        footer.text = context.getString(R.string.command_widget_running)
        try {
            // The command runs apart from this view, so a slow one neither blocks a new configuration nor a tap.
            val read = CoroutineScope(Dispatchers.Default).async { CommandLineRunner.read(context, command) }
            val result = withTimeoutOrNull(RUN_TIMEOUT_MS) { read.await() }
            if (result == null) {
                CommandWidgetStore.saveResult(context, appWidgetId, context.getString(R.string.command_widget_timeout), failed = true)
            } else {
                val text = result.output?.takeIf { it.isNotBlank() }
                    ?: result.analysis.preview.ifBlank { result.analysis.previewTitle }.ifBlank { context.getString(R.string.command_widget_no_result) }
                CommandWidgetStore.saveResult(context, appWidgetId, text.trim(), failed = !result.ran)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CommandWidgetStore.saveResult(context, appWidgetId, e.message ?: e.javaClass.simpleName, failed = true)
        } finally {
            running = false
        }
        render(command)
    }

    private fun render(command: String) {
        if (command.isBlank()) {
            header.text = "$"
            body.text = context.getString(R.string.command_widget_empty)
            body.setTextColor(dim)
            footer.text = ""
            return
        }
        header.text = "$ $command"
        val result = CommandWidgetStore.result(context, appWidgetId)
        val failed = CommandWidgetStore.failed(context, appWidgetId)
        body.text = result ?: "…"
        body.setTextColor(if (failed) Color.rgb(255, 107, 107) else accent)
        val at = CommandWidgetStore.resultAt(context, appWidgetId)
        val schedule = CommandWidgetStore.schedule(context, appWidgetId)
        val read = if (at > 0) {
            DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
        } else {
            ""
        }
        footer.text = listOf(
            read,
            describe(context, schedule),
        ).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    companion object {
        private const val RUN_TIMEOUT_MS = 30_000L

        /** The schedule in a few words: "every 5 min", "daily 08:00", "Mon, Fri 09:00", "day 15 at 06:00", "tap to run". */
        fun describe(context: Context, schedule: CommandWidgetSchedule): String {
            val time = android.text.format.DateFormat.getTimeFormat(context).format(
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, schedule.hour)
                    set(Calendar.MINUTE, schedule.minute)
                }.time,
            )
            return when (schedule.kind) {
                CommandWidgetSchedule.Kind.MANUAL -> context.getString(R.string.command_widget_tap_to_run)

                CommandWidgetSchedule.Kind.INTERVAL -> context.getString(R.string.command_widget_every, intervalLabel(context, schedule.intervalMinutes))

                CommandWidgetSchedule.Kind.DAILY -> context.getString(R.string.command_widget_daily_at, time)

                CommandWidgetSchedule.Kind.WEEKLY -> {
                    val names = java.text.DateFormatSymbols.getInstance().shortWeekdays
                    context.getString(R.string.command_widget_weekly_at, schedule.weekdays.sorted().joinToString(", ") { names[it] }, time)
                }

                CommandWidgetSchedule.Kind.MONTHLY -> context.getString(R.string.command_widget_monthly_at, schedule.dayOfMonth, time)
            }
        }

        /** "5 min", "1 h". */
        fun intervalLabel(context: Context, minutes: Int): String = if (minutes % 60 == 0) {
            context.getString(R.string.command_widget_hours, minutes / 60)
        } else {
            context.getString(R.string.command_widget_minutes, minutes)
        }

        @JvmStatic
        fun isFor(view: android.view.View?, appWidgetId: Int): Boolean = view is CommandWidgetView && view.appWidgetId == appWidgetId

        /** The launcher's own widget, or null when [info] belongs to any other provider. */
        @JvmStatic
        fun create(context: Context, appWidgetId: Int, info: AppWidgetProviderInfo?): ViewGroup? {
            if (appWidgetId <= 0 || info?.provider != CommandAppWidgetProvider.componentName) return null
            return CommandWidgetView(context, appWidgetId)
        }
    }
}
