package app.lawnchair.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The only place that writes the system wallpaper (home and lock screen). Everything else asks for a
 * change here: the launcher background color, the image of a theme, a picture picked in the carousel,
 * taking a theme's image off. Writes are serialized, so a theme being applied, an image being taken off
 * and the color sync at start-up never run over each other, and what each screen holds is remembered:
 * the last color written, and whether an image came from a theme (so a later theme can offer to take it
 * off) or from the user (which no theme touches without asking).
 */
object WallpaperController {

    private const val TAG = "WallpaperController"
    private const val PREFS = "bg_color_wallpaper"
    private const val KEY_SYSTEM = "system_color"
    private const val KEY_LOCK = "lock_color"
    private const val KEY_SYSTEM_ID = "system_id"
    private const val KEY_LOCK_ID = "lock_id"
    private const val KEY_IMAGE_SYSTEM = "system_image"
    private const val KEY_IMAGE_LOCK = "lock_image"

    /** Small on purpose: the system scales it to the screen and a solid color has nothing to lose. */
    private const val SIZE = 16

    private val writes = Mutex()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Paints [color] on the home screen ([toSystem]) and on the lock screen ([toLock]). Written only when the
     * color or the choice of screens really changes, not on every launcher start.
     */
    suspend fun paintColor(context: Context, color: Int, toSystem: Boolean, toLock: Boolean) = withContext(Dispatchers.IO) {
        writes.withLock {
            val prefs = prefs(context)
            val manager = WallpaperManager.getInstance(context)
            // Forget a screen the user switched off, so turning it back on writes the color again.
            prefs.edit {
                if (!toSystem) remove(KEY_SYSTEM).remove(KEY_SYSTEM_ID)
                if (!toLock) remove(KEY_LOCK).remove(KEY_LOCK_ID)
            }
            // The color remembered only counts while the wallpaper is still the one written: another app may have changed it since.
            var which = 0
            if (toSystem && !prefs.isStillColor(KEY_SYSTEM, KEY_SYSTEM_ID, color, manager.idOf(WallpaperManager.FLAG_SYSTEM))) which = which or WallpaperManager.FLAG_SYSTEM
            if (toLock && !prefs.isStillColor(KEY_LOCK, KEY_LOCK_ID, color, manager.idOf(WallpaperManager.FLAG_LOCK))) which = which or WallpaperManager.FLAG_LOCK
            if (which == 0) return@withLock

            val bitmap = createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            try {
                manager.setBitmap(bitmap, null, true, which)
                prefs.edit {
                    if (which and WallpaperManager.FLAG_SYSTEM != 0) putInt(KEY_SYSTEM, color).putLong(KEY_SYSTEM_ID, manager.idOf(WallpaperManager.FLAG_SYSTEM).toLong()).remove(KEY_IMAGE_SYSTEM)
                    if (which and WallpaperManager.FLAG_LOCK != 0) putInt(KEY_LOCK, color).putLong(KEY_LOCK_ID, manager.idOf(WallpaperManager.FLAG_LOCK).toLong()).remove(KEY_IMAGE_LOCK)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set the background color as wallpaper", e)
            } finally {
                bitmap.recycle()
            }
        }
    }

    /**
     * Sets [bitmap] on the chosen screens; false when the system refuses. [fromTheme] remembers that a theme
     * put it there, so a later theme that sets no wallpaper can ask whether to take it off.
     */
    suspend fun setImage(context: Context, bitmap: Bitmap, system: Boolean, lock: Boolean, fromTheme: Boolean): Boolean = withContext(Dispatchers.IO) {
        val which = flags(system, lock)
        if (which == 0) return@withContext false
        writes.withLock {
            try {
                WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, which)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set the wallpaper", e)
                return@withLock false
            }
            prefs(context).edit {
                if (system) remove(KEY_SYSTEM).remove(KEY_SYSTEM_ID)
                if (lock) remove(KEY_LOCK).remove(KEY_LOCK_ID)
                if (system) putBoolean(KEY_IMAGE_SYSTEM, fromTheme)
                if (lock) putBoolean(KEY_IMAGE_LOCK, fromTheme)
            }
            true
        }
    }

    /**
     * Takes what was written off the chosen screens: the system's own wallpaper shows again, and no color or
     * theme image is remembered for them any more.
     */
    suspend fun clear(context: Context, system: Boolean, lock: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!system && !lock) return@withContext true
        writes.withLock {
            try {
                val manager = WallpaperManager.getInstance(context)
                if (system) manager.clear(WallpaperManager.FLAG_SYSTEM)
                if (lock) manager.clear(WallpaperManager.FLAG_LOCK)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear the wallpaper", e)
                return@withLock false
            }
            prefs(context).edit {
                if (system) remove(KEY_SYSTEM).remove(KEY_SYSTEM_ID).remove(KEY_IMAGE_SYSTEM)
                if (lock) remove(KEY_LOCK).remove(KEY_LOCK_ID).remove(KEY_IMAGE_LOCK)
            }
            true
        }
    }

    /** Remembers that the user chose to keep what is on the chosen screens: from now on it is theirs, and no theme offers to remove it. */
    fun keep(context: Context, system: Boolean, lock: Boolean) {
        prefs(context).edit {
            if (system) remove(KEY_IMAGE_SYSTEM)
            if (lock) remove(KEY_IMAGE_LOCK)
        }
    }

    /** True when the image on the home screen ([lock] false) or the lock screen came from a theme and is still there. */
    fun hasThemeImage(context: Context, lock: Boolean): Boolean = prefs(context).getBoolean(if (lock) KEY_IMAGE_LOCK else KEY_IMAGE_SYSTEM, false)

    private fun flags(system: Boolean, lock: Boolean) = (if (system) WallpaperManager.FLAG_SYSTEM else 0) or (if (lock) WallpaperManager.FLAG_LOCK else 0)

    private fun WallpaperManager.idOf(which: Int): Int = try {
        getWallpaperId(which)
    } catch (e: Exception) {
        -1
    }

    private fun SharedPreferences.isStillColor(key: String, idKey: String, color: Int, id: Int) = contains(key) && getInt(key, 0) == color && getLong(idKey, -2L) == id.toLong()
}
