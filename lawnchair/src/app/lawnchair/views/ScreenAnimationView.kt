package app.lawnchair.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import app.lawnchair.command.CustomAnimation
import app.lawnchair.command.ThemeColor
import app.lawnchair.command.ThemeSpecs
import app.lawnchair.theme.color.tokens.PhosphorColorToken
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import com.caverock.androidsvg.PreserveAspectRatio
import com.caverock.androidsvg.SVG
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin
import org.json.JSONObject

/**
 * One animation that plays over the whole launcher: the monitor, falling snow, and so on. The view
 * draws a frame by asking the animation to paint [intensity] (0 to 1) at the time [now]. When the
 * system animator scale is 0 [animated] is false and an animation paints a still frame.
 */
abstract class ScreenAnimation {
    abstract fun draw(canvas: Canvas, width: Float, height: Float, now: Long, intensity: Float, animated: Boolean)

    /** A short flash when the screen changes; most animations ignore it. */
    open fun pulse(now: Long) = Unit

    companion object {
        /**
         * The animation called [key] (one of the keys the theme file knows, or "custom" for [custom],
         * the one a theme brings); the monitor for an unknown one.
         */
        fun create(context: Context, key: String?, custom: CustomAnimation? = null): ScreenAnimation = when {
            key == "custom" && custom is CustomAnimation.Particles -> CustomParticleAnimation(context, custom)
            key == "custom" && custom is CustomAnimation.Lottie -> LottieOverlayAnimation(custom.json) ?: MonitorAnimation(context)
            else -> byKey(context, key)
        }

        private fun byKey(context: Context, key: String?): ScreenAnimation = when (key) {
            "snow" -> SnowAnimation(context)
            "petals" -> PetalsAnimation(context)
            "stars" -> StarsAnimation(context)
            "rain" -> RainAnimation(context)
            else -> MonitorAnimation(context)
        }
    }
}

/**
 * The layer drawn over the launcher: it plays the chosen [ScreenAnimation] and never takes touches.
 * Hidden, it does not run. A new animation is one more subclass and one more key in
 * [ScreenAnimation.create] and in the theme file's list.
 */
class ScreenAnimationView(context: Context) : View(context) {

    private val animated = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) > 0f

    private var animation: ScreenAnimation = ScreenAnimation.create(context, null)
    private var key: String? = null

    /** From 0 (invisible) to 1 (the full look). */
    var intensity = 1f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field == clamped) return
            field = clamped
            invalidate()
        }

    /**
     * Plays the animation called [key], keeping the one that already plays when it is the same. For
     * "custom", [customJson] is the animation the theme brought, as it is stored.
     */
    fun play(key: String?, customJson: String = "") {
        val id = if (key == "custom") "custom:$customJson" else key
        if (id == this.key) return
        this.key = id
        val custom = if (key == "custom") {
            runCatching { ThemeSpecs.customAnimation(JSONObject(customJson)) }.getOrNull()
        } else {
            null
        }
        animation = ScreenAnimation.create(context, key, custom)
        invalidate()
    }

    private val ticker = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { invalidate() }
    }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Short bright flash, as when a terminal screen switches. */
    fun pulse() {
        if (!animated || visibility != VISIBLE) return
        animation.pulse(SystemClock.uptimeMillis())
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
        }
    }

    override fun onDetachedFromWindow() {
        ticker.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        animation.draw(canvas, width.toFloat(), height.toFloat(), SystemClock.uptimeMillis(), intensity, animated)
    }
}

/**
 * The CRT screen of the Nostromo terminals: scanlines, a slow rolling brightness band, phosphor
 * flicker, a vignette and a power-on flash on screen changes.
 */
private class MonitorAnimation(context: Context) : ScreenAnimation() {

    private val density = context.resources.displayMetrics.density
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
    private var flashStart = 0L
    private var sizeW = -1f
    private var sizeH = -1f

    override fun pulse(now: Long) {
        flashStart = now
    }

    private fun resize(w: Float, h: Float) {
        if (w == sizeW && h == sizeH) return
        sizeW = w
        sizeH = h
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

    override fun draw(canvas: Canvas, width: Float, height: Float, now: Long, intensity: Float, animated: Boolean) {
        resize(width, height)
        val alpha = (intensity * 255).toInt()
        scanPaint.alpha = alpha
        bandPaint.alpha = alpha
        vignettePaint.alpha = alpha

        canvas.drawRect(0f, 0f, width, height, scanPaint)

        if (animated) {
            // Rolling band.
            val phase = (now % 9000L) / 9000f
            val y = phase * (height + bandHeight) - bandHeight
            canvas.save()
            canvas.translate(0f, y)
            canvas.drawRect(0f, 0f, width, bandHeight, bandPaint)
            canvas.restore()

            // Phosphor flicker: a faint dark veil that changes every ~70 ms, with a rare deeper dip.
            val bucket = (now / 70).toInt()
            var veil = (hash(bucket) % 100) / 100f * 0.05f
            if (hash(bucket / 3) % 53 == 0) veil = 0.16f
            flickerPaint.color = Color.argb((veil * intensity * 255).toInt(), 0, 0, 0)
            canvas.drawRect(0f, 0f, width, height, flickerPaint)

            val flash = 1f - (now - flashStart) / FLASH_MS.toFloat()
            if (flash > 0f) {
                flashPaint.color = ColorUtils.setAlphaComponent(phosphor, (flash * intensity * 0x38).toInt())
                canvas.drawRect(0f, 0f, width, height, flashPaint)
            }
        }

        canvas.drawRect(0f, 0f, width, height, vignettePaint)
    }

    private fun hash(seed: Int): Int {
        var x = seed * 0x45d9f3b
        x = (x xor (x ushr 16)) * 0x45d9f3b
        x = x xor (x ushr 16)
        return x and 0x7fffffff
    }

    private companion object {
        const val FLASH_MS = 280L
    }
}

/**
 * A field of particles that move on their own. Each particle has a place (x and y as a fraction of
 * the screen), a speed, a size and a phase that makes it sway or twinkle; the intensity sets how many
 * show. A subclass says how one particle moves and how it is drawn.
 */
private abstract class ParticleAnimation(context: Context, seed: Long, private val max: Int) : ScreenAnimation() {

    protected val density = context.resources.displayMetrics.density
    protected val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    protected val x = FloatArray(max)
    protected val y = FloatArray(max)
    protected val speed = FloatArray(max)
    protected val size = FloatArray(max)
    protected val phase = FloatArray(max)
    private var last = 0L

    init {
        val rnd = Random(seed)
        for (i in 0 until max) {
            x[i] = rnd.nextFloat()
            y[i] = rnd.nextFloat()
            speed[i] = 0.5f + rnd.nextFloat()
            size[i] = 0.4f + rnd.nextFloat() * 0.6f
            phase[i] = rnd.nextFloat() * (2 * PI).toFloat()
        }
    }

    /** Moves particle [i] by [dt] seconds; [seconds] is the clock for swaying. */
    protected abstract fun step(i: Int, dt: Float, seconds: Float)

    /** Draws particle [i]; [seconds] is the clock for swaying and twinkling. */
    protected abstract fun drawOne(canvas: Canvas, i: Int, w: Float, h: Float, seconds: Float, alpha: Float)

    override fun draw(canvas: Canvas, width: Float, height: Float, now: Long, intensity: Float, animated: Boolean) {
        val dt = if (animated && last != 0L) ((now - last) / 1000f).coerceAtMost(0.1f) else 0f
        last = now
        val seconds = if (animated) now / 1000f else 0f
        val count = (max * intensity).toInt()
        for (i in 0 until count) {
            step(i, dt, seconds)
            drawOne(canvas, i, width, height, seconds, intensity.coerceAtLeast(0.4f))
        }
    }

    /** Puts a particle that left the bottom of the screen back above its top. */
    protected fun wrap(i: Int) {
        if (y[i] > 1.05f) {
            y[i] -= 1.1f
            x[i] = (x[i] + 0.37f) % 1f
        }
    }
}

private class SnowAnimation(context: Context) : ParticleAnimation(context, 11, 140) {
    init {
        paint.color = Color.WHITE
    }

    override fun step(i: Int, dt: Float, seconds: Float) {
        y[i] += dt * 0.05f * speed[i]
        wrap(i)
    }

    override fun drawOne(canvas: Canvas, i: Int, w: Float, h: Float, seconds: Float, alpha: Float) {
        val sway = sin(seconds * 0.8f * speed[i] + phase[i]) * 0.012f
        paint.alpha = (alpha * (140 + 100 * size[i])).toInt().coerceAtMost(255)
        canvas.drawCircle((x[i] + sway) * w, y[i] * h, (1.2f + 2.6f * size[i]) * density, paint)
    }
}

private class PetalsAnimation(context: Context) : ParticleAnimation(context, 23, 36) {
    private val oval = RectF()

    init {
        paint.color = 0xFFF7B6C8.toInt()
    }

    override fun step(i: Int, dt: Float, seconds: Float) {
        y[i] += dt * 0.045f * speed[i]
        wrap(i)
    }

    override fun drawOne(canvas: Canvas, i: Int, w: Float, h: Float, seconds: Float, alpha: Float) {
        val sway = sin(seconds * 0.5f * speed[i] + phase[i]) * 0.05f
        val turn = seconds * 40f * speed[i] + phase[i] * 57f
        val len = (5f + 6f * size[i]) * density
        paint.alpha = (alpha * 210).toInt().coerceAtMost(255)
        canvas.save()
        canvas.translate((x[i] + sway) * w, y[i] * h)
        canvas.rotate(turn)
        // The petal turns over as it falls: its width follows the turning.
        val flat = 0.35f + 0.65f * kotlin.math.abs(sin(seconds * 1.4f * speed[i] + phase[i]))
        oval.set(-len, -len * 0.5f * flat, len, len * 0.5f * flat)
        canvas.drawOval(oval, paint)
        canvas.restore()
    }
}

private class StarsAnimation(context: Context) : ParticleAnimation(context, 37, 120) {
    init {
        paint.color = Color.WHITE
    }

    /** Stars stay where they are and only twinkle. */
    override fun step(i: Int, dt: Float, seconds: Float) = Unit

    override fun drawOne(canvas: Canvas, i: Int, w: Float, h: Float, seconds: Float, alpha: Float) {
        val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(seconds * 1.6f * speed[i] + phase[i]))
        paint.alpha = (alpha * twinkle * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(x[i] * w, y[i] * h, (0.6f + 1.6f * size[i]) * density, paint)
    }
}

private class RainAnimation(context: Context) : ParticleAnimation(context, 41, 130) {
    init {
        paint.color = 0xFFBFD8FF.toInt()
        paint.strokeWidth = 1.2f * density
        paint.strokeCap = Paint.Cap.ROUND
    }

    override fun step(i: Int, dt: Float, seconds: Float) {
        y[i] += dt * 0.9f * speed[i]
        wrap(i)
    }

    override fun drawOne(canvas: Canvas, i: Int, w: Float, h: Float, seconds: Float, alpha: Float) {
        val len = (10f + 14f * size[i]) * density
        paint.alpha = (alpha * 120).toInt().coerceAtMost(255)
        val px = x[i] * w
        val py = y[i] * h
        // Drops slant a little to the left as they fall.
        canvas.drawLine(px, py, px - len * 0.2f, py - len, paint)
    }
}

/**
 * The particles a theme describes: the shape, color, size, speed and direction it asks for, within
 * the limits [ThemeSpecs.customAnimation] already held its numbers to.
 */
private class CustomParticleAnimation(context: Context, private val spec: CustomAnimation.Particles) : ParticleAnimation(context, 53, spec.count) {

    private val color = (spec.color as? ThemeColor.Custom)?.argb ?: PhosphorColorToken(1f).resolveColor(context)
    private val radians = Math.toRadians(spec.angle.toDouble())

    // Where a particle goes: 0 degrees falls down, 90 goes left, 180 rises, 270 goes right.
    private val dx = (-sin(radians)).toFloat()
    private val dy = kotlin.math.cos(radians).toFloat()
    private val oval = RectF()
    private val star = Path().apply {
        // A four-point star in a unit square around the origin.
        moveTo(0f, -1f)
        quadTo(0.12f, -0.12f, 1f, 0f)
        quadTo(0.12f, 0.12f, 0f, 1f)
        quadTo(-0.12f, 0.12f, -1f, 0f)
        quadTo(-0.12f, -0.12f, 0f, -1f)
        close()
    }
    private val sprite: Bitmap? = if (spec.shape == "svg") sprite(spec.svg.orEmpty()) else null

    init {
        paint.color = color
        paint.strokeWidth = 1.2f * density
        paint.strokeCap = Paint.Cap.ROUND
    }

    override fun step(i: Int, dt: Float, seconds: Float) {
        val move = dt * spec.speed * speed[i]
        x[i] += move * dx
        y[i] += move * dy
        if (y[i] > 1.05f) {
            y[i] -= 1.1f
        } else if (y[i] < -0.05f) {
            y[i] += 1.1f
        }
        if (x[i] > 1.05f) {
            x[i] -= 1.1f
        } else if (x[i] < -0.05f) {
            x[i] += 1.1f
        }
    }

    override fun drawOne(canvas: Canvas, i: Int, w: Float, h: Float, seconds: Float, alpha: Float) {
        // A drift to the side of the way it goes.
        val drift = sin(seconds * 0.7f * speed[i] + phase[i]) * spec.sway * minOf(w, h)
        val px = x[i] * w + dy * drift
        val py = y[i] * h - dx * drift
        val side = spec.size * density * (0.6f + 0.8f * size[i])
        val twinkle = 1f - spec.twinkle * (0.5f - 0.5f * sin(seconds * 1.6f * speed[i] + phase[i]))
        paint.alpha = (spec.opacity * alpha.coerceAtMost(1f) * twinkle * 255).toInt().coerceIn(0, 255)
        val turn = spec.spin * seconds * speed[i] + Math.toDegrees(phase[i].toDouble()).toFloat()
        when (spec.shape) {
            "line" -> canvas.drawLine(px, py, px - dx * side * 2f, py - dy * side * 2f, paint)

            "petal" -> {
                canvas.save()
                canvas.translate(px, py)
                canvas.rotate(turn)
                val flat = 0.35f + 0.65f * kotlin.math.abs(sin(seconds * 1.4f * speed[i] + phase[i]))
                oval.set(-side, -side * 0.5f * flat, side, side * 0.5f * flat)
                canvas.drawOval(oval, paint)
                canvas.restore()
            }

            "star" -> {
                canvas.save()
                canvas.translate(px, py)
                canvas.rotate(turn)
                canvas.scale(side, side)
                canvas.drawPath(star, paint)
                canvas.restore()
            }

            "svg" -> sprite?.let {
                canvas.save()
                canvas.translate(px, py)
                canvas.rotate(turn)
                oval.set(-side, -side, side, side)
                canvas.drawBitmap(it, null, oval, paint)
                canvas.restore()
            }

            else -> canvas.drawCircle(px, py, side * 0.5f, paint)
        }
    }

    /** The drawing of an "svg" particle, rendered once; null when the markup cannot be drawn. */
    private fun sprite(markup: String): Bitmap? = runCatching {
        val svg = SVG.getFromString(markup)
        if (svg.documentViewBox == null && svg.documentWidth > 0 && svg.documentHeight > 0) {
            svg.setDocumentViewBox(0f, 0f, svg.documentWidth, svg.documentHeight)
        }
        svg.documentPreserveAspectRatio = PreserveAspectRatio.LETTERBOX
        svg.setDocumentWidth(SPRITE_SIDE.toFloat())
        svg.setDocumentHeight(SPRITE_SIDE.toFloat())
        val bitmap = Bitmap.createBitmap(SPRITE_SIDE, SPRITE_SIDE, Bitmap.Config.ARGB_8888)
        svg.renderToCanvas(Canvas(bitmap))
        bitmap
    }.getOrNull()

    private companion object {
        const val SPRITE_SIDE = 64
    }
}

/**
 * A Lottie file the theme brings, looped over the whole screen and scaled to cover it. Nothing is
 * fetched: a file that points at outside images draws without them.
 */
private class LottieOverlayAnimation private constructor(private val drawable: LottieDrawable, private val width: Float, private val height: Float) : ScreenAnimation() {

    override fun draw(canvas: Canvas, width: Float, height: Float, now: Long, intensity: Float, animated: Boolean) {
        val scale = maxOf(width / this.width, height / this.height)
        val w = this.width * scale
        val h = this.height * scale
        drawable.setBounds(((width - w) / 2f).toInt(), ((height - h) / 2f).toInt(), ((width + w) / 2f).toInt(), ((height + h) / 2f).toInt())
        drawable.alpha = (intensity * 255).toInt()
        if (!animated && drawable.isAnimating) drawable.pauseAnimation()
        drawable.draw(canvas)
    }

    companion object {
        /** The animation the file [json] holds, or null when it is not a Lottie composition that has something to play. */
        operator fun invoke(json: String): LottieOverlayAnimation? {
            val composition = runCatching { LottieCompositionFactory.fromJsonStringSync(json, null).value }.getOrNull() ?: return null
            val bounds = composition.bounds
            if (composition.duration <= 0f || bounds.width() <= 0 || bounds.height() <= 0) return null
            val drawable = LottieDrawable().apply {
                setComposition(composition)
                repeatCount = LottieDrawable.INFINITE
                setVisible(true, false)
                playAnimation()
            }
            return LottieOverlayAnimation(drawable, bounds.width().toFloat(), bounds.height().toFloat())
        }
    }
}
