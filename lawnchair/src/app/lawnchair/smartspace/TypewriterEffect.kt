package app.lawnchair.smartspace

import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.view.animation.LinearInterpolator
import android.widget.TextView
import com.android.launcher3.R

/** True unless the user turned system animations off. */
fun animationsEnabled(context: Context): Boolean = Settings.Global.getFloat(
    context.contentResolver,
    Settings.Global.ANIMATOR_DURATION_SCALE,
    1f,
) > 0f

/**
 * Reveals [full] one character at a time, like a terminal printing a line. The text is set
 * immediately when animations are off.
 */
fun TextView.typeIn(full: CharSequence, startDelayMs: Long = 0L, msPerChar: Long = 28L) {
    if (!animationsEnabled(context) || full.isEmpty()) {
        text = full
        return
    }
    (getTag(R.id.nostromo_type_animator) as? ValueAnimator)?.cancel()
    text = ""
    val animator = ValueAnimator.ofInt(0, full.length).apply {
        duration = full.length * msPerChar
        startDelay = startDelayMs
        interpolator = LinearInterpolator()
        addUpdateListener { text = full.subSequence(0, it.animatedValue as Int) }
    }
    setTag(R.id.nostromo_type_animator, animator)
    animator.start()
}
