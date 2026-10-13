package app.lawnchair.command

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.core.graphics.createBitmap
import kotlin.math.ceil
import kotlin.random.Random

/**
 * Draws a [ThemeImage.Art]: its symbols scattered over a solid ground, in a staggered grid with a
 * little jitter, turn and change of size, so it reads as a pattern and not as a table. The same art
 * always comes out the same (the randomness is seeded by the symbols), so the preview matches what
 * the wallpaper gets.
 */
object ThemeArt {

    private const val TAG = "ThemeArt"

    /** The largest side drawn; the system scales the wallpaper to the screen anyway. */
    private const val MAX_SIDE = 2400

    /** A symbol takes from this to this share of the short side, as [ThemeImage.Art.size] goes from 0 to 1. */
    private const val MIN_SYMBOL = 0.06f
    private const val MAX_SYMBOL = 0.16f

    /** Never more symbols than this, whatever the density, so drawing stays quick. */
    private const val MAX_SYMBOLS = 400

    fun draw(context: Context, art: ThemeImage.Art, spec: ThemeSpec, maxSide: Int? = null): Bitmap? = runCatching {
        val metrics = context.resources.displayMetrics
        val screenShort = minOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
        val screenLong = maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
        // Portrait, as the phone is held at home; a thumbnail keeps the proportion at a smaller size.
        val limit = minOf(maxSide ?: MAX_SIDE, MAX_SIDE)
        val scale = minOf(1f, limit.toFloat() / screenLong)
        val width = (screenShort * scale).toInt().coerceAtLeast(1)
        val height = (screenLong * scale).toInt().coerceAtLeast(1)

        val current by lazy { ThemeStore.current(context) }
        val ground = art.background ?: fixed(spec.background) ?: fixed(current.background) ?: ThemeSpecs.DEFAULT_BACKGROUND
        val tint = art.color ?: fixed(spec.accent) ?: fixed(current.accent) ?: android.graphics.Color.WHITE

        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(ground)

        val symbolSize = width * (MIN_SYMBOL + art.size.coerceIn(0f, 1f) * (MAX_SYMBOL - MIN_SYMBOL))
        // Dense art leaves about one symbol of air between symbols; sparse art about three.
        var cell = symbolSize * (4f - 2.6f * art.density.coerceIn(0f, 1f))
        while (ceil(width / cell) * ceil(height / cell) > MAX_SYMBOLS) cell *= 1.1f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }
        val random = Random(art.symbols.joinToString("\u0000").hashCode())
        val rows = ceil(height / cell).toInt() + 1
        val columns = ceil(width / cell).toInt() + 1
        for (row in 0 until rows) {
            // Every other row moves half a cell, so the symbols do not line up in columns.
            val shift = if (row % 2 == 1) cell / 2f else 0f
            for (column in 0 until columns) {
                val x = column * cell + shift + (random.nextFloat() - 0.5f) * cell * 0.5f
                val y = row * cell + (random.nextFloat() - 0.5f) * cell * 0.5f
                paint.textSize = symbolSize * (0.7f + random.nextFloat() * 0.6f)
                val symbol = art.symbols[random.nextInt(art.symbols.size)]
                canvas.save()
                canvas.rotate((random.nextFloat() - 0.5f) * 50f, x, y)
                // The text is centered on its middle, not on its baseline.
                canvas.drawText(symbol, x, y - (paint.descent() + paint.ascent()) / 2f, paint)
                canvas.restore()
            }
        }
        bitmap
    }.onFailure { Log.w(TAG, "could not draw the art", it) }.getOrNull()

    private fun fixed(color: ThemeColor?): Int? = (color as? ThemeColor.Custom)?.argb
}
