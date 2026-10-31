package app.lawnchair.ui.preferences.components.colorpreference

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.LauncherGround
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.theme.color.ColorOption
import com.android.launcher3.R

private enum class Mode { SYSTEM, LIGHT, DARK }

private const val OPAQUE = 0xFF000000.toInt()

/**
 * Three shortcuts for the commonest wish, "make it dark" or "make it light": follow the system mode, or
 * pin the pair of colors to a light ground with dark text or a dark ground with light text. Pinning
 * trades the accent and the background when the pair is the other way round (the secondary color goes
 * back to the dimmed accent, as when a pair is picked), and a pair already that way is left as it is.
 * [onBeforeChange] runs before anything is written, so the caller can offer to undo it.
 */
@Composable
fun ColorModeShortcuts(modifier: Modifier = Modifier, onBeforeChange: () -> Unit = {}) {
    val context = LocalContext.current
    val prefs2 = preferenceManager2()
    val follows = prefs2.followSystemColorMode.getAdapter()
    val accent = prefs2.accentColor.getAdapter()
    val background = prefs2.launcherBackgroundColor.getAdapter()
    val secondary = prefs2.secondaryColor.getAdapter()

    // The pair as picked, not as drawn: following the system swaps it on screen, never in storage.
    val (text, ground) = LauncherGround.drawn(ThemeProvider.INSTANCE.get(context).phosphorOf(accent.state.value), background.state.value, context, follows = false)
    val pairIsDark = ColorUtils.calculateLuminance(ground or OPAQUE) < ColorUtils.calculateLuminance(text or OPAQUE)
    val selected = when {
        follows.state.value -> Mode.SYSTEM
        pairIsDark -> Mode.DARK
        else -> Mode.LIGHT
    }

    fun choose(mode: Mode) {
        if (mode == selected) return
        onBeforeChange()
        if (mode == Mode.SYSTEM) {
            follows.onChange(true)
            return
        }
        follows.onChange(false)
        if ((mode == Mode.DARK) != pairIsDark) {
            val lighter = if (pairIsDark) text else ground
            val darker = if (pairIsDark) ground else text
            // The background goes first: the accent is checked against it as it lands.
            val (newText, newGround) = if (mode == Mode.DARK) lighter to darker else darker to lighter
            background.onChange(ColorOption.CustomColor(newGround or OPAQUE))
            accent.onChange(ColorOption.CustomColor(newText or OPAQUE))
            secondary.onChange(ColorOption.Default)
        }
    }

    val labels = listOf(
        Mode.SYSTEM to stringResource(id = R.string.appearance_mode_system),
        Mode.LIGHT to stringResource(id = R.string.appearance_mode_light),
        Mode.DARK to stringResource(id = R.string.appearance_mode_dark),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(id = R.string.appearance_mode_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { choose(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                ) {
                    Text(text = label, maxLines = 1)
                }
            }
        }
    }
}
