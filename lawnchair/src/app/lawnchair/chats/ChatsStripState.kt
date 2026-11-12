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

    /** Height of the strip with the default look; it is also what the workspace gives up. */
    const val HEIGHT_DP = 96

    private const val STORE = "nostromo"
    private const val KEY_SHOWN = "chats_strip_shown"
    private const val KEY_HEIGHT = "chats_strip_height_dp"
    private const val ICON_DEFAULT_DP = 48
    private const val HEADER_DP = 18
    private const val LABEL_DP = 18
    private const val GAPS_DP = 12

    /** Height of the strip for [iconDp]-wide icons, with or without the header line and the names. */
    fun heightDpFor(iconDp: Int, header: Boolean, labels: Boolean): Int = GAPS_DP + iconDp + (if (header) HEADER_DP else 0) + (if (labels) LABEL_DP else 0)

    @Volatile
    private var cached: Boolean? = null

    fun isShown(context: Context): Boolean = cached ?: context.applicationContext
        .getSharedPreferences(STORE, Context.MODE_PRIVATE)
        .getBoolean(KEY_SHOWN, false)
        .also { cached = it }

    @Volatile
    private var cachedHeightDp: Int? = null

    private fun heightDp(context: Context): Int = cachedHeightDp ?: context.applicationContext
        .getSharedPreferences(STORE, Context.MODE_PRIVATE)
        .getInt(KEY_HEIGHT, HEIGHT_DP)
        .also { cachedHeightDp = it }

    /** Records the new state and height; true when either changed, so the grid has to be laid out again. */
    fun setShown(context: Context, shown: Boolean, heightDp: Int = HEIGHT_DP): Boolean {
        if (isShown(context) == shown && heightDp(context) == heightDp) return false
        cached = shown
        cachedHeightDp = heightDp
        context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOWN, shown).putInt(KEY_HEIGHT, heightDp).apply()
        return true
    }

    fun heightPx(context: Context): Int = Math.round(heightDp(context) * context.resources.displayMetrics.density)

    /** Height the workspace gives up, or 0 when there is no strip. */
    @JvmStatic
    fun reservedPx(context: Context): Int = if (isShown(context)) heightPx(context) else 0
}
