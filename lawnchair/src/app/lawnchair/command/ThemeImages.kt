package app.lawnchair.command

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log
import android.widget.Toast
import androidx.core.graphics.createBitmap
import app.lawnchair.wallpaper.BackgroundColorWallpaper
import com.android.launcher3.R
import com.caverock.androidsvg.PreserveAspectRatio
import com.caverock.androidsvg.SVG
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

    /** The longest side an SVG is drawn at; the system scales the wallpaper to the screen anyway. */
    private const val MAX_SVG_SIDE = 2400

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
            set(context, spec, home, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
        } else {
            listOfNotNull(home?.let { set(context, spec, it, WallpaperManager.FLAG_SYSTEM) }, lock?.let { set(context, spec, it, WallpaperManager.FLAG_LOCK) }).all { it }
        }
    }

    private fun set(context: Context, spec: ThemeSpec, image: ThemeImage, which: Int): Boolean {
        val bitmap = bitmap(context, spec, image) ?: return false
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

    /**
     * [image] as a bitmap no bigger than the screen needs ([maxSide] for a thumbnail): the art drawn
     * with [spec]'s colors, any other image read and decoded.
     */
    fun bitmap(context: Context, spec: ThemeSpec, image: ThemeImage, maxSide: Int? = null): Bitmap? = when (image) {
        is ThemeImage.Art -> ThemeArt.draw(context, image, spec, maxSide)
        is ThemeImage.Svg -> drawSvg(context, image, maxSide)
        else -> bytes(context, image)?.let { decode(context, it, maxSide) }
    }

    /**
     * The SVG drawn at the screen's size (a portrait phone), filling it as a wallpaper does: cropped,
     * not stretched. Only what is in the markup is drawn; AndroidSVG fetches nothing without a resolver.
     */
    private fun drawSvg(context: Context, image: ThemeImage.Svg, maxSide: Int?): Bitmap? = runCatching {
        val svg = SVG.getFromString(image.markup)
        val metrics = context.resources.displayMetrics
        val short = minOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
        val long = maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
        val scale = minOf(1f, (maxSide ?: MAX_SVG_SIDE).toFloat() / long)
        val width = (short * scale).toInt().coerceAtLeast(1)
        val height = (long * scale).toInt().coerceAtLeast(1)
        // A drawing without a viewBox still fills the screen, from its own width and height.
        if (svg.documentViewBox == null && svg.documentWidth > 0 && svg.documentHeight > 0) {
            svg.setDocumentViewBox(0f, 0f, svg.documentWidth, svg.documentHeight)
        }
        svg.documentPreserveAspectRatio = PreserveAspectRatio.FULLSCREEN
        svg.setDocumentWidth(width.toFloat())
        svg.setDocumentHeight(height.toFloat())
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        svg.renderToCanvas(Canvas(bitmap))
        bitmap
    }.onFailure { Log.w(TAG, "could not draw the SVG", it) }.getOrNull()

    /**
     * [spec] with each SVG drawn and embedded as a PNG in base64 (a JPEG when the PNG is too big),
     * so the theme file holds the picture itself. A drawing that cannot be rendered is left out.
     */
    suspend fun embed(context: Context, spec: ThemeSpec): ThemeSpec = withContext(Dispatchers.Default) {
        fun embedded(image: ThemeImage): ThemeImage? = if (image !is ThemeImage.Svg) {
            image
        } else {
            bitmap(context, spec, image)?.let { bitmap ->
                try {
                    encode(bitmap)?.let { ThemeImage.Data(java.util.Base64.getEncoder().encodeToString(it)) }
                } finally {
                    bitmap.recycle()
                }
            }
        }
        val home = spec.wallpaper?.let { w -> embedded(w.image)?.let { w.copy(image = it) } }
        // The same drawing for both screens is drawn once.
        val lock = spec.lockWallpaper?.let { if (it == spec.wallpaper?.image) home?.image else embedded(it) }
        spec.copy(wallpaper = home, lockWallpaper = lock)
    }

    /** The bitmap as a PNG within [ThemeSpecs.MAX_IMAGE_BYTES], or a JPEG of falling quality; null if none fits. */
    private fun encode(bitmap: Bitmap): ByteArray? {
        fun compress(format: Bitmap.CompressFormat, quality: Int) = java.io.ByteArrayOutputStream().also { bitmap.compress(format, quality, it) }.toByteArray()
        compress(Bitmap.CompressFormat.PNG, 100).takeIf { it.size <= ThemeSpecs.MAX_IMAGE_BYTES }?.let { return it }
        return listOf(92, 85, 75, 60).firstNotNullOfOrNull { q -> compress(Bitmap.CompressFormat.JPEG, q).takeIf { it.size <= ThemeSpecs.MAX_IMAGE_BYTES } }
    }

    /** The bytes of [image], or null when it cannot be read, is too big or is not an image (the art has none). */
    fun bytes(context: Context, image: ThemeImage): ByteArray? = runCatching {
        when (image) {
            is ThemeImage.Art, is ThemeImage.Svg -> null
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
