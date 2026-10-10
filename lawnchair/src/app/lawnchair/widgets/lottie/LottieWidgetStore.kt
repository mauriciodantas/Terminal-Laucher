package app.lawnchair.widgets.lottie

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.edit
import com.airbnb.lottie.LottieCompositionFactory
import com.android.launcher3.R
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What each Lottie widget shows and does: the imported animation (one file per widget id under the
 * app's files folder, as imported: .json, .lottie or gzip), its file name and the optional command
 * line run on a tap. The widget, the configuration screen and the provider all run in the launcher
 * process, so [changes] tells the widgets on screen to reload right away.
 */
object LottieWidgetStore {

    private const val DIR = "lottie_widgets"
    private const val PREFS = "lottie_widgets"
    private const val KEY_COMMAND = "command_"
    private const val KEY_NAME = "name_"
    private const val KEY_PLAYBACK = "playback_"

    private val _changes = MutableStateFlow(0)

    /** Bumped every time a widget's animation or command changes. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    fun file(context: Context, appWidgetId: Int): File = File(File(context.filesDir, DIR), appWidgetId.toString())

    private fun staged(context: Context, appWidgetId: Int): File = File(File(context.filesDir, DIR), "$appWidgetId.staged")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasAnimation(context: Context, appWidgetId: Int): Boolean = file(context, appWidgetId).exists()

    /** The name of the imported file, or null when the widget has no animation yet. */
    fun animationName(context: Context, appWidgetId: Int): String? = if (hasAnimation(context, appWidgetId)) {
        prefs(context).getString(KEY_NAME + appWidgetId, null) ?: context.getString(R.string.lottie_widget_current)
    } else {
        null
    }

    /** The command line run when the widget is tapped; empty when the tap only pauses the animation. */
    fun command(context: Context, appWidgetId: Int): String = prefs(context).getString(KEY_COMMAND + appWidgetId, null).orEmpty()

    /** How the animation plays; [Playback.LOOP] for widgets configured before the choice existed. */
    fun playback(context: Context, appWidgetId: Int): Playback = prefs(context).getString(KEY_PLAYBACK + appWidgetId, null)
        ?.let { name -> Playback.entries.firstOrNull { it.name == name } }
        ?: Playback.LOOP

    /**
     * Copies the animation at [uri] aside for [appWidgetId] without touching the current one, and
     * returns its file name, or null when it is not a valid Lottie animation. [save] puts it in place.
     * Blocking, call it off the main thread.
     */
    fun stage(context: Context, appWidgetId: Int, uri: Uri): String? {
        val target = staged(context, appWidgetId)
        target.parentFile?.mkdirs()
        val valid = try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { src -> target.outputStream().use { src.copyTo(it) } }
            // Parsed without a cache key so a rejected file leaves nothing behind in Lottie's cache.
            target.inputStream().use { LottieCompositionFactory.fromInputStreamSync(context, it, null) }.value != null
        } catch (_: Exception) {
            false
        }
        if (!valid) {
            target.delete()
            return null
        }
        return displayName(context, uri)
    }

    /**
     * Keeps the configuration: the staged animation, when one was picked, under [name], [command]
     * (blank clears it) and [playback]. Returns false when the staged file could not be put in place.
     */
    fun save(context: Context, appWidgetId: Int, name: String?, command: String, playback: Playback): Boolean {
        val pending = staged(context, appWidgetId)
        if (pending.exists() && !pending.renameTo(file(context, appWidgetId))) return false
        prefs(context).edit {
            if (name != null) putString(KEY_NAME + appWidgetId, name)
            val line = command.trim()
            if (line.isEmpty()) remove(KEY_COMMAND + appWidgetId) else putString(KEY_COMMAND + appWidgetId, line)
            putString(KEY_PLAYBACK + appWidgetId, playback.name)
        }
        _changes.update { it + 1 }
        return true
    }

    /** Drops an animation picked on a configuration screen that was then canceled. */
    fun discardStaged(context: Context, appWidgetId: Int) {
        staged(context, appWidgetId).delete()
    }

    fun delete(context: Context, appWidgetIds: IntArray) {
        prefs(context).edit {
            appWidgetIds.forEach {
                file(context, it).delete()
                staged(context, it).delete()
                remove(KEY_COMMAND + it)
                remove(KEY_NAME + it)
                remove(KEY_PLAYBACK + it)
            }
        }
        _changes.update { it + 1 }
    }

    /** Backup restore hands out new widget ids, so each animation and command follows its widget. */
    fun move(context: Context, oldIds: IntArray, newIds: IntArray) {
        val prefs = prefs(context)
        prefs.edit {
            oldIds.zip(newIds).forEach { (old, new) ->
                val from = file(context, old)
                if (from.exists()) from.renameTo(file(context, new))
                listOf(KEY_COMMAND, KEY_NAME, KEY_PLAYBACK).forEach { key ->
                    prefs.getString(key + old, null)?.let { putString(key + new, it) }
                    remove(key + old)
                }
            }
        }
        _changes.update { it + 1 }
    }

    private fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()

    enum class Playback {
        /** Plays over and over; a tap without a command pauses and resumes it. */
        LOOP,

        /** Rests on its first frame and plays once on each tap, besides running the command. */
        ONCE_ON_TAP,
    }
}
