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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
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
import app.lawnchair.theme.ComplementaryColors
import com.android.launcher3.R
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
                    Wheel(hue = hue, onHue = { hue = it })
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
private fun Wheel(hue: Float, onHue: (Float) -> Unit) {
    val ring = remember {
        Brush.sweepGradient(
            (0..360 step 30).map { Color(ColorUtils.HSLToColor(floatArrayOf(it.toFloat(), 0.85f, 0.55f))) },
        )
    }
    fun hueAt(offset: Offset, size: Float): Float {
        val c = size / 2f
        val degrees = Math.toDegrees(atan2(offset.y - c, offset.x - c).toDouble()).toFloat()
        return (degrees + 360f) % 360f
    }
    Canvas(
        modifier = Modifier
            .size(240.dp)
            .pointerInput(Unit) { detectTapGestures { onHue(hueAt(it, size.width.toFloat())) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onHue(hueAt(change.position, size.width.toFloat()))
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
        // The accent's marker and, opposite it, the complement that becomes the background.
        listOf(hue to true, (hue + 180f) % 360f to false).forEach { (h, isAccent) ->
            val a = Math.toRadians(h.toDouble())
            val at = Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
            val fill = Color(ColorUtils.HSLToColor(floatArrayOf(h, 0.85f, 0.55f)))
            drawCircle(fill, radius = if (isAccent) 15.dp.toPx() else 11.dp.toPx(), center = at)
            drawCircle(Color.White, radius = if (isAccent) 15.dp.toPx() else 11.dp.toPx(), center = at, style = Stroke(3.dp.toPx()))
        }
    }
}
