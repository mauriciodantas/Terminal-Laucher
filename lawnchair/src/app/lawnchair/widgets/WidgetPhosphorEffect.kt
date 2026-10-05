package app.lawnchair.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import app.lawnchair.LawnchairAppWidgetHostView
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.theme.color.tokens.PhosphorColorToken
import app.lawnchair.util.repeatOnAttached
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn

/**
 * Phosphor look for the widgets the user adds: the widget is drawn in one tone, the phosphor color
 * the user picked, with scanlines on top. The terminal's own At a Glance panel is already styled,
 * so it is left alone. Turned on and off by the "widget effect" setting.
 *
 * The color comes from the theme when the effect is applied; changing the color recreates the
 * launcher, so widgets are built again with the new one.
 */
class WidgetPhosphorEffect(private val view: View) {

    private val density = view.resources.displayMetrics.density
    private val applies = view !is LawnchairAppWidgetHostView
    private var enabled = false

    private val scanPaint = Paint().apply {
        val period = (3 * density).toInt().coerceAtLeast(3)
        val tile = Bitmap.createBitmap(1, period, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            setPixel(0, 0, Color.argb(SCANLINE_ALPHA, 0, 0, 0))
        }
        shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    init {
        if (applies) {
            val prefs = PreferenceManager2.getInstance(view.context)
            view.repeatOnAttached {
                prefs.widgetEffect.get().distinctUntilChanged()
                    .onEach { apply(it) }
                    .launchIn(this)
            }
        }
    }

    private fun apply(on: Boolean) {
        enabled = on
        if (on) {
            val paint = Paint().apply { colorFilter = toneFilter(view.context) }
            view.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        } else {
            view.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        view.invalidate()
    }

    /** Scanlines over the widget. Called after the widget has drawn its own content. */
    fun drawOverlay(canvas: Canvas) {
        if (!enabled) return
        canvas.drawRect(0f, 0f, view.width.toFloat(), view.height.toFloat(), scanPaint)
    }

    companion object {
        private const val SCANLINE_ALPHA = 0x2E

        /** Keeps the widget's brightness but paints it in the phosphor color. */
        fun toneFilter(context: Context): ColorMatrixColorFilter {
            val tone = PhosphorColorToken(1f).resolveColor(context)
            val r = Color.red(tone) / 255f
            val g = Color.green(tone) / 255f
            val b = Color.blue(tone) / 255f
            val lr = 0.2126f
            val lg = 0.7152f
            val lb = 0.0722f
            return ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        r * lr, r * lg, r * lb, 0f, 0f,
                        g * lr, g * lg, g * lb, 0f, 0f,
                        b * lr, b * lg, b * lb, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
    }
}
