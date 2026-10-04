package app.lawnchair.chats

import android.content.Context

/**
 * Whether the "Conversas rápidas" strip has chats to show, and the height it takes above the dock.
 *
 * The strip sits over the bottom of the workspace, so the workspace must leave that space free or
 * the strip would cover the last row of icons. The device profile reads [reservedPx] to shrink the
 * icon area; the flag is persisted so the very first layout of a run is already right.
 */
object ChatsStripState {

    /** Fixed height of the strip; it is also what the workspace gives up. */
    const val HEIGHT_DP = 96

    private const val STORE = "nostromo"
    private const val KEY_SHOWN = "chats_strip_shown"

    @Volatile
    private var cached: Boolean? = null

    fun isShown(context: Context): Boolean = cached ?: context.applicationContext
        .getSharedPreferences(STORE, Context.MODE_PRIVATE)
        .getBoolean(KEY_SHOWN, false)
        .also { cached = it }

    /** Records the new state; true when it changed, so the grid has to be laid out again. */
    fun setShown(context: Context, shown: Boolean): Boolean {
        if (isShown(context) == shown) return false
        cached = shown
        context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOWN, shown).apply()
        return true
    }

    fun heightPx(context: Context): Int = Math.round(HEIGHT_DP * context.resources.displayMetrics.density)

    /** Height the workspace gives up, or 0 when there is no strip. */
    @JvmStatic
    fun reservedPx(context: Context): Int = if (isShown(context)) heightPx(context) else 0
}
