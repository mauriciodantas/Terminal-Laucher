package app.lawnchair.smartspace

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import app.lawnchair.smartspace.glance.GlanceAnimationStyle
import app.lawnchair.theme.color.tokens.PhosphorColorToken
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The terminal readouts that can sit beside the big value of the At a Glance panel: the motion
 * tracker (concentric rings, a rotating sweep and one hostile contact), a sonar, an oscilloscope
 * and a level meter. All of them run off one looping clock and are drawn in the phosphor colors.
 */
class GlanceAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var style: GlanceAnimationStyle = GlanceAnimationStyle.RADAR
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val wavePath = Path()

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
        when (style) {
            GlanceAnimationStyle.SONAR -> drawSonar(canvas)
            GlanceAnimationStyle.WAVE -> drawWave(canvas)
            GlanceAnimationStyle.BARS -> drawBars(canvas)
            else -> drawRadar(canvas)
        }
    }

    /** Three rings expanding from the center, a third of a cycle apart, fading as they grow. */
    private fun drawSonar(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - strokePaint.strokeWidth
        val phase = angle / 360f
        for (i in 0 until 3) {
            val t = (phase + i / 3f) % 1f
            strokePaint.color = amber
            strokePaint.alpha = ((1f - t) * 220).toInt()
            canvas.drawCircle(cx, cy, r * t, strokePaint)
        }
        strokePaint.alpha = 255
        fillPaint.color = amber
        fillPaint.alpha = 255
        canvas.drawCircle(cx, cy, 2f * resources.displayMetrics.density, fillPaint)
    }

    /** A sine trace that scrolls across the screen, with a faint center line like an oscilloscope. */
    private fun drawWave(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val mid = h / 2f
        strokePaint.color = line
        canvas.drawLine(0f, mid, w, mid, strokePaint)
        val amplitude = h * 0.32f
        val shift = angle / 360f * 2f * PI.toFloat()
        wavePath.rewind()
        var x = 0f
        while (x <= w) {
            val phase = x / w * 2f * PI.toFloat() * 1.5f - shift
            // Two harmonics so the trace is not a plain sine.
            val y = mid - amplitude * (0.75f * sin(phase) + 0.25f * sin(phase * 3f))
            if (x == 0f) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
            x += 2f
        }
        strokePaint.color = amber
        strokePaint.strokeWidth = 1.5f * resources.displayMetrics.density
        canvas.drawPath(wavePath, strokePaint)
        strokePaint.strokeWidth = resources.displayMetrics.density
    }

    /** Five level-meter bars that rise and fall out of step with each other. */
    private fun drawBars(canvas: Canvas) {
        val count = 5
        val w = width.toFloat()
        val h = height.toFloat()
        val gap = w * 0.06f
        val barWidth = (w - gap * (count - 1)) / count
        val phase = angle / 360f * 2f * PI.toFloat()
        fillPaint.color = amber
        fillPaint.alpha = 255
        for (i in 0 until count) {
            val level = 0.2f + 0.8f * abs(sin(phase + i * 1.1f))
            val left = i * (barWidth + gap)
            canvas.drawRect(left, h * (1f - level), left + barWidth, h, fillPaint)
        }
    }

    private fun drawRadar(canvas: Canvas) {
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
