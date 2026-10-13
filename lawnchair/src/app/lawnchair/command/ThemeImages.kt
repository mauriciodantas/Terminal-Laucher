package app.lawnchair.command

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.widget.Toast
import app.lawnchair.wallpaper.BackgroundColorWallpaper
import com.android.launcher3.R
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

/**
 * The wallpapers a theme carries: downloading a link or decoding the base64 image, both capped at
 * [ThemeSpecs.MAX_IMAGE_BYTES], decoding no bigger than the screen needs, and setting them on the
 * home and lock screens. Saved themes keep their base64 images as files here, not in the preferences.
 */
object ThemeImages {

    private const val TAG = "ThemeImages"
    private const val DIR = "theme_images"
    private const val CONNECT_TIMEOUT = 10_000
    private const val READ_TIMEOUT = 20_000

    /** A bitmap past this many pixels is sampled down whatever the screen, so a huge image cannot fill the memory. */
    private const val MAX_PIXELS = 4096 * 4096

    private val scope = MainScope() + CoroutineName(TAG)

    /** Sets the images of [spec] in the background and says so if one of them fails. */
    fun applyLater(context: Context, spec: ThemeSpec) {
        if (spec.wallpaper == null && spec.lockWallpaper == null) return
        val app = context.applicationContext
        scope.launch {
            if (!apply(app, spec)) Toast.makeText(app, R.string.theme_wallpaper_failed, Toast.LENGTH_LONG).show()
        }
    }

    /** Sets the home screen and lock screen images of [spec]; false when one could not be read or set. */
    suspend fun apply(context: Context, spec: ThemeSpec): Boolean = withContext(Dispatchers.IO) {
        val home = spec.wallpaper?.image
        val lock = spec.lockImage
        // The color painted as wallpaper would cover the image the next time the launcher starts.
        BackgroundColorWallpaper.forget(context, system = home != null, lock = lock != null)
        if (home != null && home == lock) {
            set(context, home, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        } else {
            listOfNotNull(home?.let { set(context, it, WallpaperManager.FLAG_SYSTEM) }, lock?.let { set(context, it, WallpaperManager.FLAG_LOCK) }).all { it }
        }
    }

    private fun set(context: Context, image: ThemeImage, which: Int): Boolean {
        val bitmap = bytes(context, image)?.let { decode(context, it) } ?: return false
        return try {
            WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, which)
            true
        } catch (e: Exception) {
            Log.e(TAG, "could not set the wallpaper", e)
            false
        } finally {
            bitmap.recycle()
        }
    }

    /** The bytes of [image], or null when it cannot be read, is too big or is not an image. */
    fun bytes(context: Context, image: ThemeImage): ByteArray? = runCatching {
        when (image) {
            is ThemeImage.Data -> image.bytes()
            is ThemeImage.Stored -> File(dir(context), image.file).takeIf { it.isFile }?.inputStream()?.use { it.readCapped() }
            is ThemeImage.Url -> download(image.url)
        }
    }.onFailure { Log.w(TAG, "could not read the image", it) }.getOrNull()?.takeIf { ThemeSpecs.imageType(it) != null }

    private fun download(url: String): ByteArray? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.instanceFollowRedirects = true
            if (connection.responseCode !in 200..299) return null
            // A size the server announces over the limit is refused before a byte is read.
            if (connection.contentLengthLong > ThemeSpecs.MAX_IMAGE_BYTES) return null
            connection.inputStream.use { it.readCapped() }
        } finally {
            connection.disconnect()
        }
    }

    /** Up to [ThemeSpecs.MAX_IMAGE_BYTES]; null when there is more. */
    private fun InputStream.readCapped(): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > ThemeSpecs.MAX_IMAGE_BYTES) return null
        }
        return out.toByteArray()
    }

    /**
     * The image no bigger than the screen needs: it is sampled down by powers of two while it still
     * covers the screen, and always under [MAX_PIXELS]. The size is read first, without decoding.
     */
    fun decode(context: Context, bytes: ByteArray, maxSide: Int? = null): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        val metrics = context.resources.displayMetrics
        val screenShort = maxSide ?: minOf(metrics.widthPixels, metrics.heightPixels)
        val screenLong = maxSide ?: maxOf(metrics.widthPixels, metrics.heightPixels)
        val short = minOf(width, height)
        val long = maxOf(width, height)
        var sample = 1
        while (short / (sample * 2) >= screenShort && long / (sample * 2) >= screenLong) sample *= 2
        while ((width.toLong() / sample) * (height.toLong() / sample) > MAX_PIXELS) sample *= 2
        return runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }.onFailure { Log.w(TAG, "could not decode the image", it) }.getOrNull()
    }

    // ---- saved themes ----

    private fun dir(context: Context) = File(context.filesDir, DIR)

    /** [spec] with its base64 images moved to files of the app, as a saved theme keeps them. */
    fun store(context: Context, spec: ThemeSpec): ThemeSpec = spec.copy(
        wallpaper = spec.wallpaper?.let { w -> stored(context, w.image)?.let { w.copy(image = it) } },
        lockWallpaper = spec.lockWallpaper?.let { stored(context, it) },
    )

    private fun stored(context: Context, image: ThemeImage): ThemeImage? {
        if (image !is ThemeImage.Data) return image
        return runCatching {
            val bytes = image.bytes()
            val name = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(32)
            val file = File(dir(context).apply { mkdirs() }, name)
            if (!file.isFile) file.writeBytes(bytes)
            ThemeImage.Stored(name)
        }.onFailure { Log.w(TAG, "could not keep the image", it) }.getOrNull()
    }

    /** [spec] with its stored images back in base64, so the file sent carries them; an image gone is left out. */
    fun inline(context: Context, spec: ThemeSpec): ThemeSpec = spec.copy(
        wallpaper = spec.wallpaper?.let { w -> inlined(context, w.image)?.let { w.copy(image = it) } },
        lockWallpaper = spec.lockWallpaper?.let { inlined(context, it) },
    )

    private fun inlined(context: Context, image: ThemeImage): ThemeImage? {
        if (image !is ThemeImage.Stored) return image
        return bytes(context, image)?.let { ThemeImage.Data(java.util.Base64.getEncoder().encodeToString(it)) }
    }

    /** Deletes the stored images no saved theme points at any more. */
    fun prune(context: Context, themes: List<ThemeSpec>) {
        val used = themes.flatMap { listOfNotNull(it.wallpaper?.image, it.lockWallpaper) }.filterIsInstance<ThemeImage.Stored>().map { it.file }.toSet()
        dir(context).listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
    }
}
