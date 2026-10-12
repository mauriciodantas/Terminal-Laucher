package app.lawnchair.ui.preferences.components.colorpreference

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.ComplementaryColors
import app.lawnchair.theme.ComplementaryColors.Harmony
import app.lawnchair.theme.ComplementaryColors.Tint
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.ui.theme.ColorPreview
import com.android.launcher3.R
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A color wheel with the accent and, besides one-color looks, the secondary color. The [Harmony]
 * fixes the angle between the two, so dragging either turns both; in [Harmony.FREE] each moves on
 * its own. The background is not a point of the wheel: it is a dark tint (gray, the accent's or the
 * opposite hue) whose intensity the bar sets. The settings behind the dialog show the result live.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComplementaryWheelDialog(
    accent: Int?,
    background: Int?,
    secondary: Int?,
    onApply: (ComplementaryColors.Pair) -> Unit,
    onDismiss: () -> Unit,
) {
    val startHue = accent?.let(ComplementaryColors::hueOf) ?: 0f
    val startSecondary = secondary?.let(ComplementaryColors::hueOf)
    var accentHue by remember { mutableFloatStateOf(startHue) }
    var harmony by remember { mutableStateOf(ComplementaryColors.harmonyOf(startHue, startSecondary)) }
    // Kept for the free harmony; the fixed ones work it out from the accent.
    var freeSecondary by remember { mutableFloatStateOf(startSecondary ?: ((startHue + 120f) % 360f)) }
    var tint by remember {
        mutableStateOf(if (accent != null && background != null) ComplementaryColors.tintOf(accent, background) else Tint.OPPOSITE)
    }
    var intensity by remember { mutableFloatStateOf(background?.let(ComplementaryColors::intensityOf) ?: ComplementaryColors.DEFAULT_INTENSITY) }

    val secondaryHue = when (harmony) {
        Harmony.MONO -> null
        Harmony.FREE -> freeSecondary
        else -> (accentHue + harmony.angle!!) % 360f
    }
    val pair = ComplementaryColors.compose(accentHue, secondaryHue, tint, intensity)
    val prefs2 = preferenceManager2()

    LaunchedEffect(pair) {
        ColorPreview.set(prefs2.accentColor.key.name, ColorOption.CustomColor(pair.accent))
        ColorPreview.set(prefs2.launcherBackgroundColor.key.name, ColorOption.CustomColor(pair.background))
        ColorPreview.set(prefs2.secondaryColor.key.name, pair.secondary?.let { ColorOption.CustomColor(it) } ?: ColorOption.Default)
    }
    DisposableEffect(Unit) { onDispose { ColorPreview.clear() } }

    // The chosen chip filled with the accent; the plain dark container would hide which it is.
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primary,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.complementary_wheel_title)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Harmony.entries.forEach { h ->
                        Chip(stringResource(id = harmonyLabel(h)), harmony == h, chipColors) {
                            // Leaving a fixed harmony for the free one keeps the secondary color where it is.
                            if (h == Harmony.FREE && secondaryHue != null) freeSecondary = secondaryHue
                            harmony = h
                        }
                    }
                }
                Text(
                    text = stringResource(id = R.string.complementary_wheel_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Wheel(
                            accentHue = accentHue,
                            secondaryHue = secondaryHue,
                            pair = pair,
                            free = harmony == Harmony.FREE,
                            onAccent = { accentHue = it },
                            onSecondary = { freeSecondary = it },
                        )
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Color(pair.background)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = ">_", color = Color(pair.accent), style = MaterialTheme.typography.titleLarge)
                                pair.secondary?.let { Text(text = "~/", color = Color(it), style = MaterialTheme.typography.titleSmall) }
                            }
                        }
                    }
                    IntensityBar(
                        intensity = intensity,
                        backgroundAt = { ComplementaryColors.backgroundOf(accentHue, tint, it) },
                        onIntensity = { intensity = it },
                    )
                }
                Text(
                    text = stringResource(id = R.string.complementary_tint),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Tint.entries.forEach { t ->
                        Chip(stringResource(id = tintLabel(t)), tint == t, chipColors) { tint = t }
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
private fun Chip(label: String, selected: Boolean, colors: SelectableChipColors, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, colors = colors)
}

private fun harmonyLabel(harmony: Harmony): Int = when (harmony) {
    Harmony.MONO -> R.string.complementary_harmony_mono
    Harmony.ANALOGOUS -> R.string.complementary_harmony_analogous
    Harmony.TRIAD -> R.string.complementary_triad
    Harmony.SPLIT -> R.string.complementary_harmony_split
    Harmony.COMPLEMENTARY -> R.string.complementary_mode_pair
    Harmony.FREE -> R.string.complementary_harmony_free
}

private fun tintLabel(tint: Tint): Int = when (tint) {
    Tint.NEUTRAL -> R.string.complementary_tint_neutral
    Tint.ACCENT -> R.string.complementary_tint_accent
    Tint.OPPOSITE -> R.string.complementary_tint_opposite
}

@Composable
private fun Wheel(
    accentHue: Float,
    secondaryHue: Float?,
    pair: ComplementaryColors.Pair,
    free: Boolean,
    onAccent: (Float) -> Unit,
    onSecondary: (Float) -> Unit,
) {
    val ring = remember {
        Brush.sweepGradient(
            (0..360 step 30).map { Color(ColorUtils.HSLToColor(floatArrayOf(it.toFloat(), 0.85f, 0.55f))) },
        )
    }
    val currentAccent by rememberUpdatedState(accentHue)
    val currentSecondary by rememberUpdatedState(secondaryHue)
    val currentFree by rememberUpdatedState(free)
    // True while the finger holds the secondary marker rather than the accent's.
    var grabbedSecondary by remember { mutableStateOf(false) }

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
        grabbedSecondary = currentSecondary?.let { gap(angle, it) < gap(angle, currentAccent) } == true
    }

    fun move(offset: Offset, size: Float) {
        val angle = angleAt(offset, size)
        val secondary = currentSecondary
        when {
            !grabbedSecondary || secondary == null -> onAccent(angle)

            // Free: the secondary color moves alone.
            currentFree -> onSecondary(angle)

            // A fixed harmony: the accent follows, the same angle away.
            else -> onAccent((angle - (secondary - currentAccent) + 720f) % 360f)
        }
    }

    Canvas(
        modifier = Modifier
            .size(216.dp)
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
        fun pointAt(h: Float): Offset {
            val a = Math.toRadians(h.toDouble())
            return Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
        }
        val accentAt = pointAt(accentHue)
        // The two colors joined, so the angle between them reads at a glance.
        secondaryHue?.let { drawLine(Color.White, accentAt, pointAt(it), strokeWidth = 2.dp.toPx()) }
        listOfNotNull(
            Triple(accentAt, pair.accent, 15.dp.toPx()),
            secondaryHue?.let { Triple(pointAt(it), pair.secondary ?: pair.accent, 12.dp.toPx()) },
        ).forEach { (at, color, size) ->
            drawCircle(Color(color), radius = size, center = at)
            drawCircle(Color.White, radius = size, center = at, style = Stroke(3.dp.toPx()))
        }
    }
}

/**
 * A vertical bar for the intensity of the background: the track paints the background from its
 * darkest (bottom) to its brightest (top), and the thumb sits where it is now.
 */
@Composable
private fun IntensityBar(intensity: Float, backgroundAt: (Float) -> Int, onIntensity: (Float) -> Unit) {
    val darkest = Color(backgroundAt(0f))
    val brightest = Color(backgroundAt(1f))
    val outline = MaterialTheme.colorScheme.outlineVariant
    fun at(y: Float, height: Float) = (1f - y / height).coerceIn(0f, 1f)
    Canvas(
        modifier = Modifier
            .size(width = 32.dp, height = 216.dp)
            .pointerInput(Unit) { detectTapGestures { onIntensity(at(it.y, size.height.toFloat())) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onIntensity(at(change.position.y, size.height.toFloat()))
                }
            },
    ) {
        val radius = CornerRadius(16.dp.toPx())
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(brightest, darkest)),
            cornerRadius = radius,
        )
        drawRoundRect(color = outline, cornerRadius = radius, style = Stroke(1.dp.toPx()))
        val thumbY = (1f - intensity) * size.height
        val thumb = Offset(size.width / 2f, thumbY.coerceIn(14.dp.toPx(), size.height - 14.dp.toPx()))
        drawCircle(Color(backgroundAt(intensity)), radius = 13.dp.toPx(), center = thumb)
        drawCircle(Color.White, radius = 13.dp.toPx(), center = thumb, style = Stroke(3.dp.toPx()))
    }
}
