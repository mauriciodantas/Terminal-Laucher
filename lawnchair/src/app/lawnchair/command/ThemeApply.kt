package app.lawnchair.command

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import com.android.launcher3.R

/**
 * Applies a whole theme of the user's. What the theme says nothing about stays as an earlier theme left it, so
 * when some of it is not the default the user is asked: put those back to the defaults, or keep them as they are.
 * Where no screen can ask (a scheduled chain, the watch) the theme is applied as it always was, keeping them.
 */
object ThemeApply {

    enum class Choice { DEFAULTS, KEEP }

    /** Asks what to do with [left]; [onChoice] gets null when the user backs out. Needs an Activity [context]. */
    fun ask(context: Context, left: List<ThemeElement>, onChoice: (Choice?) -> Unit): Boolean {
        val activity = activityOf(context)?.takeUnless { it.isFinishing || it.isDestroyed } ?: return false
        var answered = false
        fun answer(choice: Choice?) {
            if (answered) return
            answered = true
            onChoice(choice)
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.theme_leftover_title)
            .setMessage(activity.getString(R.string.theme_leftover_message, left.joinToString(", ") { it.desc.lowercase() }))
            .setPositiveButton(R.string.theme_leftover_defaults) { _, _ -> answer(Choice.DEFAULTS) }
            .setNeutralButton(R.string.theme_leftover_keep) { _, _ -> answer(Choice.KEEP) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> answer(null) }
            .setOnCancelListener { answer(null) }
            .show()
        return true
    }

    /** Applies [spec] with [choice] for the elements in [left]. */
    fun apply(context: Context, spec: ThemeSpec, left: List<ThemeElement>, choice: Choice): Boolean =
        if (choice == Choice.DEFAULTS) ThemeStore.applyWithDefaults(context, spec, left) else ThemeStore.apply(context, spec)

    /** Applies [spec], asking first when it leaves out something that is not the default; [onDone] gets whether it was applied (null: the user backed out). */
    fun run(context: Context, spec: ThemeSpec, onDone: (Boolean?) -> Unit) {
        val left = ThemeStore.leftovers(context, spec)
        if (left.isEmpty() || !ask(context, left) { choice -> onDone(choice?.let { apply(context, spec, left, it) }) }) {
            onDone(ThemeStore.apply(context, spec))
        }
    }

    private fun activityOf(context: Context): Activity? {
        var c = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
