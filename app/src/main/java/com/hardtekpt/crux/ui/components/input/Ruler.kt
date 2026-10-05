package com.hardtekpt.crux.ui.components.input

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.FloatExponentialDecaySpec
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.theme.Archivo
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.theme.JetBrainsMono
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The scale a [RulerInput] draws: values from [min] to [max] in [step]s, a mid tick every
 * [midEvery] steps, a long tick every [majorEvery], and a label every [labelEvery].
 */
data class RulerScale(
    val min: Double,
    val max: Double,
    val step: Double,
    val midEvery: Int,
    val majorEvery: Int,
    val labelEvery: Int,
    val spacing: Dp = 8.dp,
    val label: (Double) -> String = { it.roundToInt().toString() },
) {
    val steps: Int get() = ((max - min) / step).roundToInt()

    fun indexOf(value: Double): Int = ((value - min) / step).roundToInt().coerceIn(0, steps)

    /** The value at a tick, rounded so 0.1 steps don't drift into 72.39999. */
    fun valueAt(index: Int): Double = Math.round((min + index * step) * 1000) / 1000.0
}

/** How far a flick carries the scale before it settles. */
private val FlingDecay = FloatExponentialDecaySpec(frictionMultiplier = 3f)

/**
 * A horizontal scale slid under a fixed needle, for body measurements: drag or flick it and
 * it settles on the nearest step. The header shows the value (tap it to type one) and
 * [delta], usually the change since the last entry.
 */
@Composable
fun RulerInput(
    title: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    scale: RulerScale,
    display: (Double) -> String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    delta: String? = null,
    parseTyped: (String) -> Double? = { it.replace(',', '.').toDoubleOrNull() },
    typeUnit: String = unit.orEmpty(),
    testTag: String = "ruler",
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val stepPx = with(density) { scale.spacing.toPx() }
    val maxOffset = scale.steps * stepPx
    val tick = rememberTicker()
    val reduceMotion = rememberReduceMotion()
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentValue by rememberUpdatedState(value)

    var offset by remember(scale, stepPx) { mutableFloatStateOf(scale.indexOf(value) * stepPx) }
    // True from the first touch until the scale has settled on a tick. Only then does the
    // scale follow the value; while moving, the value follows the scale. One direction at a
    // time, so the two never chase each other.
    var moving by remember { mutableStateOf(false) }
    var settle by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    var typing by rememberSaveable { mutableStateOf(false) }

    // A value changed from outside (typing, a unit switch, TalkBack) moves the scale.
    LaunchedEffect(value, scale, stepPx) {
        if (!moving) offset = scale.indexOf(value) * stepPx
    }
    // The scale moving picks values, with a tick per step and a firmer one on long ticks.
    LaunchedEffect(scale, stepPx) {
        snapshotFlow { (offset / stepPx).roundToInt().coerceIn(0, scale.steps) }
            .distinctUntilChanged()
            .collect { index ->
                if (!moving || scale.indexOf(currentValue) == index) return@collect
                tick(index % scale.majorEvery == 0)
                currentOnChange(scale.valueAt(index))
            }
    }

    val dragState = rememberDraggableState { delta -> offset = (offset - delta).coerceIn(0f, maxOffset) }
    // Glides on after a flick and comes to rest exactly on the nearest tick. It runs in its
    // own scope, so recompositions while the value changes can't cut it short.
    fun settleFrom(velocity: Float) {
        settle?.cancel()
        settle = scope.launch {
            try {
                val glide = if (reduceMotion) 0f else FlingDecay.getTargetValue(offset, -velocity) - offset
                val target = ((offset + glide) / stepPx).roundToInt().coerceIn(0, scale.steps) * stepPx
                if (reduceMotion) {
                    offset = target
                } else {
                    animate(
                        initialValue = offset,
                        targetValue = target,
                        initialVelocity = -velocity,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                    ) { v, _ -> offset = v.coerceIn(0f, maxOffset) }
                    offset = target
                }
            } finally {
                moving = false
            }
        }
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.onSurfaceVariant)
    val nudge = { by: Int -> onValueChange(scale.valueAt((scale.indexOf(value) + by).coerceIn(0, scale.steps))) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Eyebrow(title)
                if (delta != null) {
                    Text(delta, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .clickable(onClickLabel = "Type a value") { typing = true }
                    .testTag("${testTag}_value"),
            ) {
                Text(
                    display(value),
                    style = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-1).sp, fontFeatureSettings = "tnum"),
                    color = colors.onSurface,
                )
                if (unit != null) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .testTag(testTag)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft, Key.DirectionDown, Key.VolumeDown -> { nudge(-1); true }
                        Key.DirectionRight, Key.DirectionUp, Key.VolumeUp -> { nudge(1); true }
                        else -> false
                    }
                }
                .focusable()
                .clearAndSetSemantics {
                    contentDescription = title
                    stateDescription = display(value) + (unit?.let { " $it" } ?: "")
                    progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), scale.min.toFloat()..scale.max.toFloat(), (scale.steps - 1).coerceAtLeast(0))
                    setProgress { target ->
                        onValueChange(scale.valueAt(scale.indexOf(target.toDouble())))
                        true
                    }
                }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        settle?.cancel()
                        moving = true
                    },
                    onDragStopped = { velocity -> settleFrom(velocity) },
                ),
        ) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .fadeEdges(vertical = false, edge = 0.22f),
            ) {
                val centre = size.width / 2
                val first = floor((offset - centre) / stepPx).toInt().coerceAtLeast(0)
                val last = ceil((offset + centre) / stepPx).toInt().coerceAtMost(scale.steps)
                val hairline = 1.dp.toPx()
                for (i in first..last) {
                    val x = centre + i * stepPx - offset
                    val (height, color) = when {
                        i % scale.majorEvery == 0 -> 26.dp.toPx() to colors.onSurfaceVariant
                        i % scale.midEvery == 0 -> 18.dp.toPx() to colors.outline
                        else -> 10.dp.toPx() to colors.outlineVariant
                    }
                    drawRect(color, topLeft = Offset(x - hairline / 2, size.height - height), size = Size(hairline, height))
                    if (i % scale.labelEvery == 0) {
                        val text = measurer.measure(scale.label(scale.valueAt(i)), labelStyle)
                        drawText(text, topLeft = Offset(x - text.size.width / 2f, 6.dp.toPx()))
                    }
                }
                val needle = 3.dp.toPx()
                drawRoundRect(
                    color = colors.primary,
                    topLeft = Offset(centre - needle / 2, size.height - 40.dp.toPx()),
                    size = Size(needle, 40.dp.toPx()),
                    cornerRadius = CornerRadius(needle / 2),
                )
            }
        }
    }
    if (typing) {
        TypeValueDialog(
            title = title,
            initial = display(value),
            unit = typeUnit,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
            onDismiss = { typing = false },
            parse = { text -> parseTyped(text)?.takeIf { it >= scale.min && it <= scale.max } },
            error = "Pick ${scale.label(scale.min)} to ${scale.label(scale.max)}",
            onConfirm = {
                onValueChange(scale.valueAt(scale.indexOf(it)))
                typing = false
            },
        )
    }
}
