package app.lawnchair.ui.preferences.components.colorpreference

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.ComplementaryColors
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.theme.ColorPreview
import com.android.launcher3.R
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A color wheel: dragging picks the accent's hue and the opposite point of the ring, the
 * complement, becomes the background. The preview in the middle shows the pair as it will look.
 */
@Composable
fun ComplementaryWheelDialog(
    initialHue: Float,
    onApply: (ComplementaryColors.Pair) -> Unit,
    onDismiss: () -> Unit,
) {
    var hue by remember { mutableFloatStateOf(initialHue) }
    val pair = ComplementaryColors.pairForHue(hue)
    val prefs2 = preferenceManager2()

    // The settings behind the dialog take the pair's colors as the wheel turns, so the user sees the result.
    LaunchedEffect(pair) {
        ColorPreview.set(prefs2.accentColor.key.name, ColorOption.CustomColor(pair.accent))
        ColorPreview.set(prefs2.launcherBackgroundColor.key.name, ColorOption.CustomColor(pair.background))
    }
    DisposableEffect(Unit) { onDispose { ColorPreview.clear() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.complementary_wheel_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(id = R.string.complementary_wheel_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Box(contentAlignment = Alignment.Center) {
                    Wheel(hue = hue, pair = pair, onHue = { hue = it })
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(Color(pair.background)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = ">_", color = Color(pair.accent), style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(pair) }) { Text(text = stringResource(id = R.string.complementary_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(id = R.string.complementary_cancel)) }
        },
    )
}

@Composable
private fun Wheel(hue: Float, pair: ComplementaryColors.Pair, onHue: (Float) -> Unit) {
    val ring = remember {
        Brush.sweepGradient(
            (0..360 step 30).map { Color(ColorUtils.HSLToColor(floatArrayOf(it.toFloat(), 0.85f, 0.55f))) },
        )
    }
    val currentHue by rememberUpdatedState(hue)
    // Which marker the finger took hold of: the accent, or the background opposite it.
    var grabbedBackground by remember { mutableStateOf(false) }

    fun angleAt(offset: Offset, size: Float): Float {
        val c = size / 2f
        val degrees = Math.toDegrees(atan2(offset.y - c, offset.x - c).toDouble()).toFloat()
        return (degrees + 360f) % 360f
    }

    fun gap(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return min(d, 360f - d)
    }

    fun grab(offset: Offset, size: Float) {
        val angle = angleAt(offset, size)
        grabbedBackground = gap(angle, (currentHue + 180f) % 360f) < gap(angle, currentHue)
    }

    fun move(offset: Offset, size: Float) {
        val angle = angleAt(offset, size)
        onHue(if (grabbedBackground) (angle + 180f) % 360f else angle)
    }

    Canvas(
        modifier = Modifier
            .size(240.dp)
            .pointerInput(Unit) {
                detectTapGestures {
                    grab(it, size.width.toFloat())
                    move(it, size.width.toFloat())
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { grab(it, size.width.toFloat()) }) { change, _ ->
                    change.consume()
                    move(change.position, size.width.toFloat())
                }
            },
    ) {
        val stroke = 36.dp.toPx()
        val radius = min(size.width, size.height) / 2f - stroke / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawArc(
            brush = ring,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke),
        )
        // The accent's marker and, opposite it, the background's, each filled with the color it applies.
        listOf(
            Triple(hue, pair.accent, true),
            Triple((hue + 180f) % 360f, pair.background, false),
        ).forEach { (h, color, isAccent) ->
            val a = Math.toRadians(h.toDouble())
            val at = Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
            val size = if (isAccent) 15.dp.toPx() else 13.dp.toPx()
            drawCircle(Color(color), radius = size, center = at)
            drawCircle(Color.White, radius = size, center = at, style = Stroke(3.dp.toPx()))
        }
    }
}
