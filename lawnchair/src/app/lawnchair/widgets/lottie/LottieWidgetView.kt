package app.lawnchair.widgets.lottie

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import app.lawnchair.command.CommandLine
import app.lawnchair.theme.color.tokens.PhosphorColorToken
import app.lawnchair.util.repeatOnAttached
import app.lawnchair.widgets.lottie.LottieWidgetStore.Playback
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieCompositionFactory
import com.android.launcher3.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    init {
        addView(animation, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(empty, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        empty.visibility = View.GONE
        animation.setOnClickListener { onTap() }
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

    /** The playback mode applied to the animation on screen; null until one is. */
    private var playback: Playback? = null

    private fun onTap() {
        val command = LottieWidgetStore.command(context, appWidgetId)
        if (playback == Playback.ONCE_ON_TAP) {
            // From the start, also when a previous tap's run has not ended.
            animation.playAnimation()
        } else if (command.isEmpty()) {
            if (animation.isAnimating) animation.pauseAnimation() else animation.resumeAnimation()
        }
        if (command.isEmpty()) return
        val scope = attachedScope ?: return
        scope.launch {
            val result = CommandLine.run(context, command)
            if (!result.ran) {
                val reason = result.analysis.preview.ifBlank { result.analysis.previewTitle }
                Toast.makeText(context, context.getString(R.string.lottie_widget_command_failed, command, reason), Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private suspend fun load() {
        val file = LottieWidgetStore.file(context, appWidgetId)
        val composition = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext null
            // The modification time is part of the key, so a new import is not served from the cache.
            val key = "lottie_widget_${appWidgetId}_${file.lastModified()}"
            runCatching { file.inputStream().use { LottieCompositionFactory.fromInputStreamSync(context, it, key) } }
                .getOrNull()?.value
        }
        if (composition == null) {
            animation.cancelAnimation()
            showEmpty(true)
            return
        }
        showEmpty(false)
        applyThemeColors(LottieWidgetStore.themeColors(context, appWidgetId))
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
