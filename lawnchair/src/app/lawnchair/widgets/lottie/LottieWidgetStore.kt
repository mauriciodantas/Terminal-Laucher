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
 * app's files folder, as imported: .json, .lottie or gzip), its file name, the optional command
 * line run on a tap, how it plays and whether it takes the theme colors. The widget, the
 * configuration screen and the provider all run in the launcher process, so [changes] tells the
 * widgets on screen to reload right away.
 */
object LottieWidgetStore {

    private const val DIR = "lottie_widgets"
    private const val PREFS = "lottie_widgets"
    private const val KEY_COMMAND = "command_"
    private const val KEY_NAME = "name_"
    private const val KEY_PLAYBACK = "playback_"
    private const val KEY_THEME_COLORS = "theme_colors_"
    private const val KEY_SHOW_RESULT = "show_result_"
    private const val KEY_LAST_RESULT = "last_result_"
    private const val KEY_LAST_RESULT_AT = "last_result_at_"
    private const val KEY_RESULT_DURATION = "result_duration_"
    private const val PENDING = "pending"
    private const val KEY_PENDING_AT = "pending_at"

    const val DEFAULT_RESULT_DURATION_SECONDS = 60

    /** An imported widget waits this long for the home screen to add it; later it is left unused. */
    private const val PENDING_MAX_AGE = 30 * 60 * 1000L

    private val _changes = MutableStateFlow(0)

    /** Bumped every time a widget's animation or command changes. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    fun file(context: Context, appWidgetId: Int): File = File(File(context.filesDir, DIR), appWidgetId.toString())

    private fun staged(context: Context, appWidgetId: Int): File = File(File(context.filesDir, DIR), "$appWidgetId.staged")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasCustomAnimation(context: Context, appWidgetId: Int): Boolean = file(context, appWidgetId).exists()

    /** Always true, as widgets display either a custom imported animation or the built-in default animation. */
    fun hasAnimation(context: Context, appWidgetId: Int): Boolean = true

    /** The name of the imported file, or the default animation name when none was picked yet. */
    fun animationName(context: Context, appWidgetId: Int): String = if (hasCustomAnimation(context, appWidgetId)) {
        prefs(context).getString(KEY_NAME + appWidgetId, null) ?: context.getString(R.string.lottie_widget_current)
    } else {
        context.getString(R.string.lottie_widget_default_animation)
    }

    /** The command line run when the widget is tapped; empty when the tap only pauses the animation. */
    fun command(context: Context, appWidgetId: Int): String = prefs(context).getString(KEY_COMMAND + appWidgetId, null).orEmpty()

    /** How the animation plays; [Playback.LOOP] for widgets configured before the choice existed. */
    fun playback(context: Context, appWidgetId: Int): Playback = prefs(context).getString(KEY_PLAYBACK + appWidgetId, null)
        ?.let { name -> Playback.entries.firstOrNull { it.name == name } }
        ?: Playback.LOOP

    /** Whether the animation is painted in the theme colors instead of its own; off for older widgets. */
    fun themeColors(context: Context, appWidgetId: Int): Boolean = prefs(context).getBoolean(KEY_THEME_COLORS + appWidgetId, false)

    /** Whether to show a toast message with the result of the executed command on tap; off for older widgets. */
    fun showResult(context: Context, appWidgetId: Int): Boolean = prefs(context).getBoolean(KEY_SHOW_RESULT + appWidgetId, false)

    /** The last command output displayed on the widget, or null. */
    fun lastResult(context: Context, appWidgetId: Int): String? = prefs(context).getString(KEY_LAST_RESULT + appWidgetId, null)

    /** Timestamp in ms when the last command output was set, or 0. */
    fun lastResultAt(context: Context, appWidgetId: Int): Long = prefs(context).getLong(KEY_LAST_RESULT_AT + appWidgetId, 0L)

    /** Duration in seconds that the result stays visible before the animation returns. Default: 60s (1 min). */
    fun resultDurationSeconds(context: Context, appWidgetId: Int): Int = prefs(context).getInt(KEY_RESULT_DURATION + appWidgetId, DEFAULT_RESULT_DURATION_SECONDS)

    /** Saves or clears the last command output displayed on the widget, along with its timestamp. */
    fun saveLastResult(context: Context, appWidgetId: Int, result: String?) {
        prefs(context).edit {
            if (result.isNullOrBlank()) {
                remove(KEY_LAST_RESULT + appWidgetId)
                remove(KEY_LAST_RESULT_AT + appWidgetId)
            } else {
                putString(KEY_LAST_RESULT + appWidgetId, result)
                putLong(KEY_LAST_RESULT_AT + appWidgetId, System.currentTimeMillis())
            }
        }
    }

    /** The animation the configuration screen shows: the one just picked, or the saved one; null without either. */
    fun shownFile(context: Context, appWidgetId: Int): File? = staged(context, appWidgetId).takeIf { it.exists() } ?: file(context, appWidgetId).takeIf { it.exists() }

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
     * (blank clears it), [playback], [themeColors], [showResult] and [resultDurationSeconds].
     * Returns false when the staged file could not be put in place.
     */
    fun save(
        context: Context,
        appWidgetId: Int,
        name: String?,
        command: String,
        playback: Playback,
        themeColors: Boolean,
        showResult: Boolean = false,
        resultDurationSeconds: Int = DEFAULT_RESULT_DURATION_SECONDS,
    ): Boolean {
        val pending = staged(context, appWidgetId)
        if (pending.exists() && !pending.renameTo(file(context, appWidgetId))) return false
        prefs(context).edit {
            if (name != null) putString(KEY_NAME + appWidgetId, name)
            val line = command.trim()
            if (line.isEmpty()) remove(KEY_COMMAND + appWidgetId) else putString(KEY_COMMAND + appWidgetId, line)
            putString(KEY_PLAYBACK + appWidgetId, playback.name)
            putBoolean(KEY_THEME_COLORS + appWidgetId, themeColors)
            putBoolean(KEY_SHOW_RESULT + appWidgetId, showResult)
            putInt(KEY_RESULT_DURATION + appWidgetId, resultDurationSeconds)
        }
        _changes.update { it + 1 }
        return true
    }

    /**
     * Keeps an imported [pack] for the next Lottie widget added to the home screen, which takes it
     * in [applyPending] instead of asking for an animation. A later import replaces it.
     */
    fun setPending(context: Context, pack: LottieWidgetPack) {
        val target = File(File(context.filesDir, DIR), PENDING)
        target.parentFile?.mkdirs()
        target.writeBytes(pack.animation)
        prefs(context).edit {
            putString(KEY_NAME + PENDING, pack.name)
            putString(KEY_COMMAND + PENDING, pack.command)
            putString(KEY_PLAYBACK + PENDING, pack.playback.name)
            putBoolean(KEY_THEME_COLORS + PENDING, pack.themeColors)
            putBoolean(KEY_SHOW_RESULT + PENDING, pack.showResult)
            putInt(KEY_RESULT_DURATION + PENDING, pack.resultDurationSeconds)
            putLong(KEY_PENDING_AT, System.currentTimeMillis())
        }
    }

    /**
     * Gives [appWidgetId] the imported widget waiting in [setPending], when there is one and the
     * widget has no animation yet. Returns true when it did. The home screen may report a new
     * widget twice (its configuration and the pin callback), and only the first one takes it.
     */
    @Synchronized
    fun applyPending(context: Context, appWidgetId: Int): Boolean {
        if (hasAnimation(context, appWidgetId)) return false
        val pending = File(File(context.filesDir, DIR), PENDING)
        val prefs = prefs(context)
        val fresh = System.currentTimeMillis() - prefs.getLong(KEY_PENDING_AT, 0) <= PENDING_MAX_AGE
        if (!pending.exists() || !fresh || !pending.renameTo(file(context, appWidgetId))) return false
        prefs.edit {
            listOf(KEY_NAME, KEY_COMMAND, KEY_PLAYBACK).forEach { key ->
                prefs.getString(key + PENDING, null)?.let { putString(key + appWidgetId, it) }
                remove(key + PENDING)
            }
            putBoolean(KEY_THEME_COLORS + appWidgetId, prefs.getBoolean(KEY_THEME_COLORS + PENDING, false))
            remove(KEY_THEME_COLORS + PENDING)
            putBoolean(KEY_SHOW_RESULT + appWidgetId, prefs.getBoolean(KEY_SHOW_RESULT + PENDING, false))
            remove(KEY_SHOW_RESULT + PENDING)
            putInt(KEY_RESULT_DURATION + appWidgetId, prefs.getInt(KEY_RESULT_DURATION + PENDING, DEFAULT_RESULT_DURATION_SECONDS))
            remove(KEY_RESULT_DURATION + PENDING)
            remove(KEY_PENDING_AT)
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
                remove(KEY_THEME_COLORS + it)
                remove(KEY_SHOW_RESULT + it)
                remove(KEY_LAST_RESULT + it)
                remove(KEY_LAST_RESULT_AT + it)
                remove(KEY_RESULT_DURATION + it)
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
                if (prefs.contains(KEY_THEME_COLORS + old)) putBoolean(KEY_THEME_COLORS + new, prefs.getBoolean(KEY_THEME_COLORS + old, false))
                remove(KEY_THEME_COLORS + old)
                if (prefs.contains(KEY_SHOW_RESULT + old)) putBoolean(KEY_SHOW_RESULT + new, prefs.getBoolean(KEY_SHOW_RESULT + old, false))
                remove(KEY_SHOW_RESULT + old)
                if (prefs.contains(KEY_LAST_RESULT + old)) putString(KEY_LAST_RESULT + new, prefs.getString(KEY_LAST_RESULT + old, null))
                remove(KEY_LAST_RESULT + old)
                if (prefs.contains(KEY_LAST_RESULT_AT + old)) putLong(KEY_LAST_RESULT_AT + new, prefs.getLong(KEY_LAST_RESULT_AT + old, 0L))
                remove(KEY_LAST_RESULT_AT + old)
                if (prefs.contains(KEY_RESULT_DURATION + old)) putInt(KEY_RESULT_DURATION + new, prefs.getInt(KEY_RESULT_DURATION + old, DEFAULT_RESULT_DURATION_SECONDS))
                remove(KEY_RESULT_DURATION + old)
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
