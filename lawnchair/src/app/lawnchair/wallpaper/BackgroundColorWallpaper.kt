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
    private const val KEY_IMAGE_SYSTEM = "system_image"
    private const val KEY_IMAGE_LOCK = "lock_image"

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
                if (which and WallpaperManager.FLAG_SYSTEM != 0) putInt(KEY_SYSTEM, color).remove(KEY_IMAGE_SYSTEM)
                if (which and WallpaperManager.FLAG_LOCK != 0) putInt(KEY_LOCK, color).remove(KEY_IMAGE_LOCK)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set the background color as wallpaper", e)
        } finally {
            bitmap.recycle()
        }
    }

    /** Forgets the color written to the screens that got another image, so the color is written again when asked. */
    fun forget(context: Context, system: Boolean, lock: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (system) remove(KEY_SYSTEM)
            if (lock) remove(KEY_LOCK)
        }
    }

    /** Remembers that a theme put an image on the screens, so a later theme can offer to take it off. */
    fun markImage(context: Context, system: Boolean, lock: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            if (system) putBoolean(KEY_IMAGE_SYSTEM, true)
            if (lock) putBoolean(KEY_IMAGE_LOCK, true)
        }
    }

    /** True when the image on the home screen ([lock] false) or the lock screen came from a theme and is still there. */
    fun hasThemeImage(context: Context, lock: Boolean): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(if (lock) KEY_IMAGE_LOCK else KEY_IMAGE_SYSTEM, false)

    /** Takes the images a theme placed off the chosen screens: the system's own wallpaper shows again. */
    suspend fun clearImages(context: Context, system: Boolean, lock: Boolean) = withContext(Dispatchers.IO) {
        val manager = WallpaperManager.getInstance(context)
        try {
            if (system) manager.clear(WallpaperManager.FLAG_SYSTEM)
            if (lock) manager.clear(WallpaperManager.FLAG_LOCK)
            forget(context, system, lock)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                if (system) remove(KEY_IMAGE_SYSTEM)
                if (lock) remove(KEY_IMAGE_LOCK)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear the wallpaper", e)
        }
    }

    private fun android.content.SharedPreferences.isColor(key: String, color: Int) = contains(key) && getInt(key, 0) == color
}
