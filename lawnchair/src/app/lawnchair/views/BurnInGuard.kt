package app.lawnchair.views

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import kotlin.random.Random

/**
 * Protects OLED panels from burn-in while the home screen sits untouched: every [SHIFT_INTERVAL_MS]
 * the [content] slides a few dp to a new spot, and after [IDLE_MS] without touches a black layer
 * dims everything. Any touch brings the brightness back. It never takes touches itself.
 */
class BurnInGuard(context: Context, private val content: View) : View(context) {

    private val density = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    private val shift = object : Runnable {
        override fun run() {
            val range = (MAX_SHIFT_DP * density).toInt()
            content.animate()
                .translationX(Random.nextInt(-range, range + 1).toFloat())
                .translationY(Random.nextInt(-range, range + 1).toFloat())
                .setDuration(SHIFT_DURATION_MS)
                .start()
            handler.postDelayed(this, SHIFT_INTERVAL_MS)
        }
    }

    private val dim = Runnable {
        animate().alpha(DIM_ALPHA).setDuration(DIM_DURATION_MS).start()
    }

    init {
        setBackgroundColor(Color.BLACK)
        alpha = 0f
        isClickable = false
        isFocusable = false
    }

    /** Starts shifting and the idle countdown; call when the launcher is in front. */
    fun start() {
        stop()
        handler.postDelayed(shift, SHIFT_INTERVAL_MS)
        handler.postDelayed(dim, IDLE_MS)
    }

    /** Stops everything and restores full brightness; call when the launcher leaves the front. */
    fun stop() {
        handler.removeCallbacks(shift)
        handler.removeCallbacks(dim)
        animate().cancel()
        alpha = 0f
    }

    /** Call on every touch: wakes the screen and restarts the idle countdown. */
    fun onUserActivity() {
        handler.removeCallbacks(dim)
        if (alpha > 0f) animate().alpha(0f).setDuration(WAKE_DURATION_MS).start()
        handler.postDelayed(dim, IDLE_MS)
    }

    private companion object {
        const val MAX_SHIFT_DP = 4
        const val SHIFT_INTERVAL_MS = 60_000L
        const val SHIFT_DURATION_MS = 2_000L
        const val IDLE_MS = 60_000L
        const val DIM_ALPHA = 0.7f
        const val DIM_DURATION_MS = 3_000L
        const val WAKE_DURATION_MS = 150L
    }
}
