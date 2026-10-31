package app.lawnchair.ui.preferences.components.controls

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate

/** A row that only tells something, laid out like [ClickablePreference] but with nothing to tap. */
@Composable
fun InfoPreference(
    label: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    PreferenceTemplate(
        title = { Text(text = label) },
        modifier = modifier,
        description = subtitle?.let { { Text(text = it) } },
    )
}
