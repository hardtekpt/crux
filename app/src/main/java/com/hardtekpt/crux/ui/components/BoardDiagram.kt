package com.hardtekpt.crux.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hardtekpt.crux.data.BoardGeometry
import com.hardtekpt.crux.data.HoldRole
import com.hardtekpt.crux.ui.theme.CruxTheme

/** The colour each hold role is drawn in: Crux's own, readable on both themes. */
@Composable
fun holdColor(role: HoldRole): Color = when (role) {
    HoldRole.START -> CruxTheme.colors.success
    HoldRole.HAND -> MaterialTheme.colorScheme.primary
    HoldRole.FINISH -> Color(0xFFC77DFF)
    HoldRole.FOOT -> MaterialTheme.colorScheme.secondary
}

/**
 * A board climb's holds on its board, drawn from the board's hold positions: every hold the board
 * (and a Kilter's size) has as a faint dot, the climb's as rings in their role's colour; a
 * MoonBoard's grid with its letters and numbers. [small] fills the climb's holds and leaves the
 * rest out, for a thumbnail. Draws nothing for a board Crux doesn't know.
 */
@Composable
fun BoardDiagram(board: String, holds: String, modifier: Modifier = Modifier, small: Boolean = false) {
    val context = LocalContext.current
    val found = remember(board) { BoardGeometry.layout(context, board) } ?: return
    val (layout, sizeId) = found
    val climb = remember(holds) { BoardGeometry.holds(holds) }
    val placed = climb.mapNotNull { hold -> layout.points[hold.id]?.let { it to hold.role } }
    if (placed.isEmpty()) return
    val colors = HoldRole.entries.associateWith { holdColor(it) }
    val dot = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labels = layout.isGrid && !small

    // The area to draw, in the board's units: a Kilter's size (grown to fit a climb set beyond
    // it), or a MoonBoard's whole grid.
    val area = if (layout.isGrid) {
        listOf(0.5f, layout.columns!! + 0.5f, 0.5f, layout.rows!! + 0.5f)
    } else {
        val edges = sizeId?.let(layout.sizes::get) ?: listOf(
            layout.points.values.minOf { it.x },
            layout.points.values.maxOf { it.x },
            layout.points.values.minOf { it.y },
            layout.points.values.maxOf { it.y },
        )
        val pad = 4f
        listOf(
            minOf(edges[0], placed.minOf { it.first.x }) - pad,
            maxOf(edges[1], placed.maxOf { it.first.x }) + pad,
            minOf(edges[2], placed.minOf { it.first.y }) - pad,
            maxOf(edges[3], placed.maxOf { it.first.y }) + pad,
        )
    }
    val (left, right, bottom, top) = area
    val gutter = if (labels) 1.2f else 0f
    val width = right - left + gutter
    val height = top - bottom + gutter
    val shown = layout.points.values.filter { it.x in left..right && it.y in bottom..top }

    Canvas(
        modifier
            .aspectRatio(width / height)
            .semantics { contentDescription = BoardGeometry.describe(climb) },
    ) {
        val scale = size.width / width
        fun at(x: Float, y: Float) = Offset((x - left + gutter) * scale, (top - y) * scale)
        val unit = if (layout.isGrid) 1f else 8f
        if (!small) shown.forEach { drawCircle(dot, radius = unit * 0.13f * scale, center = at(it.x, it.y)) }
        placed.forEach { (point, role) ->
            val color = colors.getValue(role)
            if (small) {
                drawCircle(color, radius = unit * 0.75f * scale, center = at(point.x, point.y))
            } else {
                drawCircle(color, radius = unit * 0.38f * scale, center = at(point.x, point.y), style = Stroke(width = unit * 0.11f * scale))
            }
        }
        if (labels) {
            val style = TextStyle(color = ink, fontSize = (unit * 0.42f * scale).toSp())
            for (c in 1..layout.columns!!) {
                val text = measurer.measure("ABCDEFGHIJKLMNOP"[c - 1].toString(), style)
                drawText(
                    text,
                    topLeft = Offset(
                        at(c.toFloat(), bottom).x - text.size.width / 2f,
                        (top - bottom) * scale + (gutter * scale - text.size.height) / 2f,
                    ),
                )
            }
            for (r in 1..layout.rows!!) {
                val text = measurer.measure(r.toString(), style)
                drawText(text, topLeft = Offset((gutter * scale - text.size.width) / 2f, at(0f, r.toFloat()).y - text.size.height / 2f))
            }
        }
    }
}

/** The role colours as words beside dots, so the diagram doesn't rely on colour alone. */
@Composable
fun HoldLegend(holds: String, modifier: Modifier = Modifier) {
    val roles = remember(holds) { BoardGeometry.holds(holds).map { it.role }.toSet() }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3), verticalAlignment = Alignment.CenterVertically) {
        HoldRole.entries.filter { it in roles }.forEach { role ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(10.dp).border(2.dp, holdColor(role), CircleShape))
                Text(role.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A diagram in a framed box that opens the full board when tapped. */
@Composable
fun BoardDiagramCard(board: String, holds: String, title: String, modifier: Modifier = Modifier, maxHeight: Int = 320, small: Boolean = false) {
    var full by rememberSaveable { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(CruxTheme.size.borderHairline, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClickLabel = "Show the whole board") { full = true }
            .padding(if (small) 3.dp else 8.dp)
            .testTag("board_diagram"),
        contentAlignment = Alignment.Center,
    ) {
        BoardDiagram(board, holds, Modifier.heightIn(max = maxHeight.dp), small = small)
    }
    if (full) BoardDiagramDialog(board, holds, title) { full = false }
}

/** The whole board, as large as the screen allows, with the legend. */
@Composable
fun BoardDiagramDialog(board: String, holds: String, title: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(CruxTheme.space.s4)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(CruxTheme.space.s4)
                .testTag("board_diagram_full"),
            verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            BoardDiagram(board, holds, Modifier.fillMaxWidth().heightIn(max = 560.dp))
            HoldLegend(holds)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close", fontSize = 14.sp) }
        }
    }
}
