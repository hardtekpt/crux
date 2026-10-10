package com.hardtekpt.crux.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hardtekpt.crux.ui.theme.CruxTheme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

// Shared chart components. One series per chart and no legend (the card header names
// it), except the donut, which always lists its slices. Every chart can be read without
// touch: values the climber needs are labelled, and a tap shows the exact date and value.

/** One point on a time series: x is any increasing number, usually LocalDate.toEpochDay(). */
data class SeriesPoint(val x: Double, val y: Double)

private val ChartGrid = 1.dp
private val LineWidth = 2.5.dp
private val DotRadius = 4.5.dp
private val DotRing = 2.dp

/**
 * Time series line with a soft area fill. The y-axis hugs the data; the newest point is
 * emphasised and labelled. Tap or drag to read any point.
 */
@Composable
fun TimeSeriesChart(
    points: List<SeriesPoint>,
    formatX: (Double) -> String,
    formatY: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    unit: String = "",
    description: String = "",
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurfaceVariant)
    val valueStyle = CruxTheme.type.code.copy(color = colors.onSurface)
    val tooltipStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurface)
    val sorted = remember(points) { points.sortedBy { it.x } }
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    // Plot left/right edges from the last draw, for mapping touches to points.
    val plotEdges = remember { FloatArray(2) }
    val reveal = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { reveal.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }

    if (sorted.isEmpty()) return
    val scale = remember(sorted) { niceScale(sorted.minOf { it.y }, sorted.maxOf { it.y }) }
    val xMin = sorted.first().x
    val xSpan = (sorted.last().x - xMin).takeIf { it > 0 } ?: 1.0

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .testTag("time_series_chart")
            .semantics { contentDescription = description }
            .pointerInput(sorted) {
                detectTapGestures { offset ->
                    val index = nearestIndex(sorted, offset.x, plotEdges[0], plotEdges[1], xMin, xSpan)
                    selected = if (selected == index) null else index
                }
            }
            .pointerInput(sorted) {
                detectHorizontalDragGestures(onDragEnd = {}) { change, _ ->
                    selected = nearestIndex(sorted, change.position.x, plotEdges[0], plotEdges[1], xMin, xSpan)
                }
            },
    ) {
        val yLabels = scale.ticks.map { measurer.measure(formatY(it), axisStyle) }
        val left = (yLabels.maxOf { it.size.width } + 8.dp.toPx())
        val xLabelHeight = measurer.measure("0", axisStyle).size.height + 6.dp.toPx()
        val top = 28.dp.toPx() // room for the end label and tooltip
        val right = size.width - 12.dp.toPx()
        val bottom = size.height - xLabelHeight
        plotEdges[0] = left
        plotEdges[1] = right
        fun px(x: Double) = left + ((x - xMin) / xSpan).toFloat() * (right - left)
        fun py(y: Double) = bottom - scale.fraction(y) * (bottom - top)

        // Gridlines and y labels at round values the axis reaches.
        scale.ticks.forEachIndexed { i, tick ->
            val y = py(tick)
            drawLine(colors.outlineVariant, Offset(left, y), Offset(right, y), ChartGrid.toPx())
            drawText(yLabels[i], topLeft = Offset(left - 8.dp.toPx() - yLabels[i].size.width, y - yLabels[i].size.height / 2f))
        }
        // First and last dates on the x-axis.
        val firstLabel = measurer.measure(formatX(sorted.first().x), axisStyle)
        drawText(firstLabel, topLeft = Offset(left, bottom + 6.dp.toPx()))
        if (sorted.size > 1) {
            val lastLabel = measurer.measure(formatX(sorted.last().x), axisStyle)
            drawText(lastLabel, topLeft = Offset(right - lastLabel.size.width, bottom + 6.dp.toPx()))
        }

        val positions = sorted.map { Offset(px(it.x), py(it.y)) }
        val line = Path().apply {
            positions.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(positions.last().x, bottom)
            lineTo(positions.first().x, bottom)
            close()
        }
        clipRect(right = left + (right - left) * reveal.value + DotRadius.toPx() * 2) {
            drawPath(area, colors.primary.copy(alpha = 0.14f))
            drawPath(
                line,
                colors.primary,
                style = Stroke(width = LineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        // The newest value, emphasised and labelled.
        val end = positions.last()
        drawDot(end, colors.primary, colors.surfaceContainer)
        if (selected == null) {
            val endText = measurer.measure("${formatY(sorted.last().y)}$unit", valueStyle)
            val x = (end.x - endText.size.width).coerceAtLeast(left)
            drawText(endText, topLeft = Offset(x, (end.y - endText.size.height - 8.dp.toPx()).coerceAtLeast(0f)))
        }

        // Crosshair and tooltip for the touched point.
        selected?.let { index ->
            val p = positions[index]
            drawLine(colors.outline, Offset(p.x, top), Offset(p.x, bottom), ChartGrid.toPx())
            drawDot(p, colors.primary, colors.surfaceContainer)
            drawTooltip(
                measurer,
                "${formatX(sorted[index].x)} · ${formatY(sorted[index].y)}$unit",
                tooltipStyle,
                anchorX = p.x,
                minX = 0f,
                maxX = size.width,
                background = colors.surfaceContainerHighest,
            )
        }
    }
}

private fun nearestIndex(points: List<SeriesPoint>, touchX: Float, left: Float, right: Float, xMin: Double, xSpan: Double): Int {
    val fraction = if (right > left) ((touchX - left) / (right - left)).coerceIn(0f, 1f) else 1f
    val x = xMin + fraction * xSpan
    return points.indices.minBy { abs(points[it].x - x) }
}

private fun DrawScope.drawDot(center: Offset, fill: Color, ring: Color) {
    drawCircle(ring, radius = DotRadius.toPx() + DotRing.toPx(), center = center)
    drawCircle(fill, radius = DotRadius.toPx(), center = center)
}

private fun DrawScope.drawTooltip(measurer: TextMeasurer, text: String, style: TextStyle, anchorX: Float, minX: Float, maxX: Float, background: Color) {
    val layout = measurer.measure(text, style)
    val padH = 8.dp.toPx()
    val padV = 4.dp.toPx()
    val w = layout.size.width + padH * 2
    val h = layout.size.height + padV * 2
    val x = (anchorX - w / 2).coerceIn(minX, maxX - w)
    drawRoundRect(background, Offset(x, 0f), Size(w, h), CornerRadius(8.dp.toPx()))
    drawText(layout, topLeft = Offset(x + padH, padV))
}

/** One bar: a label under it and its value. */
data class BarDatum(val label: String, val value: Double)

/**
 * Vertical bars for counts over time. Every bar carries its value; the newest bar is the
 * accent so now reads against before. Tap a bar to see its full label.
 */
@Composable
fun BarChart(
    bars: List<BarDatum>,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    highlightLast: Boolean = true,
    description: String = "",
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurfaceVariant)
    val valueStyle = CruxTheme.type.code.copy(color = colors.onSurface)
    val reveal = remember(bars) { Animatable(0f) }
    LaunchedEffect(bars) { reveal.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
    if (bars.isEmpty()) return
    val maxValue = bars.maxOf { it.value }.takeIf { it > 0 } ?: 1.0

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .testTag("bar_chart")
            .semantics { contentDescription = description },
    ) {
        val labelHeight = measurer.measure("0", axisStyle).size.height + 6.dp.toPx()
        val valueHeight = measurer.measure("0", valueStyle).size.height + 4.dp.toPx()
        val bottom = size.height - labelHeight
        val top = valueHeight + 2.dp.toPx()
        val slot = size.width / bars.size
        val barWidth = min(24.dp.toPx(), slot * 0.6f)
        val radius = CornerRadius(4.dp.toPx())

        drawLine(colors.outlineVariant, Offset(0f, bottom), Offset(size.width, bottom), ChartGrid.toPx())
        // Labels that would collide are thinned out, always keeping the newest bar's.
        val labels = bars.map { measurer.measure(it.label, axisStyle, maxLines = 1) }
        val widest = labels.maxOf { it.size.width } + 6.dp.toPx()
        val labelEvery = kotlin.math.ceil(widest / slot).toInt().coerceAtLeast(1)
        bars.forEachIndexed { i, bar ->
            val cx = slot * i + slot / 2
            val h = ((bar.value / maxValue).toFloat() * (bottom - top) * reveal.value)
            val isAccent = highlightLast && i == bars.lastIndex
            val color = if (isAccent) colors.primary else colors.primaryContainer
            if (h > 0f) {
                // Rounded data end, square at the baseline.
                val path = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = cx - barWidth / 2,
                            top = bottom - h,
                            right = cx + barWidth / 2,
                            bottom = bottom,
                            topLeftCornerRadius = radius,
                            topRightCornerRadius = radius,
                        ),
                    )
                }
                drawPath(path, color)
            }
            val value = measurer.measure(formatValue(bar.value), valueStyle)
            drawText(value, topLeft = Offset(cx - value.size.width / 2, bottom - h - value.size.height - 4.dp.toPx()))
            if ((bars.lastIndex - i) % labelEvery == 0) {
                val label = labels[i]
                val x = (cx - label.size.width / 2).coerceIn(0f, size.width - label.size.width)
                drawText(label, topLeft = Offset(x, bottom + 6.dp.toPx()))
            }
        }
    }
}

/** One row of a horizontal bar list, e.g. a grade and how many times it was sent. */
data class RankedDatum(val label: String, val value: Double)

/**
 * Horizontal bars on a track, label on the left in the grade style and value at the bar's
 * end. Used for the grade pyramid: hardest first.
 */
@Composable
fun HorizontalBarList(
    rows: List<RankedDatum>,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = CruxTheme.type.gradeSmall,
    accentFirst: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val max = rows.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 1.0
    val reveal = remember(rows) { Animatable(0f) }
    LaunchedEffect(rows) { reveal.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
    Column(modifier.testTag("horizontal_bars"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEachIndexed { index, row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.label,
                    style = labelStyle,
                    color = colors.onSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(end = CruxTheme.space.s2).size(width = 48.dp, height = 20.dp),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(14.dp)
                        .background(colors.surfaceContainerHighest, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((row.value / max).toFloat() * reveal.value)
                            .height(14.dp)
                            .background(
                                if (accentFirst && index == 0) colors.primary else colors.primaryContainer,
                                androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                            ),
                    )
                }
                Text(
                    formatValue(row.value),
                    style = CruxTheme.type.code,
                    color = colors.onSurface,
                    modifier = Modifier.padding(start = CruxTheme.space.s2).size(width = 28.dp, height = 18.dp),
                )
            }
        }
    }
}

/** One slice of a donut. */
data class SliceDatum(val label: String, val value: Double)

/**
 * Donut for parts of a whole with up to five parts (more fold into Other). The total sits
 * in the middle and the legend always lists every slice with its value, so colour is never
 * the only key. Tap a slice or a legend row to bring it forward.
 */
@Composable
fun DonutChart(
    slices: List<SliceDatum>,
    centerValue: String,
    centerLabel: String,
    formatValue: (Double) -> String,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
) {
    val colors = MaterialTheme.colorScheme
    val palette = CruxTheme.colors.chart
    val shown = remember(slices) { foldSlices(slices.filter { it.value > 0 }) }
    val total = shown.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
    var selected by remember(slices) { mutableStateOf<Int?>(null) }
    val reveal = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) { reveal.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
    fun colorAt(i: Int) = if (i < CATEGORICAL_SLOTS) palette[i] else colors.outline

    Row(
        modifier.testTag("donut_chart"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s4),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
            Canvas(
                Modifier
                    .size(size)
                    .pointerInput(shown) {
                        detectTapGestures { offset ->
                            val c = Offset(this.size.width / 2f, this.size.height / 2f)
                            if (hypot(offset.x - c.x, offset.y - c.y) > this.size.width / 2f) return@detectTapGestures
                            val angle = (Math.toDegrees(atan2((offset.y - c.y).toDouble(), (offset.x - c.x).toDouble())) + 90 + 360) % 360
                            var acc = 0.0
                            val hit = shown.indexOfFirst {
                                acc += it.value / total * 360
                                angle <= acc
                            }
                            selected = if (selected == hit) null else hit
                        }
                    },
            ) {
                val stroke = 18.dp.toPx()
                val gap = if (shown.size > 1) 2.dp.toPx() else 0f
                val diameter = this.size.minDimension - stroke - 6.dp.toPx()
                val topLeft = Offset((this.size.width - diameter) / 2, (this.size.height - diameter) / 2)
                val gapDegrees = Math.toDegrees((gap / (diameter / 2)).toDouble()).toFloat()
                var start = -90f
                shown.forEachIndexed { i, slice ->
                    val sweep = (slice.value / total * 360).toFloat() * reveal.value
                    val emphasised = selected == null || selected == i
                    drawArc(
                        color = colorAt(i).copy(alpha = if (emphasised) 1f else 0.35f),
                        startAngle = start + gapDegrees / 2,
                        sweepAngle = (sweep - gapDegrees).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(diameter, diameter),
                        style = Stroke(width = if (selected == i) stroke + 4.dp.toPx() else stroke),
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val sel = selected?.let { shown.getOrNull(it) }
                Text(
                    sel?.let { formatValue(it.value) } ?: centerValue,
                    style = CruxTheme.type.metricMedium,
                    color = colors.onSurface,
                )
                Text(
                    (sel?.label ?: centerLabel).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s1), modifier = Modifier.weight(1f)) {
            shown.forEachIndexed { i, slice ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = if (selected == i) null else i }
                        .padding(vertical = 4.dp),
                ) {
                    Box(Modifier.size(10.dp).background(colorAt(i), CircleShape))
                    Text(
                        slice.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected == null || selected == i) colors.onSurface else colors.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = CruxTheme.space.s2),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(formatValue(slice.value), style = CruxTheme.type.code, color = colors.onSurface)
                }
            }
        }
    }
}

/** Keeps the largest slices in palette order and folds the rest into Other. */
internal fun foldSlices(slices: List<SliceDatum>): List<SliceDatum> {
    if (slices.size <= CATEGORICAL_SLOTS) return slices
    val keep = slices.sortedByDescending { it.value }.take(CATEGORICAL_SLOTS - 1)
    val rest = slices.filterNot { it in keep }
    return keep + SliceDatum("Other", rest.sumOf { it.value })
}
