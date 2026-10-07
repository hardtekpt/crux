package com.hardtekpt.crux.ui.you

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hardtekpt.crux.data.model.MeasurementType
import com.hardtekpt.crux.ui.LocalUnits
import com.hardtekpt.crux.ui.Shown
import com.hardtekpt.crux.ui.measurement
import com.hardtekpt.crux.ui.theme.Archivo
import com.hardtekpt.crux.ui.theme.JetBrainsMono
import com.hardtekpt.crux.ui.weight

/*
 * Vector body outlines for the Measurements and Circumferences pages, drawn from SVG path
 * data in a fixed coordinate space (the "view box") scaled to the width available.
 */

/** Standing, arms down, seen from behind; 200 units wide, feet at y = 430, head centre (100, 34). */
private const val BODY_DOWN =
    "M107,50 C108,60 110,66 112,68 C122,72 136,72 144,78 C152,84 154,94 154,104 L157,150 C158,160 160,168 160,176 L164,222 C165,228 166,232 167,238 C170,248 170,258 165,264 C161,268 156,264 155,258 L153,240 C152,234 151,230 150,226 L144,182 C142,174 140,166 139,158 L134,118 C132,140 128,160 127,180 C127,196 134,206 136,222 C138,250 136,280 132,310 C130,322 129,330 130,342 C132,362 130,384 124,404 C124,412 130,420 128,426 C120,430 110,430 106,426 C104,418 108,410 108,402 C106,384 104,364 106,344 C107,334 107,326 106,316 C104,290 102,262 100,246 C98,262 96,290 94,316 C93,326 93,334 94,344 C96,364 94,384 92,402 C92,410 96,418 94,426 C90,430 80,430 72,426 C70,420 76,412 76,404 C70,384 68,362 70,342 C71,330 70,322 68,310 C64,280 62,250 64,222 C66,206 73,196 73,180 C72,160 68,140 66,118 L61,158 C60,166 58,174 56,182 L50,226 C49,230 48,234 47,240 L45,258 C44,264 39,268 35,264 C30,258 30,248 33,238 C34,232 35,228 36,222 L40,176 C40,168 42,160 43,150 L46,104 C46,94 48,84 56,78 C64,72 78,72 88,68 C90,66 92,60 93,50 Z"

/** Arms out (T pose); fingertips at x = -12.5 and 412.5, feet at y = 430, head centre (200, 34). */
private const val BODY_T =
    "M207,50 C208,60 210,66 212,68 C222,72 234,72 244,76 C262,78 288,79 312,80 L372,82 C378,82 382,81 386,80 C400,78 412,82 412.5,87 C412,92 402,94 388,93 C382,93 378,94 372,95 L312,97 C282,99 252,104 234,118 C232,140 228,160 227,180 C227,196 234,206 236,222 C238,250 236,280 232,310 C230,322 229,330 230,342 C232,362 230,384 224,404 C224,412 230,420 228,426 C220,430 210,430 206,426 C204,418 208,410 208,402 C206,384 204,364 206,344 C207,334 207,326 206,316 C204,290 202,262 200,246 C198,262 196,290 194,316 C193,326 193,334 194,344 C196,364 194,384 192,402 C192,410 196,418 194,426 C190,430 180,430 172,426 C170,420 176,412 176,404 C170,384 168,362 170,342 C171,330 170,322 168,310 C164,280 162,250 164,222 C166,206 173,196 173,180 C172,160 168,140 166,118 C148,104 118,99 88,97 L28,95 C22,94 18,93 12,93 C-2,94 -12,92 -12.5,87 C-12,82 0,78 14,80 C18,81 22,82 28,82 L88,80 C112,79 138,78 156,76 C166,72 178,72 188,68 C190,66 192,60 193,50 Z"

private val Eyebrow = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.2.sp)

/** Maps view-box units to pixels in a figure of a given width. */
private class ViewBox(val x: Float, val y: Float, val width: Float, val height: Float) {
    fun scale(widthPx: Float) = widthPx / width
}

/** Where a placed label sits relative to its point: its start, centre or end. */
private enum class Anchor { Start, Center, End }

private class FigureScope(val box: ViewBox, val scale: Float) {
    fun px(x: Float) = (x - box.x) * scale
    fun py(y: Float) = (y - box.y) * scale
}

/** A figure the width of its parent, its height from the view box, with labels laid over it. */
@Composable
private fun Figure(box: ViewBox, modifier: Modifier = Modifier, draw: DrawScope.(FigureScope) -> Unit, labels: @Composable BoxScope.(FigureScope) -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth.toFloat()
        val scope = remember(widthPx) { FigureScope(box, box.scale(widthPx)) }
        val heightDp = with(LocalDensity.current) { (box.height * scope.scale).toDp() }
        Box(Modifier.fillMaxWidth().height(heightDp)) {
            Canvas(Modifier.fillMaxSize()) { draw(scope) }
            labels(scope)
        }
    }
}

/** Places [content] so that its [anchor] edge sits at view-box x and its top at view-box y. */
@Composable
private fun Placed(scope: FigureScope, x: Float, top: Float, anchor: Anchor, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(Constraints())
            layout(constraints.maxWidth, constraints.maxHeight) {
                val left = scope.px(x) - when (anchor) {
                    Anchor.Start -> 0f
                    Anchor.Center -> placeable.width / 2f
                    Anchor.End -> placeable.width.toFloat()
                }
                placeable.place(left.toInt(), scope.py(top).toInt())
            }
        },
    ) { Box(modifier) { content() } }
}

/** Draws [pathData] scaled into the figure, filled and outlined like the mock. */
private fun DrawScope.body(scope: FigureScope, pathData: String, headX: Float, fill: Color, line: Color) {
    val path = PathParser().parsePathString(pathData).toPath()
    withTransform({
        translate(-scope.box.x * scope.scale, -scope.box.y * scope.scale)
        scale(scope.scale, scope.scale, Offset.Zero)
    }) {
        drawPath(path, fill)
        drawPath(path, line, style = Stroke(width = 1.6f, join = StrokeJoin.Round))
        drawOval(fill, topLeft = Offset(headX - 17f, 12f), size = Size(34f, 44f))
        drawOval(line, topLeft = Offset(headX - 17f, 12f), size = Size(34f, 44f), style = Stroke(width = 1.6f))
    }
}

private fun DrawScope.line(scope: FigureScope, x1: Float, y1: Float, x2: Float, y2: Float, color: Color, width: Float, dash: FloatArray? = null) {
    drawLine(
        color,
        Offset(scope.px(x1), scope.py(y1)),
        Offset(scope.px(x2), scope.py(y2)),
        strokeWidth = width * scope.scale,
        pathEffect = dash?.let { d -> PathEffect.dashPathEffect(d.map { it * scope.scale }.toFloatArray()) },
    )
}

/** A value and its unit, the unit smaller and muted. */
@Composable
private fun valueText(shown: Shown, size: Int): AnnotatedString {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return buildAnnotatedString {
        append(shown.value)
        shown.unit?.let {
            withStyle(SpanStyle(color = muted, fontSize = (size * 0.66f).sp, fontWeight = FontWeight.Normal)) {
                append(
                    if (it ==
                        "%"
                    ) {
                        it
                    } else {
                        " $it"
                    },
                )
            }
        }
    }
}

/**
 * Height and wingspan as dimension lines on an arms-out figure: height up the left side,
 * wingspan over the arms with dashed extension lines to the fingertips. Tap a value to set it.
 */
@Composable
fun ProportionsFigure(
    height: Double?,
    wingspan: Double?,
    reach: Double?,
    weightKg: Double?,
    bodyFat: Double?,
    onEdit: (MeasurementType) -> Unit,
    onLogWeight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val units = LocalUnits.current
    val ink = colors.onSurface
    // Reach drawn to scale against height (head top y = 12, floor y = 430), kept on the figure.
    val reachTop = if (reach != null && height != null && height > 0) {
        (430f - (reach / height).toFloat() * 418f).coerceIn(-130f, 0f)
    } else {
        -115f
    }
    Figure(
        box = ViewBox(-70f, -150f, 540f, 600f),
        modifier = modifier.testTag("proportions_figure"),
        draw = { s ->
            line(s, -70f, 431f, 450f, 431f, colors.outlineVariant, 2f)
            body(s, BODY_T, 200f, colors.surfaceContainer, colors.outline)
            // Height: up the left side, with an extension line to the top of the head.
            line(s, -34f, 12f, -34f, 430f, ink, 2f)
            line(s, -44f, 12f, -24f, 12f, ink, 2f)
            line(s, -44f, 430f, -24f, 430f, ink, 2f)
            line(s, -24f, 12f, 180f, 12f, colors.outline, 1.2f, floatArrayOf(3f, 4f))
            // Wingspan: over the arms, with extension lines down to the fingertips.
            line(s, -12.5f, -24f, 412.5f, -24f, ink, 2f)
            line(s, -12.5f, -34f, -12.5f, -14f, ink, 2f)
            line(s, 412.5f, -34f, 412.5f, -14f, ink, 2f)
            line(s, -12.5f, -14f, -12.5f, 80f, colors.outline, 1.2f, floatArrayOf(3f, 4f))
            line(s, 412.5f, -14f, 412.5f, 80f, colors.outline, 1.2f, floatArrayOf(3f, 4f))
            // Standing reach: a dashed line up the right side to where the hand reaches.
            line(s, 446f, 430f, 446f, reachTop, colors.primary, 2f, floatArrayOf(6f, 5f))
            line(s, 436f, reachTop, 456f, reachTop, colors.primary, 2f)
            // Weight and body fat as callouts pinned to the torso.
            line(s, 120f, 196f, 172f, 160f, colors.outline, 1.2f, floatArrayOf(2f, 3f))
            line(s, 282f, 226f, 228f, 196f, colors.outline, 1.2f, floatArrayOf(2f, 3f))
            listOf(Offset(174f, 158f), Offset(226f, 194f)).forEach { pin ->
                drawCircle(colors.surface, radius = 7f * s.scale, center = Offset(s.px(pin.x), s.py(pin.y)))
                drawCircle(colors.primary, radius = 5f * s.scale, center = Offset(s.px(pin.x), s.py(pin.y)))
            }
        },
        labels = { s ->
            Placed(s, x = -60f, top = 221f - 30f, anchor = Anchor.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .rotate(-90f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEdit(MeasurementType.HEIGHT) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("stat_HEIGHT"),
                ) {
                    Text("HEIGHT", style = Eyebrow, color = colors.onSurfaceVariant)
                    DimensionValue(height?.let { units.measurement(MeasurementType.HEIGHT, it) }, "you_height")
                }
            }
            Placed(s, x = 434f, top = reachTop + 4f, anchor = Anchor.End) {
                Callout(
                    "REACH",
                    reach?.let {
                        units.measurement(MeasurementType.STANDING_REACH, it)
                    },
                    "you_standing_reach",
                    Alignment.End,
                    "stat_STANDING_REACH",
                ) {
                    onEdit(MeasurementType.STANDING_REACH)
                }
            }
            Placed(s, x = 6f, top = 166f, anchor = Anchor.Start) {
                Callout("WEIGHT", weightKg?.let { units.weight(it) }, "figure_weight", Alignment.Start, "figure_log_weight", onLogWeight)
            }
            Placed(s, x = 290f, top = 196f, anchor = Anchor.Start) {
                Callout("BODY FAT", bodyFat?.let { units.measurement(MeasurementType.BODY_FAT, it) }, "you_body_fat", Alignment.Start, "stat_BODY_FAT") {
                    onEdit(MeasurementType.BODY_FAT)
                }
            }
            Placed(s, x = 200f, top = -80f, anchor = Anchor.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEdit(MeasurementType.WINGSPAN) }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .testTag("stat_WINGSPAN"),
                ) {
                    Text("WINGSPAN", style = Eyebrow, color = colors.onSurfaceVariant)
                    DimensionValue(wingspan?.let { units.measurement(MeasurementType.WINGSPAN, it) }, "you_wingspan")
                }
            }
        },
    )
}

/** A label and value laid on the figure, tappable to update it. */
@Composable
private fun Callout(label: String, shown: Shown?, valueTag: String, align: Alignment.Horizontal, tag: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = align,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag(tag),
    ) {
        Text(label, style = Eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DimensionValue(shown, valueTag)
    }
}

@Composable
private fun DimensionValue(shown: Shown?, tag: String) {
    if (shown == null) {
        Text("Add", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag(tag))
    } else {
        Text(
            valueText(shown, 18),
            style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(tag),
        )
    }
}

/** A measuring site on the back-view figure: its band(s) and where they sit. */
private data class Site(
    val name: String,
    val left: MeasurementType,
    val right: MeasurementType?,
    val y: Float,
    /** Band centre x and half-width, on the climber's left (the figure's left, seen from behind). */
    val cx: Float,
    val rx: Float,
)

// View-box coordinates (figure offset by 64 across and 6 down, as in the approved mock).
private val SITES = listOf(
    Site("Bicep", MeasurementType.BICEP, MeasurementType.BICEP_RIGHT, 138f, 118.2f, 11.6f),
    Site("Forearm", MeasurementType.FOREARM, MeasurementType.FOREARM_RIGHT, 206f, 109.7f, 9.3f),
    Site("Thigh", MeasurementType.THIGH, MeasurementType.THIGH_RIGHT, 286f, 145f, 18f),
    Site("Chest", MeasurementType.CHEST, null, 128f, 164f, 33.4f),
    Site("Waist", MeasurementType.WAIST, null, 186f, 164f, 27f),
)

/**
 * Circumferences on a back-view figure: a tape band at each site, the climber's left side's
 * values on the left and right side's on the right, with the difference under the right.
 * Chest and waist sit inside the torso. A site with no value yet has a dashed band and Add.
 */
@Composable
fun CircumferenceFigure(latest: Map<MeasurementType, Double>, onEdit: (MeasurementType) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val units = LocalUnits.current
    Figure(
        box = ViewBox(0f, 0f, 328f, 456f),
        modifier = modifier.testTag("circumference_figure"),
        draw = { s ->
            withTransform({ translate(64f * s.scale, 6f * s.scale) }) {
                body(FigureScope(ViewBox(0f, 0f, 328f, 456f), s.scale), BODY_DOWN, 100f, colors.surfaceContainer, colors.outline)
            }
            SITES.forEach { site ->
                val bands = if (site.right == null) listOf(site.cx to site.left) else listOf(site.cx to site.left, (328f - site.cx) to site.right)
                bands.forEach { (cx, type) ->
                    val set = type in latest
                    val style = if (set) {
                        Stroke(2f * s.scale)
                    } else {
                        Stroke(
                            1.5f * s.scale,
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(
                                    3f * s.scale,
                                    3f * s.scale,
                                ),
                            ),
                        )
                    }
                    drawOval(
                        if (set) colors.primary else colors.outline,
                        topLeft = Offset(s.px(cx - site.rx), s.py(site.y - 4.5f)),
                        size = Size(2 * site.rx * s.scale, 9f * s.scale),
                        style = style,
                    )
                }
                if (site.right != null) {
                    val dash = floatArrayOf(2f, 3f)
                    line(s, 62f, site.y, site.cx - site.rx - 2f, site.y, colors.outline, 1.2f, dash)
                    line(s, 328f - site.cx + site.rx + 2f, site.y, 266f, site.y, colors.outline, 1.2f, dash)
                }
            }
        },
        labels = { s ->
            SITES.forEach { site ->
                if (site.right != null) {
                    val left = latest[site.left]
                    val right = latest[site.right]
                    SideLabel(
                        s, x = 58f, top = site.y - 17f, Anchor.End, "L ${site.name.uppercase()}",
                        left?.let {
                            units.measurement(site.left, it)
                        },
                        site.left, null, onEdit,
                    )
                    val diff = if (left != null && right != null && left != right) {
                        "${if (right > left) "R" else "L"} +${units.measurement(site.left, kotlin.math.abs(right - left))}"
                    } else {
                        null
                    }
                    SideLabel(
                        s, x = 270f, top = site.y - 17f, Anchor.Start, "R ${site.name.uppercase()}",
                        right?.let {
                            units.measurement(site.right, it)
                        },
                        site.right,
                        diff?.let {
                            it to
                                "diff_${site.name}"
                        },
                        onEdit,
                    )
                } else {
                    val value = latest[site.left]
                    // Value above the band, name below it, inside the torso.
                    Placed(s, x = site.cx, top = site.y - 26f, anchor = Anchor.Center) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onEdit(site.left) }
                                .padding(horizontal = 4.dp)
                                .testTag("stat_${site.left.name}"),
                        ) {
                            SiteValue(value?.let { units.measurement(site.left, it) }, site.left, 14)
                        }
                    }
                    Placed(s, x = site.cx, top = site.y + 7f, anchor = Anchor.Center) {
                        Text(site.name.uppercase(), style = Eyebrow.copy(fontSize = 8.sp), color = colors.onSurfaceVariant)
                    }
                }
            }
        },
    )
}

@Composable
private fun BoxScope.SideLabel(
    s: FigureScope,
    x: Float,
    top: Float,
    anchor: Anchor,
    label: String,
    shown: Shown?,
    type: MeasurementType,
    diff: Pair<String, String>?,
    onEdit: (MeasurementType) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Placed(s, x = x, top = top, anchor = anchor) {
        Column(
            horizontalAlignment = if (anchor == Anchor.End) Alignment.End else Alignment.Start,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onEdit(type) }
                .padding(horizontal = 2.dp)
                .testTag("stat_${type.name}"),
        ) {
            Text(label, style = Eyebrow.copy(fontSize = 9.sp), color = colors.onSurfaceVariant)
            SiteValue(shown, type, 15)
            diff?.let { (text, tag) ->
                Text(text, style = Eyebrow.copy(letterSpacing = 0.sp), color = colors.secondary, modifier = Modifier.testTag(tag))
            }
        }
    }
}

@Composable
private fun SiteValue(shown: Shown?, type: MeasurementType, size: Int) {
    val tag = "you_${type.name.lowercase()}"
    if (shown == null) {
        Text(
            "Add",
            style = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.SemiBold, fontSize = size.sp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag(tag),
        )
    } else {
        Text(
            valueText(shown, size),
            style = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.SemiBold, fontSize = size.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(tag),
        )
    }
}
