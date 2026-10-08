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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.ComplementaryColors
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import com.android.launcher3.R

/**
 * The twelve hues of the color wheel, each shown as its accent over the complement it brings along.
 * Tapping one sets the accent and the background together.
 */
@Composable
fun ComplementaryColorsPreference(modifier: Modifier = Modifier) {
    val prefs2 = preferenceManager2()
    val accent = prefs2.accentColor.getAdapter()
    val background = prefs2.launcherBackgroundColor.getAdapter()
    val currentAccent = (accent.state.value as? ColorOption.CustomColor)?.color
    val currentBackground = (background.state.value as? ColorOption.CustomColor)?.color

    Column(modifier = modifier) {
        PreferenceTemplate(
            title = { Text(text = stringResource(id = R.string.appearance_complementary)) },
            description = { Text(text = stringResource(id = R.string.appearance_complementary_desc)) },
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ComplementaryColors.pairs.forEach { pair ->
                val selected = currentAccent == pair.accent && currentBackground == pair.background
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            // The background goes first: the accent is checked against it as it lands.
                            background.onChange(ColorOption.CustomColor(pair.background))
                            accent.onChange(ColorOption.CustomColor(pair.accent))
                        }
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
                        Text(text = ">_", color = Color(pair.accent), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = stringResource(id = pair.name),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
