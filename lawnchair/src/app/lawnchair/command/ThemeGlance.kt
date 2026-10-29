package app.lawnchair.command

import android.content.Context
import android.util.Log
import android.widget.Toast
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.smartspace.glance.GlanceAnimationFile
import app.lawnchair.smartspace.glance.GlanceAnimationStyle
import com.android.launcher3.R
import com.patrykmichalik.opto.core.setBlocking
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

/** The Lottie animation a theme gives the At a Glance panel: the file it carries, or the one it links to. */
object ThemeGlance {

    private const val TAG = "ThemeGlance"
    private const val CONNECT_TIMEOUT = 10_000
    private const val READ_TIMEOUT = 20_000

    private val scope = MainScope() + CoroutineName(TAG)

    /** Sets the animation in the background (a link has to be downloaded) and says so if it fails. */
    fun applyLater(context: Context, lottie: GlanceLottie) {
        val app = context.applicationContext
        scope.launch {
            if (!apply(app, lottie)) Toast.makeText(app, R.string.theme_glance_lottie_failed, Toast.LENGTH_LONG).show()
        }
    }

    /** Stores the animation and picks it; false when it could not be read, is too big or is not a Lottie file. */
    suspend fun apply(context: Context, lottie: GlanceLottie): Boolean = withContext(Dispatchers.IO) {
        val (text, name) = when (lottie) {
            is GlanceLottie.Json -> lottie.json to "theme.json"
            is GlanceLottie.Url -> (download(lottie.url) ?: return@withContext false) to (lottie.url.substringBefore('?').substringAfterLast('/').ifBlank { "theme.json" })
        }
        val stored = (GlanceAnimationFile.store(context, text, name) as? GlanceAnimationFile.Result.Imported)?.stored ?: return@withContext false
        val p = PreferenceManager2.getInstance(context)
        p.glanceAnimationFile.setBlocking(stored)
        p.glanceAnimation.setBlocking(GlanceAnimationStyle.CUSTOM)
        true
    }

    /** The text at [url], or null when it cannot be read or is over what a panel animation may weigh. */
    private fun download(url: String): String? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.instanceFollowRedirects = true
            if (connection.responseCode !in 200..299) return@runCatching null
            if (connection.contentLengthLong > GlanceAnimationFile.MAX_BYTES) return@runCatching null
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            connection.inputStream.use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > GlanceAnimationFile.MAX_BYTES) return@runCatching null
                }
            }
            out.toByteArray().decodeToString()
        } finally {
            connection.disconnect()
        }
    }.onFailure { Log.w(TAG, "could not read the animation", it) }.getOrNull()
}
