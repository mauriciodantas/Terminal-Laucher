package app.lawnchair.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.provider.Settings
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import app.lawnchair.theme.color.tokens.PhosphorColorToken

/**
 * CRT screen layer drawn over the whole launcher: scanlines, a slow rolling brightness band,
 * phosphor flicker and a vignette, like the Nostromo terminals. It never takes touches.
 *
 * [pulse] plays a short power-on flash and is used on screen changes. When the system animator
 * scale is 0 the layer stays static (scanlines and vignette only). [intensity] scales every part of
 * the layer at once, 1 being the original look.
 */
class CrtOverlayView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val animated = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) > 0f

    private val phosphor = PhosphorColorToken(1f).resolveColor(context)

    private val scanPaint = Paint().apply {
        val period = (3 * density).toInt().coerceAtLeast(3)
        val tile = Bitmap.createBitmap(1, period, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            setPixel(0, 0, Color.argb(0x2E, 0, 0, 0))
        }
        shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
    private val bandPaint = Paint()
    private val vignettePaint = Paint()
    private val flickerPaint = Paint()
    private val flashPaint = Paint()

    private val bandHeight = 140 * density
    private var phase = 0f
    private var flash = 0f

    var intensity = 1f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field == clamped) return
            field = clamped
            val alpha = (clamped * 255).toInt()
            scanPaint.alpha = alpha
            bandPaint.alpha = alpha
            vignettePaint.alpha = alpha
            invalidate()
        }

    private val ticker = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 9000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    private val flashAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
        duration = 280
        addUpdateListener {
            flash = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Short bright flash, as when a terminal screen switches. */
    fun pulse() {
        if (!animated || visibility != VISIBLE) return
        flashAnimator.cancel()
        flashAnimator.start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animated && visibility == VISIBLE) ticker.start()
    }

    /** A hidden layer must not keep animating. */
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView !== this) return
        if (visibility == VISIBLE) {
            if (animated && isAttachedToWindow && !ticker.isStarted) ticker.start()
        } else {
            ticker.cancel()
            flashAnimator.cancel()
        }
    }

    override fun onDetachedFromWindow() {
        ticker.cancel()
        flashAnimator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        vignettePaint.shader = RadialGradient(
            w / 2f,
            h / 2f,
            maxOf(w, h) * 0.75f,
            intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(0x66, 0, 0, 0)),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        bandPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            bandHeight,
            intArrayOf(
                ColorUtils.setAlphaComponent(phosphor, 0),
                ColorUtils.setAlphaComponent(phosphor, 0x12),
                ColorUtils.setAlphaComponent(phosphor, 0),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        canvas.drawRect(0f, 0f, w, h, scanPaint)

        if (animated) {
            // Rolling band.
            val y = phase * (h + bandHeight) - bandHeight
            canvas.save()
            canvas.translate(0f, y)
            canvas.drawRect(0f, 0f, w, bandHeight, bandPaint)
            canvas.restore()

            // Phosphor flicker: a faint dark veil that changes every ~70 ms, with a rare deeper dip.
            val bucket = (System.currentTimeMillis() / 70).toInt()
            val noise = hash(bucket)
            var veil = (noise % 100) / 100f * 0.05f
            if (hash(bucket / 3) % 53 == 0) veil = 0.16f
            flickerPaint.color = Color.argb((veil * intensity * 255).toInt(), 0, 0, 0)
            canvas.drawRect(0f, 0f, w, h, flickerPaint)

            if (flash > 0f) {
                flashPaint.color = ColorUtils.setAlphaComponent(phosphor, (flash * intensity * 0x38).toInt())
                canvas.drawRect(0f, 0f, w, h, flashPaint)
            }
        }

        canvas.drawRect(0f, 0f, w, h, vignettePaint)
    }

    private fun hash(seed: Int): Int {
        var x = seed * 0x45d9f3b
        x = (x xor (x ushr 16)) * 0x45d9f3b
        x = x xor (x ushr 16)
        return x and 0x7fffffff
    }
}
