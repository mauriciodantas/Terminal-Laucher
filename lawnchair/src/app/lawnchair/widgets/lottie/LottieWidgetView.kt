package app.lawnchair.widgets.lottie

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
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

    private fun showEmpty(show: Boolean) {
        empty.visibility = if (show) View.VISIBLE else View.GONE
        animation.visibility = if (show) View.INVISIBLE else View.VISIBLE
    }

    companion object {
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
