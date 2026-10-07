package app.lawnchair.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import com.android.launcher3.icons.BaseIconFactory
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.IconThemeController
import com.android.launcher3.icons.SourceHint
import com.android.launcher3.icons.ThemedBitmap
import com.android.launcher3.icons.mono.MonoThemedBitmap
import com.android.launcher3.icons.mono.ThemedIconDrawable
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Terminal icon theme: every icon becomes a phosphor glyph on a dark panel, at one standard size.
 *
 * - Apps with a native monochrome layer use it as the glyph.
 * - Apps without one (full-color or legacy icons) are rendered as a phosphor image: the icon's
 *   luminance becomes the glyph's brightness, inverted when the icon is mostly light so the
 *   background stays dark. This keeps them recognizable instead of leaving a colored tile.
 * - Every glyph is rescaled by its visible bounds and ink area, so wide, tall, dense and thin
 *   icons all end up with the same optical size.
 */
class TerminalIconThemeController(
    private val colorProvider: (Context) -> IntArray = ThemedIconDrawable.Companion::getColors,
) : IconThemeController {

    override val themeID = "terminal-v1"

    override fun createThemedBitmap(
        icon: AdaptiveIconDrawable,
        info: BitmapInfo,
        factory: BaseIconFactory,
        sourceHint: SourceHint?,
    ): ThemedBitmap? {
        val size = info.icon.width
        if (size <= 0 || info.icon.height != size) return null

        val mono = icon.monochrome
        val mask = if (mono != null) nativeMonoMask(mono, size) else luminanceMask(icon, size)
        val normalized = normalize(mask, size)
        mask.recycle()
        normalized ?: return ThemedBitmap.NOT_SUPPORTED
        return MonoThemedBitmap(normalized, factory.whiteShadowLayer, colorProvider)
    }

    override fun decode(
        data: ByteArray,
        info: BitmapInfo,
        factory: BaseIconFactory,
        sourceHint: SourceHint,
    ): ThemedBitmap {
        val icon = info.icon
        if (data.size != icon.height * icon.width) return ThemedBitmap.NOT_SUPPORTED
        val monoBitmap = Bitmap.createBitmap(icon.width, icon.height, Bitmap.Config.ALPHA_8)
        monoBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(data))
        return MonoThemedBitmap(monoBitmap, factory.whiteShadowLayer, colorProvider)
    }

    override fun createThemedAdaptiveIcon(
        context: Context,
        originalIcon: AdaptiveIconDrawable,
        info: BitmapInfo?,
    ): AdaptiveIconDrawable {
        val colors = colorProvider(context)
        originalIcon.mutate()
        val themed = info?.themedBitmap as? MonoThemedBitmap ?: return originalIcon
        val glyph = InsetDrawable(
            BitmapDrawable(themed.mono).apply {
                colorFilter = BlendModeColorFilter(colors[1], BlendMode.SRC_IN)
            },
            AdaptiveIconDrawable.getExtraInsetFraction() / 2,
        )
        return AdaptiveIconDrawable(ColorDrawable(colors[0]), glyph)
    }

    /** Renders the native monochrome layer and keeps only its alpha. */
    private fun nativeMonoMask(mono: android.graphics.drawable.Drawable, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val layer = InsetDrawable(mono, -AdaptiveIconDrawable.getExtraInsetFraction())
        layer.setBounds(0, 0, size, size)
        layer.draw(Canvas(bitmap))
        return bitmap
    }

    /** Renders the full-color icon and turns its luminance into a phosphor brightness mask. */
    private fun luminanceMask(icon: AdaptiveIconDrawable, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, size, size)
        icon.draw(Canvas(bitmap))

        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)

        val lum = FloatArray(pixels.size)
        var weight = 0.0
        var weighted = 0.0
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = Color.alpha(p) / 255f
            if (a <= 0f) continue
            val l = (0.2126f * Color.red(p) + 0.7152f * Color.green(p) + 0.0722f * Color.blue(p)) / 255f
            lum[i] = l
            weight += a
            weighted += l * a
        }
        // Mostly light icons would glare as a phosphor panel, so draw their ink as the bright part.
        val invert = weight > 0 && weighted / weight > 0.55

        val value = FloatArray(pixels.size)
        val histogram = IntArray(256)
        var counted = 0
        for (i in pixels.indices) {
            val a = Color.alpha(pixels[i]) / 255f
            if (a <= 0f) continue
            val v = (if (invert) 1f - lum[i] else lum[i]) * a
            value[i] = v
            if (a > 0.5f) {
                histogram[(v * 255f).toInt().coerceIn(0, 255)]++
                counted++
            }
        }

        // Contrast stretch between the 5th and 95th percentiles.
        var lo = 0f
        var hi = 1f
        if (counted > 0) {
            var acc = 0
            for (b in 0..255) {
                acc += histogram[b]
                if (acc >= counted * 0.05f) {
                    lo = b / 255f
                    break
                }
            }
            acc = 0
            for (b in 255 downTo 0) {
                acc += histogram[b]
                if (acc >= counted * 0.05f) {
                    hi = b / 255f
                    break
                }
            }
        }
        val range = max(hi - lo, 0.2f)

        val out = IntArray(pixels.size)
        for (i in pixels.indices) {
            val a = Color.alpha(pixels[i]) / 255f
            if (a <= 0f) continue
            val stretched = ((value[i] - lo) / range).coerceIn(0f, 1f)
            out[i] = Color.argb((stretched * a * 255f).toInt().coerceIn(0, 255), 255, 255, 255)
        }
        bitmap.setPixels(out, 0, size, 0, 0, size, size)
        return bitmap
    }

    /**
     * Rescales [mask] so its visible bounds and ink area match the standard glyph size, centered
     * on the tile, and returns the result as an ALPHA_8 bitmap. Returns null when it is empty.
     */
    private fun normalize(mask: Bitmap, size: Int): Bitmap? {
        val pixels = IntArray(size * size)
        mask.getPixels(pixels, 0, size, 0, 0, size, size)

        var minX = size
        var minY = size
        var maxX = -1
        var maxY = -1
        var ink = 0.0
        for (y in 0 until size) {
            for (x in 0 until size) {
                val a = Color.alpha(pixels[y * size + x])
                if (a < INK_THRESHOLD) continue
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                ink += a / 255.0
            }
        }
        if (maxX < minX || ink <= 0.0) return null

        val bw = (maxX - minX + 1).toFloat()
        val bh = (maxY - minY + 1).toFloat()
        val boxScale = BOX_FRACTION * size / max(bw, bh)
        val areaScale = sqrt(AREA_FRACTION * size * size / ink.toFloat())
        val scale = min(boxScale, areaScale).coerceIn(MIN_SCALE, MAX_SCALE)

        val cx = (minX + maxX + 1) / 2f
        val cy = (minY + maxY + 1) / 2f
        val matrix = Matrix().apply {
            postTranslate(-cx, -cy)
            postScale(scale, scale)
            postTranslate(size / 2f, size / 2f)
        }

        // Draw straight into an ALPHA_8 bitmap so its buffer is exactly width * height, which is
        // what MonoThemedBitmap.serialize() expects.
        val alphaOnly = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
        Canvas(alphaOnly).drawBitmap(mask, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return alphaOnly
    }

    private companion object {
        /** Alpha (0-255) from which a pixel counts as part of the glyph. */
        const val INK_THRESHOLD = 40

        /** Longest side of the glyph, as a fraction of the tile. */
        const val BOX_FRACTION = 0.58f

        /** Target ink area, as a fraction of the tile area; limits dense, filled icons. */
        const val AREA_FRACTION = 0.19f

        const val MIN_SCALE = 0.35f
        const val MAX_SCALE = 6f
    }
}
