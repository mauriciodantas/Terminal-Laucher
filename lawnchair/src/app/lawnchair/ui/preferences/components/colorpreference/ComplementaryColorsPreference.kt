package app.lawnchair.ui.preferences.components.colorpreference

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.ComplementaryColors
import app.lawnchair.theme.color.ColorOption
import com.android.launcher3.R

/**
 * The ready-made looks, the pastel ones, then the twelve hues of the color wheel, each shown as its accent over the
 * complement it brings along, in one row with no text around it. Tapping one sets the accent and the background together.
 */
@Composable
fun ComplementaryColorsPreference(modifier: Modifier = Modifier) {
    val looks = rememberLooks()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WheelChip(onClick = looks::openWheel)
            ComplementaryColors.all.forEach { pair -> LookChip(pair, looks.isSelected(pair), onClick = { looks.apply(pair) }) }
        }
    }
}

/**
 * The look on screen and how a look is applied, as Appearance does it: the background, then the
 * accent, then the secondary color (a pair goes back to the dimmed accent). While it is remembered,
 * [openWheel] opens the color wheel to compose any other look.
 */
@Stable
class Looks internal constructor(
    private val accent: Int?,
    private val background: Int?,
    private val secondary: Int?,
    val apply: (ComplementaryColors.Pair) -> Unit,
    private val onOpenWheel: () -> Unit,
) {
    fun isSelected(pair: ComplementaryColors.Pair) = accent == pair.accent && background == pair.background && secondary == pair.secondary

    fun openWheel() = onOpenWheel()
}

@Composable
fun rememberLooks(onApply: (ComplementaryColors.Pair) -> Unit = {}): Looks {
    val prefs2 = preferenceManager2()
    val accent = prefs2.accentColor.getAdapter()
    val background = prefs2.launcherBackgroundColor.getAdapter()
    val secondary = prefs2.secondaryColor.getAdapter()
    val currentSecondary = (secondary.state.value as? ColorOption.CustomColor)?.color
    val currentAccent = (accent.state.value as? ColorOption.CustomColor)?.color
    val currentBackground = (background.state.value as? ColorOption.CustomColor)?.color

    var wheelOpen by remember { mutableStateOf(false) }
    val applyPair = { pair: ComplementaryColors.Pair ->
        // The background goes first: the accent is checked against it as it lands.
        background.onChange(ColorOption.CustomColor(pair.background))
        accent.onChange(ColorOption.CustomColor(pair.accent))
        // A pair goes back to the dimmed accent; a triad brings its third color.
        secondary.onChange(pair.secondary?.let { ColorOption.CustomColor(it) } ?: ColorOption.Default)
        onApply(pair)
    }
    if (wheelOpen) {
        ComplementaryWheelDialog(
            accent = currentAccent,
            background = currentBackground,
            secondary = currentSecondary,
            onApply = {
                wheelOpen = false
                applyPair(it)
            },
            onDismiss = { wheelOpen = false },
        )
    }
    return Looks(currentAccent, currentBackground, currentSecondary, applyPair) { wheelOpen = true }
}

/** One look: its accent prompt over its background (and the secondary beside it), with its name under. */
@Composable
fun LookChip(pair: ComplementaryColors.Pair, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 40.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    border = BorderStroke(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    shape = RoundedCornerShape(10.dp),
                )
                .background(Color(pair.background)),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = ">_", color = Color(pair.accent), style = MaterialTheme.typography.titleMedium)
                // A three-color look shows its secondary color beside the prompt.
                pair.secondary?.let { Text(text = " ~", color = Color(it), style = MaterialTheme.typography.titleMedium) }
            }
        }
        Text(
            text = stringResource(id = pair.name),
            style = MaterialTheme.typography.labelSmall,
            // Set, not inherited: outside a surface (the first-run screen) the inherited color is black.
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** The first chip: a ring of every hue, opening the wheel to pick any other. */
@Composable
fun WheelChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ring = remember {
        Brush.sweepGradient(
            (0..360 step 30).map { Color(ColorUtils.HSLToColor(floatArrayOf(it.toFloat(), 0.85f, 0.55f))) },
        )
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 40.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(ring))
            Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface))
        }
        Text(
            text = stringResource(id = R.string.complementary_custom),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
