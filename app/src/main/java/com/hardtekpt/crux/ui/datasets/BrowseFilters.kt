package com.hardtekpt.crux.ui.datasets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.hardtekpt.crux.data.BrowseFilters
import com.hardtekpt.crux.data.BrowseQuery
import com.hardtekpt.crux.data.BrowseView
import com.hardtekpt.crux.data.GradePreset
import com.hardtekpt.crux.data.SortKey
import com.hardtekpt.crux.data.SortLine
import com.hardtekpt.crux.data.YouState
import com.hardtekpt.crux.data.label
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxSegmentedButtons
import com.hardtekpt.crux.ui.components.CruxSheet
import com.hardtekpt.crux.ui.components.CruxSwitchRow
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.SheetButton
import com.hardtekpt.crux.ui.theme.CruxTheme
import kotlin.math.roundToInt

/** One filter or the sort, as the bar has a chip for it and a small sheet behind it. */
enum class BrowsePart { VIEW, SORT, GRADE, ANGLE, YOU, RATING, ASCENTS, SETTER, BENCHMARKS, ALL }

/** What the filter bar and its sheets show: the set, what the board offers, and how many climbs it leaves. */
data class BrowseBarState(
    val query: BrowseQuery = BrowseQuery(),
    /** A catalogue has angles, ratings, ascents and benchmarks; a dataset doesn't. */
    val catalogue: Boolean = true,
    val angles: List<Int> = emptyList(),
    val scale: GradeScale? = null,
    /** Climbs per grade (as indices in [scale]) at this angle, for the grade grid. */
    val gradeCounts: Map<Int, Int> = emptyMap(),
    val setters: List<Pair<String, Int>> = emptyList(),
    /** Your hardest send here, for the grade presets. */
    val maxSent: Int? = null,
    val views: List<BrowseView> = emptyList(),
    val shown: Int = 0,
    val defaultSorts: List<SortLine> = BrowseQuery.DEFAULT_SORTS,
)

data class BrowseBarActions(val onQuery: (BrowseQuery) -> Unit = {}, val onSaveView: (String) -> Unit = {}, val onDeleteView: (String) -> Unit = {})

private val RATINGS = listOf(null, 2.0, 2.5, 3.0)
private val ASCENTS = listOf(null, 10, 50, 200)

private fun ratingLabel(r: Double?) = if (r == null) "Any" else "${if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()}+"
private fun ascentsLabel(a: Int?) = if (a == null) "Any" else "$a+"

/** "6C–7A", "from 6C", "up to 7A". */
internal fun gradeChip(f: BrowseFilters): String? {
    val scale = f.gradeScale ?: return null
    return when {
        f.gradeFrom != null && f.gradeTo != null -> if (f.gradeFrom ==
            f.gradeTo
        ) {
            scale.label(f.gradeFrom)
        } else {
            "${scale.label(f.gradeFrom)}–${scale.label(f.gradeTo)}"
        }

        f.gradeFrom != null -> "from ${scale.label(f.gradeFrom)}"

        f.gradeTo != null -> "up to ${scale.label(f.gradeTo)}"

        else -> null
    }
}

private fun settersChip(setters: Set<String>): String? = when {
    setters.isEmpty() -> null
    setters.size <= 2 -> setters.joinToString(", ")
    else -> setters.take(2).joinToString(", ") + " +${setters.size - 2}"
}

/**
 * The filter bar: a chip per filter and the sort, each reading its value and in the selected
 * style when set. A chip opens just its control in a small sheet; Benchmarks toggles. Filters
 * add up and the list follows at once. [allOpen] shows every filter at once (the Tune button).
 */
@Composable
fun BrowseFilterBar(state: BrowseBarState, actions: BrowseBarActions, allOpen: Boolean, onAllOpen: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf<BrowsePart?>(null) }
    val q = state.query
    val f = q.filters
    val chips = buildList {
        if (state.views.isNotEmpty() || q.view != null) add(Triple(BrowsePart.VIEW, q.view ?: "Views", q.view != null))
        add(Triple(BrowsePart.SORT, q.sorts.label().ifBlank { "Sort" }, q.sorts != state.defaultSorts))
        add(Triple(BrowsePart.GRADE, gradeChip(f) ?: "Grade", gradeChip(f) != null))
        if (state.catalogue && state.angles.size > 1) add(Triple(BrowsePart.ANGLE, f.angle?.let { "$it°" } ?: "Angle", true))
        add(Triple(BrowsePart.YOU, f.you.label() ?: "You", f.you.label() != null))
        if (state.catalogue) {
            add(Triple(BrowsePart.RATING, "★ " + ratingLabel(f.minRating), f.minRating != null))
            add(Triple(BrowsePart.ASCENTS, f.minAscents?.let { "$it+ ascents" } ?: "Ascents", f.minAscents != null))
        }
        add(Triple(BrowsePart.SETTER, settersChip(f.setters) ?: "Setter", f.setters.isNotEmpty()))
        if (state.catalogue) add(Triple(BrowsePart.BENCHMARKS, "Benchmarks", f.benchmarks))
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), modifier = modifier.fillMaxWidth().testTag("browse_bar")) {
        items(chips, key = { it.first }) { (part, label, on) ->
            CruxFilterChip(
                label,
                on,
                {
                    if (part == BrowsePart.BENCHMARKS) {
                        actions.onQuery(q.copy(filters = f.copy(benchmarks = !f.benchmarks), view = null))
                    } else {
                        open = part
                    }
                },
                Modifier.testTag("browse_chip_${part.name}"),
            )
        }
    }
    val showing = if (allOpen) BrowsePart.ALL else open
    showing?.let { part ->
        BrowsePartSheet(part, state, actions, onOpenPart = {
            open = it
            onAllOpen(false)
        }) {
            open = null
            onAllOpen(false)
        }
    }
}

/** The small sheet behind one chip, or every filter at once for [BrowsePart.ALL]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BrowsePartSheet(part: BrowsePart, state: BrowseBarState, actions: BrowseBarActions, onOpenPart: (BrowsePart) -> Unit, onDismiss: () -> Unit) {
    val q = state.query
    val f = q.filters
    fun set(filters: BrowseFilters) = actions.onQuery(q.copy(filters = filters, view = null))
    val show = SheetButton("Show ${"%,d".format(state.shown)} ${if (state.shown == 1) "climb" else "climbs"}", onDismiss, tag = "browse_show")
    when (part) {
        BrowsePart.ANGLE -> CruxSheet(onDismiss, Modifier.testTag("browse_sheet_angle"), title = "Angle", confirm = show) {
            AngleChoice(state.angles, f.angle) { set(f.copy(angle = it)) }
            Text(
                "Grades, ratings and ascents are per angle, so the list follows it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        BrowsePart.GRADE -> CruxSheet(
            onDismiss,
            Modifier.testTag("browse_sheet_grade"),
            title = "Grade",
            action = SheetButton("Any grade", { set(f.copy(gradeFrom = null, gradeTo = null)) }, tag = "browse_grade_any"),
            confirm = show,
        ) {
            GradeGrid(state, onRange = { from, to -> set(f.copy(gradeScale = state.scale, gradeFrom = from, gradeTo = to)) })
        }

        BrowsePart.YOU -> CruxSheet(onDismiss, Modifier.testTag("browse_sheet_you"), title = "You", confirm = show) {
            YouChoice(f.you) { set(f.copy(you = it)) }
            Text(
                "New and Tried together is every climb you haven't sent.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        BrowsePart.RATING -> CruxSheet(onDismiss, Modifier.testTag("browse_sheet_rating"), title = "Rating", confirm = show) {
            CruxSegmentedButtons(RATINGS, f.minRating, ::ratingLabel, { set(f.copy(minRating = it)) })
            Text(
                "The board's own rating at this angle, out of 3 or more.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        BrowsePart.ASCENTS -> CruxSheet(onDismiss, Modifier.testTag("browse_sheet_ascents"), title = "Ascents", confirm = show) {
            CruxSegmentedButtons(ASCENTS, f.minAscents, ::ascentsLabel, { set(f.copy(minAscents = it)) })
            Text("Skip the climbs few people have repeated.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        BrowsePart.SETTER -> CruxSheet(
            onDismiss,
            Modifier.testTag("browse_sheet_setter"),
            title = "Setter",
            action = SheetButton("Anyone", { set(f.copy(setters = emptySet())) }, tag = "browse_setter_any"),
            confirm = show,
        ) {
            SetterChoice(state.setters, f.setters) { set(f.copy(setters = it)) }
        }

        BrowsePart.SORT -> CruxSheet(
            onDismiss,
            Modifier.testTag("browse_sheet_sort"),
            title = "Sort",
            action = SheetButton("Done", onDismiss, tag = "browse_sort_done"),
        ) {
            SortChain(q.sorts, state.catalogue) { actions.onQuery(q.copy(sorts = it, view = null)) }
        }

        BrowsePart.VIEW -> CruxSheet(onDismiss, Modifier.testTag("browse_sheet_view"), title = "Views") {
            Views(state, actions, onPicked = onDismiss)
        }

        BrowsePart.BENCHMARKS -> onDismiss()

        BrowsePart.ALL -> CruxSheet(
            onDismiss,
            Modifier.testTag("browse_sheet_all"),
            title = "Filters",
            action = SheetButton("Clear all", { set(f.cleared()) }, tag = "browse_clear_all"),
            confirm = show,
        ) {
            val shape = MaterialTheme.shapes.large
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(CruxTheme.size.borderHairline, MaterialTheme.colorScheme.outlineVariant, shape),
            ) {
                var first = true

                @Composable
                fun Line(label: String, content: @Composable () -> Unit) {
                    if (!first) Box(Modifier.fillMaxWidth().height(CruxTheme.size.borderHairline).background(MaterialTheme.colorScheme.outlineVariant))
                    first = false
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = CruxTheme.space.s3, vertical = CruxTheme.space.s2),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
                    ) {
                        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(72.dp))
                        Box(Modifier.weight(1f)) { content() }
                    }
                }

                @Composable
                fun Opens(value: String, part: BrowsePart) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { onOpenPart(part) }
                            .testTag("browse_all_${part.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                    }
                }
                if (state.catalogue && state.angles.size > 1) Line("Angle") { AngleChoice(state.angles, f.angle) { set(f.copy(angle = it)) } }
                Line("Grade") { Opens(gradeChip(f) ?: "Any grade", BrowsePart.GRADE) }
                Line("You") { YouChoice(f.you) { set(f.copy(you = it)) } }
                if (state.catalogue) {
                    Line("Rating") { CruxSegmentedButtons(RATINGS, f.minRating, ::ratingLabel, { set(f.copy(minRating = it)) }) }
                    Line("Ascents") { CruxSegmentedButtons(ASCENTS, f.minAscents, ::ascentsLabel, { set(f.copy(minAscents = it)) }) }
                }
                Line("Setter") { Opens(settersChip(f.setters) ?: "Anyone", BrowsePart.SETTER) }
                if (state.catalogue) {
                    if (!first) Box(Modifier.fillMaxWidth().height(CruxTheme.size.borderHairline).background(MaterialTheme.colorScheme.outlineVariant))
                    CruxSwitchRow(
                        "Benchmarks only",
                        null,
                        f.benchmarks,
                        { set(f.copy(benchmarks = it)) },
                        Modifier.heightIn(min = 56.dp).padding(horizontal = CruxTheme.space.s3).testTag("browse_benchmarks"),
                        titleStyle = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            Views(state, actions, onPicked = {})
        }
    }
}

@Composable
private fun AngleChoice(angles: List<Int>, angle: Int?, onAngle: (Int) -> Unit) {
    if (angles.size <= 6) {
        CruxSegmentedButtons(angles, angle ?: angles.first(), { "$it" }, onAngle)
    } else {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
            angles.forEach { a -> CruxFilterChip("$a°", a == angle, { onAngle(a) }, Modifier.testTag("browse_angle_$a")) }
        }
    }
}

/** New, Tried, Sent: any of them; none (or all) is everyone's. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YouChoice(you: Set<YouState>, onYou: (Set<YouState>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
        YouState.entries.forEach { s ->
            CruxFilterChip(s.label, s in you, { onYou(if (s in you) you - s else you + s) }, Modifier.testTag("browse_you_${s.name}"))
        }
    }
}

/**
 * The grade grid: every grade the board has here, with a bar for how many climbs it has. Tap the
 * easiest, then the hardest; tapping again starts a new range. Presets follow your sends here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradeGrid(state: BrowseBarState, onRange: (Int?, Int?) -> Unit) {
    val scale = state.scale ?: run {
        Text("These climbs have no grades to filter by.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val f = state.query.filters
    val counts = state.gradeCounts
    val span = if (counts.isEmpty()) scale.grades.indices else counts.keys.min()..counts.keys.max()
    val most = (counts.values.maxOrNull() ?: 1).coerceAtLeast(1)
    // After the first tap, the range waits for its other end.
    var pending by remember { mutableStateOf<Int?>(null) }
    val from = f.gradeFrom.takeIf { f.gradeScale == scale }
    val to = f.gradeTo.takeIf { f.gradeScale == scale }
    state.maxSent?.let { max ->
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
            GradePreset.entries.forEach { preset ->
                val r = preset.range(max, scale.grades.size)
                CruxFilterChip(preset.label, from == r.first && to == r.last, {
                    pending = null
                    onRange(r.first, r.last)
                }, Modifier.testTag("browse_preset_${preset.name}"))
            }
        }
    }
    val cells = span.toList()
    Column(verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), modifier = Modifier.testTag("browse_grade_grid")) {
        cells.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { i ->
                    val inRange =
                        (from != null && to != null && i in from..to) || (from != null && to == null && i >= from) || (from == null && to != null && i <= to)
                    val end = i == from || i == to || i == pending
                    val colors = MaterialTheme.colorScheme
                    val n = counts[i] ?: 0
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .semantics(mergeDescendants = true) {
                                contentDescription = "${scale.label(i)}, $n ${if (n == 1) "climb" else "climbs"}"
                                selected = inRange
                            }
                            .toggleable(inRange, role = Role.Button) {
                                val p = pending
                                if (p == null) {
                                    pending = i
                                    onRange(i, i)
                                } else {
                                    pending = null
                                    onRange(minOf(p, i), maxOf(p, i))
                                }
                            }
                            .testTag("browse_grade_$i"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.height(22.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(
                                Modifier
                                    .fillMaxWidth(0.5f)
                                    .height((22f * n / most).coerceAtLeast(if (n > 0) 2f else 0f).dp)
                                    .background(
                                        if (inRange) colors.primary.copy(alpha = 0.6f) else colors.surfaceContainerHighest,
                                        RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp),
                                    ),
                            )
                        }
                        Box(
                            Modifier
                                .padding(top = 3.dp)
                                .fillMaxWidth()
                                .height(40.dp)
                                .background(if (inRange) colors.primaryContainer else colors.surfaceContainerLow, RoundedCornerShape(8.dp))
                                .border(
                                    if (end) {
                                        BorderStroke(
                                            CruxTheme.size.borderEmphasis,
                                            colors.primary,
                                        )
                                    } else {
                                        BorderStroke(CruxTheme.size.borderHairline, colors.outlineVariant)
                                    },
                                    RoundedCornerShape(8.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                scale.label(i),
                                style = CruxTheme.type.code,
                                color = if (inRange) colors.onPrimaryContainer else colors.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                repeat(6 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
    Text(
        if (pending != null) "Now tap the hardest grade." else "Tap the easiest grade, then the hardest. The bars show how many climbs each grade has.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Setters with climbs in the list, searchable; tick any number. */
@Composable
private fun SetterChoice(setters: List<Pair<String, Int>>, picked: Set<String>, onPicked: (Set<String>) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    CruxTextField("", search, { search = it.take(40) }, placeholder = "Search setters", modifier = Modifier.testTag("browse_setter_search"))
    val shown = (picked.map { p -> p to (setters.firstOrNull { it.first == p }?.second ?: 0) } + setters.filter { it.first !in picked })
        .filter { search.isBlank() || it.first.contains(search.trim(), ignoreCase = true) }
        .take(60)
    if (shown.isEmpty()) Text("No setter by that name here.", style = MaterialTheme.typography.bodyMedium)
    shown.forEach { (name, n) ->
        val on = name in picked
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(on, role = Role.Checkbox) { onPicked(if (on) picked - name else picked + name) }
                .testTag("browse_setter_$name"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = on, onCheckedChange = null)
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(start = CruxTheme.space.s2))
            Text("$n", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * The sort chain: up to three lines, each a key and a direction, held and dragged to reorder
 * (TalkBack has Move up and Move down). The first line decides; the next breaks its ties.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortChain(sorts: List<SortLine>, catalogue: Boolean, onSorts: (List<SortLine>) -> Unit) {
    val heights = remember { mutableStateMapOf<SortKey, Int>() }
    var dragging by remember { mutableStateOf<SortKey?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val gap = with(androidx.compose.ui.platform.LocalDensity.current) { CruxTheme.space.s2.toPx() }
    fun move(from: Int, to: Int) {
        if (from == to || to !in sorts.indices) return
        onSorts(sorts.toMutableList().also { it.add(to, it.removeAt(from)) })
    }
    Column(verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), modifier = Modifier.testTag("browse_sorts")) {
        sorts.forEachIndexed { index, line ->
            key(line.key) {
                val lifted = dragging == line.key
                val shape = MaterialTheme.shapes.medium
                Row(
                    Modifier
                        .onSizeChanged { heights[line.key] = it.height }
                        .zIndex(if (lifted) 1f else 0f)
                        .offset { IntOffset(0, if (lifted) offset.roundToInt() else 0) }
                        .then(if (lifted) Modifier.shadow(8.dp, shape) else Modifier)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .border(CruxTheme.size.borderHairline, MaterialTheme.colorScheme.outlineVariant, shape)
                        .padding(vertical = CruxTheme.space.s1)
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction("Move up") { move(index, index - 1).let { true } },
                                CustomAccessibilityAction("Move down") { move(index, index + 1).let { true } },
                            )
                        }
                        .testTag("browse_sort_${line.key.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.DragIndicator,
                        contentDescription = "Hold and drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = CruxTheme.space.s2)
                            .pointerInput(line.key, sorts) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragging = line.key
                                        offset = 0f
                                    },
                                    onDragEnd = {
                                        val step = (heights[line.key] ?: 1) + gap
                                        move(index, (index + (offset / step).roundToInt()).coerceIn(0, sorts.lastIndex))
                                        dragging = null
                                        offset = 0f
                                    },
                                    onDragCancel = {
                                        dragging = null
                                        offset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        offset += amount.y
                                    },
                                )
                            },
                    )
                    Box(
                        Modifier.size(22.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Column(Modifier.weight(1f).padding(start = CruxTheme.space.s2)) {
                        Text(line.key.label, style = MaterialTheme.typography.titleSmall)
                        Text(line.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = { onSorts(sorts.map { if (it.key == line.key) it.copy(descending = !it.descending) else it }) },
                        modifier = Modifier.testTag("browse_sort_dir_${line.key.name}"),
                    ) {
                        Icon(
                            if (line.descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                            contentDescription = "${line.description}, tap to turn it round",
                        )
                    }
                    IconButton(
                        onClick = { onSorts(sorts.filterNot { it.key == line.key }) },
                        enabled = sorts.size > 1,
                        modifier = Modifier.testTag("browse_sort_remove_${line.key.name}"),
                    ) { Icon(Icons.Rounded.Close, contentDescription = "Remove ${line.key.label}") }
                }
            }
        }
    }
    val more = SortKey.entries.filter { k -> (catalogue || !k.catalogueOnly) && sorts.none { it.key == k } }
    if (sorts.size < 3 && more.isNotEmpty()) {
        Eyebrow("Then by", Modifier.padding(top = CruxTheme.space.s1))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
            more.forEach { k ->
                androidx.compose.material3.AssistChip(
                    onClick = { onSorts(sorts + SortLine(k)) },
                    label = { Text(k.label) },
                    leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("browse_sort_add_${k.name}"),
                )
            }
        }
    }
    Text(
        "Climbs are sorted by the first line. Where two tie, the next line decides. Hold a line's handle and drag to change the order.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Saved views as chips (tap to use, hold to delete), and Save as view. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Views(state: BrowseBarState, actions: BrowseBarActions, onPicked: () -> Unit) {
    var naming by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Eyebrow("Views", Modifier.weight(1f))
        TextButton(onClick = { naming = true }, modifier = Modifier.testTag("browse_save_view")) { Text("Save as view") }
    }
    if (state.views.isEmpty()) {
        Text(
            "Save these filters and sorts under a name to come back to them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
            state.views.forEach { view ->
                Box(
                    Modifier.pointerInput(view.name) {
                        detectTapGestures(onLongPress = { deleting = view.name })
                    },
                ) {
                    CruxFilterChip(view.name, state.query.view == view.name, {
                        val angle = state.query.filters.angle
                        // A view keeps the angle you're on, so it never jumps to another wall angle.
                        actions.onQuery(view.query.copy(filters = view.query.filters.copy(angle = angle ?: view.query.filters.angle), view = view.name))
                        onPicked()
                    }, Modifier.testTag("browse_view_${view.name}"))
                }
            }
        }
    }
    if (naming) {
        var name by rememberSaveable { mutableStateOf(state.query.view.orEmpty()) }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Save as view") },
            text = { CruxTextField("Name", name, { name = it.take(30) }, placeholder = "Warm-up, Session…", modifier = Modifier.testTag("browse_view_name")) },
            confirmButton = {
                TextButton(onClick = {
                    actions.onSaveView(name)
                    naming = false
                }, enabled = name.isNotBlank(), modifier = Modifier.testTag("browse_view_save")) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }
    deleting?.let { name ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete $name?") },
            text = { Text("The view goes; your climbs and filters stay as they are.") },
            confirmButton = {
                TextButton(onClick = {
                    actions.onDeleteView(name)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}
