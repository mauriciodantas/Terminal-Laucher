package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.LauncherGround
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.theme.color.ColorOption
import com.android.launcher3.R

/**
 * A small home screen drawn with the colors as they are now: the clock and the apps in the accent over
 * the background, the secondary color on the second line, the dock and one folder. It follows each change at once,
 * so a color can be judged without leaving Settings. It only shows colors: shapes, fonts and the wallpaper are not drawn.
 */
@Composable
fun AppearancePreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs2 = preferenceManager2()
    val accentOption = prefs2.accentColor.getAdapter().state.value
    val backgroundOption = prefs2.launcherBackgroundColor.getAdapter().state.value
    val secondaryOption = prefs2.secondaryColor.getAdapter().state.value
    val dockOption = prefs2.hotseatBackgroundColor.getAdapter().state.value
    val folderOption = prefs2.folderColor.getAdapter().state.value
    // Read so the pair is drawn the way the home screen draws it when it follows the system mode.
    prefs2.followSystemColorMode.getAdapter().state.value

    val (accentInt, groundInt) = LauncherGround.drawn(ThemeProvider.INSTANCE.get(context).phosphorOf(accentOption), backgroundOption, context)
    val accent = Color(accentInt or OPAQUE)
    val ground = Color(groundInt or OPAQUE)
    val secondary = (secondaryOption as? ColorOption.CustomColor)?.let { Color(it.color or OPAQUE) } ?: accent.copy(alpha = 0.6f)
    // A panel left on its default is the ground lifted a little toward the text.
    val panel = accent.copy(alpha = 0.14f).compositeOver(ground)
    val dock = (dockOption as? ColorOption.CustomColor)?.let { Color(it.color) }?.compositeOver(ground) ?: panel
    val folder = (folderOption as? ColorOption.CustomColor)?.let { Color(it.color) }?.compositeOver(ground) ?: panel

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val description = stringResource(id = R.string.appearance_preview_desc)
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .clearAndSetSemantics { contentDescription = description }
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ground)
                .padding(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(text = "12:34", color = accent, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
                    Text(text = "~ terminal", color = secondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) { PreviewTile(accent = accent, fill = Color.Transparent) }
                    PreviewTile(accent = accent, fill = folder)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(dock),
            )
        }
        Text(
            text = stringResource(id = R.string.appearance_preview_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun PreviewTile(accent: Color, fill: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(fill)
            .border(BorderStroke(1.dp, accent), RoundedCornerShape(7.dp)),
    ) {
        Text(text = ">", color = accent, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

private const val OPAQUE = 0xFF000000.toInt()
