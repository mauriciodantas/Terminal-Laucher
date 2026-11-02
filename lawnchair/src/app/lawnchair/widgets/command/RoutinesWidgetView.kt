package app.lawnchair.widgets.command

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.lawnchair.command.CommandExtras
import app.lawnchair.command.JobStore
import app.lawnchair.command.RoutineStore
import app.lawnchair.command.Routines
import app.lawnchair.theme.color.tokens.PhosphorColorToken
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.RoutinesList
import app.lawnchair.util.repeatOnAttached
import com.android.launcher3.R
import com.android.launcher3.widget.RoundedCornerEnforcement
import kotlinx.coroutines.flow.collect

/**
 * Content of the routines widget, drawn like the command widget: a dark rounded panel with an accent
 * outline and monospace text. A title, then one line for each scheduled command and rule (the first
 * ones that fit, and "+N" for the rest). It redraws when a job is saved, cancelled or fired, and a tap
 * opens the routines screen.
 */
@SuppressLint("ViewConstructor")
class RoutinesWidgetView(context: Context, private val appWidgetId: Int) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val accent = PhosphorColorToken(1f).resolveColor(context)
    private val dim = PhosphorColorToken(0.6f).resolveColor(context)

    private val title = text(11f, dim).apply { text = context.getString(R.string.widget_routines_title) }
    private val lines = List(MAX_LINES) { text(13f, accent) }
    private val more = text(11f, dim)

    init {
        orientation = VERTICAL
        val pad = (12 * density).toInt()
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            setColor(Color.argb(225, 14, 14, 14))
            cornerRadius = RoundedCornerEnforcement.computeEnforcedRadius(context).takeIf { it > 0f } ?: (16 * density)
            setStroke((2 * density).toInt(), PhosphorColorToken(0.4f).resolveColor(context))
        }
        addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        lines.forEach { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = (4 * density).toInt() }) }
        addView(more, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = (4 * density).toInt() })
        setOnClickListener {
            val intent = PreferenceActivity.createIntent(context, RoutinesList)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
        repeatOnAttached { RoutinesFeed.changes.collect { render() } }
    }

    private fun text(sizeSp: Float, color: Int) = TextView(context).apply {
        setTextColor(color)
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.START
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        render()
    }

    private fun render() {
        val runs = RoutineStore.load(context)
        val all = Routines.widgetLines(CommandExtras(jobs = JobStore.load(context), routineRuns = runs), runs)
        val room = ((height / density - CHROME_DP) / LINE_DP).toInt().coerceIn(1, MAX_LINES)
        // The last row of the room is the "+N" when something does not fit.
        val shown = if (all.size > room) room - 1 else all.size
        lines.forEachIndexed { i, view ->
            val line = when {
                i < shown -> all[i]
                i == 0 -> context.getString(R.string.widget_routines_empty)
                else -> null
            }
            view.visibility = if (line == null) GONE else VISIBLE
            view.text = line.orEmpty()
            view.setTextColor(if (all.isEmpty()) dim else accent)
        }
        val rest = all.size - shown
        more.visibility = if (rest > 0 && all.size > room) VISIBLE else GONE
        more.text = context.getString(R.string.widget_routines_more, rest)
    }

    companion object {
        private const val MAX_LINES = 6

        /** Height of the title and the margins, and of one line, in dp: how many lines fit in the widget. */
        private const val CHROME_DP = 44
        private const val LINE_DP = 22

        @JvmStatic
        fun isFor(view: android.view.View?, appWidgetId: Int): Boolean = view is RoutinesWidgetView && view.appWidgetId == appWidgetId

        /** The launcher's own widget, or null when [info] belongs to any other provider. */
        @JvmStatic
        fun create(context: Context, appWidgetId: Int, info: AppWidgetProviderInfo?): ViewGroup? {
            if (appWidgetId <= 0 || info?.provider != RoutinesAppWidgetProvider.componentName) return null
            return RoutinesWidgetView(context, appWidgetId)
        }
    }
}
