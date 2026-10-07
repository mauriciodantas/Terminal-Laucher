package app.lawnchair.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.lawnchair.theme.color.ColorOption

/**
 * A color the user is trying out in a picker and has not applied yet. The settings theme reads it,
 * so the screen the picker is on changes color while they drag the sliders; leaving the picker
 * without applying clears it and the saved color comes back.
 */
object ColorPreview {

    private var key by mutableStateOf<String?>(null)
    private var option by mutableStateOf<ColorOption?>(null)

    fun set(key: String, option: ColorOption) {
        this.key = key
        this.option = option
    }

    fun clear() {
        key = null
        option = null
    }

    /** The color being tried for the preference named [key], or null when there is none. */
    fun optionFor(key: String): ColorOption? = option.takeIf { this.key == key }
}
