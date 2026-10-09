package app.lawnchair.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Paints the launcher background color as the system wallpaper and, when the user allowed it, on the
 * lock screen too. The last color written to each screen is remembered, so the wallpaper is only
 * rewritten when the color (or the choice of screens) really changes, not on every launcher start.
 */
object BackgroundColorWallpaper {

    private const val TAG = "BackgroundColorWallpaper"
    private const val PREFS = "bg_color_wallpaper"
    private const val KEY_SYSTEM = "system_color"
    private const val KEY_LOCK = "lock_color"

    /** Small on purpose: the system scales it to the screen and a solid color has nothing to lose. */
    private const val SIZE = 16

    suspend fun sync(context: Context, color: Int, toSystem: Boolean, toLock: Boolean) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Forget a screen the user switched off, so turning it back on writes the color again.
        prefs.edit {
            if (!toSystem) remove(KEY_SYSTEM)
            if (!toLock) remove(KEY_LOCK)
        }

        var which = 0
        if (toSystem && !prefs.isColor(KEY_SYSTEM, color)) which = which or WallpaperManager.FLAG_SYSTEM
        if (toLock && !prefs.isColor(KEY_LOCK, color)) which = which or WallpaperManager.FLAG_LOCK
        if (which == 0) return@withContext

        val bitmap = createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        try {
            WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, which)
            prefs.edit {
                if (which and WallpaperManager.FLAG_SYSTEM != 0) putInt(KEY_SYSTEM, color)
                if (which and WallpaperManager.FLAG_LOCK != 0) putInt(KEY_LOCK, color)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set the background color as wallpaper", e)
        } finally {
            bitmap.recycle()
        }
    }

    private fun android.content.SharedPreferences.isColor(key: String, color: Int) = contains(key) && getInt(key, 0) == color
}
