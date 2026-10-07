package app.lawnchair.smartspace.glance

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.airbnb.lottie.LottieCompositionFactory
import java.io.ByteArrayOutputStream
import java.io.File

/** The animation shown beside the big value of the At a Glance panel. */
enum class GlanceAnimationStyle(val id: String) {
    OFF("off"),
    RADAR("radar"),
    SONAR("sonar"),
    WAVE("wave"),
    BARS("bars"),
    CUSTOM("custom"),
    ;

    companion object {
        /** The style saved under [id]; anything unknown (an older or newer version) is [OFF]. */
        fun fromId(id: String?): GlanceAnimationStyle = entries.firstOrNull { it.id == id } ?: OFF
    }
}

/**
 * The user's own Lottie animation, kept as a private copy so it keeps working when the original is
 * moved or deleted. The preference holds `"<revision>|<name>"`; the revision changes on every import
 * so the panel reloads even when the new file has the same name.
 */
object GlanceAnimationFile {

    private const val FILE_NAME = "glance_animation.json"

    /** A Lottie file is plain JSON; anything bigger than this is not a small icon-sized animation. */
    const val MAX_BYTES = 512 * 1024

    sealed interface Result {
        data class Imported(val stored: String) : Result
        data object TooBig : Result
        data object Invalid : Result
    }

    fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun exists(context: Context) = file(context).isFile

    fun encode(revision: Long, name: String) = "$revision|$name"

    fun displayName(stored: String): String = stored.substringAfter('|', "").ifBlank { FILE_NAME }

    /**
     * Reads [uri], checks that it is a Lottie animation and stores a copy. Blocking: run it off the
     * main thread. Nothing is replaced unless the file is valid.
     */
    fun import(context: Context, uri: Uri, now: Long = System.currentTimeMillis()): Result {
        val text = runCatching { readLimited(context, uri) }.getOrNull() ?: return Result.Invalid
        if (text.length > MAX_BYTES) return Result.TooBig
        if (!isLottie(text)) return Result.Invalid
        return runCatching {
            file(context).writeText(text)
            Result.Imported(encode(now, nameOf(context, uri)))
        }.getOrDefault(Result.Invalid)
    }

    fun remove(context: Context) {
        file(context).delete()
    }

    /** True when [json] parses as a Lottie composition that has something to play. */
    fun isLottie(json: String): Boolean {
        if (!json.trimStart().startsWith("{")) return false
        val result = LottieCompositionFactory.fromJsonStringSync(json, null)
        val composition = result.value ?: return false
        return composition.duration > 0f && composition.bounds.width() > 0 && composition.bounds.height() > 0
    }

    private fun readLimited(context: Context, uri: Uri): String? = context.contentResolver.openInputStream(uri)?.use { input ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        // Stop one byte past the limit: enough to know it is too big without reading all of it.
        while (out.size() <= MAX_BYTES) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        out.toByteArray().decodeToString()
    }

    private fun nameOf(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: FILE_NAME
}
