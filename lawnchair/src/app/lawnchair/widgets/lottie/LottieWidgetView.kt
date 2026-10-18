package app.lawnchair.widgets.lottie

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import app.lawnchair.command.CommandLineRunner
import app.lawnchair.theme.color.tokens.PhosphorColorToken
import app.lawnchair.util.repeatOnAttached
import app.lawnchair.widgets.lottie.LottieWidgetStore.Playback
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieCompositionFactory
import com.android.launcher3.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Content of the Lottie widget: the animation imported for this widget id, played as its
 * [LottieWidgetStore.Playback] says. A tap runs the command line set for the widget; in a loop
 * without a command it pauses and resumes the animation instead, and in [Playback.ONCE_ON_TAP] it
 * also plays the animation once. Without an animation it shows how to pick one.
 */
@SuppressLint("ViewConstructor")
class LottieWidgetView(context: Context, private val appWidgetId: Int) : FrameLayout(context) {

    private val animation = LottieAnimationView(context).apply {
        setFailureListener { showEmpty(true) }
    }

    private val empty = TextView(context).apply {
        gravity = Gravity.CENTER
        text = context.getString(R.string.lottie_widget_empty)
        setTextColor(PhosphorColorToken(1f).resolveColor(context))
        val pad = (12 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
    }

    private val resultView = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(PhosphorColorToken(1f).resolveColor(context))
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        maxLines = 8
        ellipsize = TextUtils.TruncateAt.END
        val pad = (12 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            setColor(Color.argb(225, 14, 14, 14))
            cornerRadius = 16 * resources.displayMetrics.density
            setStroke((2 * resources.displayMetrics.density).toInt(), PhosphorColorToken(0.4f).resolveColor(context))
        }
        visibility = View.GONE
    }

    init {
        addView(animation, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(empty, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val resultParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
            val margin = (4 * resources.displayMetrics.density).toInt()
            setMargins(margin, margin, margin, margin)
        }
        addView(resultView, resultParams)
        empty.visibility = View.GONE
        val clickListener = OnClickListener { onTap() }
        setOnClickListener(clickListener)
        animation.setOnClickListener(clickListener)
        resultView.setOnClickListener(clickListener)
        repeatOnAttached {
            attachedScope = this
            try {
                LottieWidgetStore.changes.collectLatest { load() }
            } finally {
                attachedScope = null
            }
        }
    }

    /** Runs while the view is attached; a tap's command runs in it. */
    private var attachedScope: CoroutineScope? = null

    /** Job for timing out the command result view and returning the animation. */
    private var resultTimerJob: Job? = null

    /** The playback mode applied to the animation on screen; null until one is. */
    private var playback: Playback? = null

    private fun showResult(text: String?) {
        resultTimerJob?.cancel()
        resultTimerJob = null
        if (!text.isNullOrBlank()) {
            animation.visibility = View.GONE
            resultView.text = text
            resultView.visibility = View.VISIBLE
        } else {
            resultView.visibility = View.GONE
            animation.visibility = View.VISIBLE
        }
    }

    private fun scheduleResultRevert(delayMs: Long) {
        resultTimerJob?.cancel()
        if (delayMs <= 0L) {
            showResult(null)
            LottieWidgetStore.saveLastResult(context, appWidgetId, null)
            return
        }
        val scope = attachedScope ?: MainScope()
        resultTimerJob = scope.launch {
            delay(delayMs)
            showResult(null)
            LottieWidgetStore.saveLastResult(context, appWidgetId, null)
        }
    }

    private fun onTap() {
        val command = LottieWidgetStore.command(context, appWidgetId)
        val showResult = LottieWidgetStore.showResult(context, appWidgetId)
        if (playback == Playback.ONCE_ON_TAP) {
            // From the start, also when a previous tap's run has not ended.
            animation.playAnimation()
        } else if (command.isEmpty()) {
            if (animation.isAnimating) animation.pauseAnimation() else animation.resumeAnimation()
        }
        if (command.isEmpty()) return
        if (showResult) {
            showResult("> $command...")
        }
        val scope = attachedScope ?: MainScope()
        scope.launch {
            val result = CommandLineRunner.run(context, command)
            val durationSec = LottieWidgetStore.resultDurationSeconds(context, appWidgetId)
            val durationMs = durationSec * 1000L
            if (!result.ran) {
                val reason = result.analysis.preview.ifBlank { result.analysis.previewTitle }
                if (showResult) {
                    val msg = "ERR: $reason"
                    showResult(msg)
                    LottieWidgetStore.saveLastResult(context, appWidgetId, msg)
                    scheduleResultRevert(durationMs)
                }
                Toast.makeText(context, context.getString(R.string.lottie_widget_command_failed, command, reason), Toast.LENGTH_LONG)
                    .show()
            } else if (showResult) {
                val text = result.output?.takeIf { it.isNotBlank() }
                    ?: result.analysis.preview.ifBlank { result.analysis.previewTitle }
                if (text.isNotBlank()) {
                    showResult(text)
                    LottieWidgetStore.saveLastResult(context, appWidgetId, text)
                    scheduleResultRevert(durationMs)
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                } else {
                    showResult(null)
                    LottieWidgetStore.saveLastResult(context, appWidgetId, null)
                }
            }
        }
    }

    private suspend fun load() {
        val file = LottieWidgetStore.file(context, appWidgetId)
        val composition = withContext(Dispatchers.IO) {
            if (file.exists()) {
                // The modification time is part of the key, so a new import is not served from the cache.
                val key = "lottie_widget_${appWidgetId}_${file.lastModified()}"
                runCatching { file.inputStream().use { LottieCompositionFactory.fromInputStreamSync(context, it, key) } }
                    .getOrNull()?.value
            } else {
                DefaultWidgetAnimation.load(context)
            }
        }
        if (composition == null) {
            animation.cancelAnimation()
            showEmpty(true)
            return
        }
        showEmpty(false)
        applyThemeColors(LottieWidgetStore.themeColors(context, appWidgetId))
        if (LottieWidgetStore.showResult(context, appWidgetId)) {
            val last = LottieWidgetStore.lastResult(context, appWidgetId)
            if (!last.isNullOrBlank()) {
                val durationSec = LottieWidgetStore.resultDurationSeconds(context, appWidgetId)
                val durationMs = durationSec * 1000L
                val lastAt = LottieWidgetStore.lastResultAt(context, appWidgetId)
                val elapsed = System.currentTimeMillis() - lastAt
                val remaining = durationMs - elapsed
                if (remaining > 0) {
                    showResult(last)
                    scheduleResultRevert(remaining)
                } else {
                    showResult(null)
                    LottieWidgetStore.saveLastResult(context, appWidgetId, null)
                }
            } else {
                showResult(null)
            }
        } else {
            showResult(null)
        }
        val mode = LottieWidgetStore.playback(context, appWidgetId)
        if (animation.composition === composition && playback == mode) return
        animation.setComposition(composition)
        playback = mode
        when (mode) {
            Playback.LOOP -> {
                animation.repeatCount = ValueAnimator.INFINITE
                animation.playAnimation()
            }

            Playback.ONCE_ON_TAP -> {
                animation.cancelAnimation()
                animation.repeatCount = 0
                animation.progress = 0f
            }
        }
    }

    /** Paints the animation in the theme accent, or gives it back its own colors. */
    private fun applyThemeColors(on: Boolean) {
        if (on) {
            animation.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = themeFilter(context) })
        } else {
            animation.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    private fun showEmpty(show: Boolean) {
        empty.visibility = if (show) View.VISIBLE else View.GONE
        animation.visibility = if (show) View.INVISIBLE else View.VISIBLE
    }

    companion object {
        /** How bright the darkest parts stay, so dark lines still show against the background. */
        private const val FLOOR = 0.35f

        /**
         * Keeps the animation's light and shade but paints it in the theme accent, the color the
         * terminal draws its text in, so it reads in the light and the dark mode alike. Black lands
         * on a dim accent instead of black, which would vanish over a dark background.
         */
        fun themeFilter(context: Context): ColorMatrixColorFilter {
            val accent = PhosphorColorToken(1f).resolveColor(context)
            val lr = 0.2126f
            val lg = 0.7152f
            val lb = 0.0722f
            val rows = listOf(Color.red(accent), Color.green(accent), Color.blue(accent)).flatMap { channel ->
                val c = channel / 255f
                val k = c * (1 - FLOOR)
                listOf(k * lr, k * lg, k * lb, 0f, c * FLOOR * 255)
            }
            return ColorMatrixColorFilter(ColorMatrix((rows + listOf(0f, 0f, 0f, 1f, 0f)).toFloatArray()))
        }

        /** Whether [view] is the content already built for [appWidgetId]. */
        @JvmStatic
        fun isFor(view: View?, appWidgetId: Int): Boolean = view is LottieWidgetView && view.appWidgetId == appWidgetId

        /** The launcher's own widget, or null when [info] belongs to any other provider. */
        @JvmStatic
        fun create(context: Context, appWidgetId: Int, info: AppWidgetProviderInfo?): ViewGroup? {
            if (appWidgetId <= 0 || info?.provider != LottieAppWidgetProvider.componentName) return null
            return LottieWidgetView(context, appWidgetId)
        }
    }
}
