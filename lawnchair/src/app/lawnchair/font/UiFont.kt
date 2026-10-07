package app.lawnchair.font

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The font the user picked under Appearance. The layouts and the theme come with the terminal fonts
 * (IBM Plex Mono for text, VT323 for the clock); this applies the user's choice on top of them, and
 * does nothing while the choice is still the default.
 */
object UiFont {

    /** True while the interface font is the terminal default. */
    fun isDefault(font: FontCache.Font): Boolean = font.fullDisplayName.startsWith(DEFAULT_NAME)

    fun chosen(context: Context): FontCache.Font = PreferenceManager.getInstance(context).fontWorkspace.get()

    fun pixelClock(context: Context): Boolean = PreferenceManager2.getInstance(context).terminalPixelClock.firstCached()

    /** Puts the user's font on [view], keeping its [style] (bold, normal). Untouched while it is the default. */
    fun apply(view: TextView, style: Int = Typeface.NORMAL) {
        val font = chosen(view.context)
        if (isDefault(font)) return
        setTypeface(view, font, style)
    }

    /** The clock and the big values: the pixel font while the user keeps it, otherwise the interface font. */
    fun applyToClock(view: TextView) {
        if (pixelClock(view.context)) return
        val context = view.context
        val font = chosen(context)
        if (!isDefault(font)) {
            setTypeface(view, font, Typeface.NORMAL)
        } else {
            ResourcesCompat.getFont(context, R.font.ibm_plex_mono_regular)?.let { view.typeface = it }
        }
    }

    private fun setTypeface(view: TextView, font: FontCache.Font, style: Int) {
        val cache = FontCache.INSTANCE.get(view.context)
        CoroutineScope(Dispatchers.Main).launch {
            cache.getTypeface(font)?.let { view.typeface = Typeface.create(it, style) }
        }
    }

    private const val DEFAULT_NAME = "IBM Plex Mono"
}
