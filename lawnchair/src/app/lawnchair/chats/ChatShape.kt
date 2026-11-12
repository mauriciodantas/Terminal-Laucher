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
