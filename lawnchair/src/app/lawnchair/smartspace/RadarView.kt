package app.lawnchair.smartspace

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import app.lawnchair.theme.color.tokens.PhosphorColorToken
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Motion tracker readout for the home screen: concentric rings, a rotating sweep and one hostile contact.
 */
class RadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val amber = PhosphorColorToken(1f).resolveColor(context)
    private val dim = PhosphorColorToken(0.62f).resolveColor(context)
    private val line = PhosphorColorToken(0.32f).resolveColor(context)
    private val sweepEdge = ColorUtils.setAlphaComponent(amber, 0x55)
    private val sweepClear = ColorUtils.setAlphaComponent(amber, 0)
    private val alert = 0xFFFF5A45.toInt()

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var angle = 0f
    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 4000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            angle = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - strokePaint.strokeWidth

        strokePaint.color = dim
        canvas.drawCircle(cx, cy, r, strokePaint)
        strokePaint.color = line
        canvas.drawCircle(cx, cy, r * 0.66f, strokePaint)
        canvas.drawCircle(cx, cy, r * 0.33f, strokePaint)
        canvas.drawLine(cx - r, cy, cx + r, cy, strokePaint)
        canvas.drawLine(cx, cy - r, cx, cy + r, strokePaint)

        canvas.save()
        canvas.rotate(angle, cx, cy)
        sweepPaint.shader = SweepGradient(
            cx,
            cy,
            intArrayOf(sweepClear, sweepClear, sweepEdge),
            floatArrayOf(0f, 0.8f, 1f),
        )
        canvas.drawCircle(cx, cy, r, sweepPaint)
        strokePaint.color = amber
        canvas.drawLine(cx, cy, cx + r, cy, strokePaint)
        canvas.restore()

        // Fixed contact at ~42 degrees, fading as the sweep passes over it.
        val contactAngle = Math.toRadians(-48.0)
        val bx = cx + (r * 0.7f * cos(contactAngle)).toFloat()
        val by = cy + (r * 0.7f * sin(contactAngle)).toFloat()
        val since = ((angle - 312f) + 360f) % 360f
        val alpha = (255 - since / 360f * 200f).toInt().coerceIn(55, 255)
        fillPaint.color = alert
        fillPaint.alpha = alpha
        canvas.drawCircle(bx, by, 3f * resources.displayMetrics.density, fillPaint)

        // Sonar ping: a ring expands from the contact right after the sweep passes over it.
        if (since < 120f) {
            val t = since / 120f
            strokePaint.color = alert
            strokePaint.alpha = ((1f - t) * 190).toInt()
            canvas.drawCircle(bx, by, (3f + 14f * t) * resources.displayMetrics.density, strokePaint)
        }

        fillPaint.color = amber
        fillPaint.alpha = 255
        canvas.drawCircle(cx, cy, 1.5f * resources.displayMetrics.density, fillPaint)
    }
}
