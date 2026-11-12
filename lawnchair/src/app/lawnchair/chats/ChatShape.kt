package app.lawnchair.chats

import androidx.annotation.StringRes
import com.android.launcher3.R

/** The outline of each icon in the "Conversas rápidas" strip. */
enum class ChatShape(val id: String, @StringRes val labelRes: Int) {
    SQUARE("square", R.string.glance_chat_shape_square),
    ROUNDED("rounded", R.string.glance_chat_shape_rounded),
    CIRCLE("circle", R.string.glance_chat_shape_circle),
    ;

    companion object {
        fun fromId(id: String?): ChatShape = entries.firstOrNull { it.id == id } ?: SQUARE
    }
}

/** A ready-made look for the "Conversas rápidas" strip; each setting can still be changed on top of it. */
enum class ChatPreset(
    val id: String,
    val shape: ChatShape,
    val border: Boolean,
    val borderWidth: Int,
    val fill: Boolean,
    val glow: Boolean,
    val header: Boolean,
    val labels: Boolean,
) {
    TERMINAL("terminal", ChatShape.SQUARE, true, 1, true, true, true, true),
    OUTLINE("outline", ChatShape.ROUNDED, true, 2, false, false, true, true),
    OPEN("open", ChatShape.CIRCLE, false, 1, true, true, false, true),
    MINIMAL("minimal", ChatShape.CIRCLE, false, 1, true, false, false, false),
    ;

    companion object {
        fun fromId(id: String?): ChatPreset? = entries.firstOrNull { it.id == id }
    }
}

/** The color of the unread counter on each chat icon; null in [argb] follows the theme. */
enum class ChatBadgeColor(val id: String, val argb: Int?, @StringRes val labelRes: Int) {
    ACCENT("accent", null, R.string.glance_chat_badge_accent),
    RED("red", 0xFFFF5A45.toInt(), R.string.glance_urgent_red),
    AMBER("amber", 0xFFFFB020.toInt(), R.string.glance_urgent_amber),
    MAGENTA("magenta", 0xFFFF4FA3.toInt(), R.string.glance_urgent_magenta),
    ;

    companion object {
        fun fromId(id: String?): ChatBadgeColor = entries.firstOrNull { it.id == id } ?: ACCENT
    }
}
