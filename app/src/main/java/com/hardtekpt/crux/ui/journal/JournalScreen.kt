package com.hardtekpt.crux.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.ClimbRepository
import com.hardtekpt.crux.data.LoggedResult
import com.hardtekpt.crux.data.Note
import com.hardtekpt.crux.data.NoteRepository
import com.hardtekpt.crux.data.RecordRepository
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.Venue
import com.hardtekpt.crux.ui.components.EmptyState
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.ImageThumbnail
import com.hardtekpt.crux.ui.components.InlineEmptyState
import com.hardtekpt.crux.ui.components.VideoThumbnail
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.displayName
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.theme.Archivo
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.theme.JetBrainsMono
import com.hardtekpt.crux.ui.you.describe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import javax.inject.Inject

/** One journal group: a day at a place. */
data class JournalDay(
    val date: LocalDate,
    val place: String?,
    val climbs: List<Climb>,
) {
    val key: String get() = "$date|${place.orEmpty()}|$venue"
    val venue: Venue get() = climbs.first().venue
    val title: String get() = listOfNotNull(date.dayLabel(), place, venue.label).joinToString(" · ")
}

/** Climbs arrive newest first; grouping keeps that order. */
fun List<Climb>.groupByDayAndPlace(): List<JournalDay> =
    groupBy { Triple(it.date, it.place, it.venue) }.map { (key, climbs) -> JournalDay(key.first, key.second, climbs) }

/**
 * Something that happened on a day. Workout sessions will be another kind once the session
 * logger exists; until then training shows as the results logged on exercises.
 */
sealed interface TimelineEntry {
    val key: String
    val kind: JournalFilter

    /** Climbs at one place on one day. */
    data class Climbs(val day: JournalDay) : TimelineEntry {
        override val key get() = "climbs|${day.key}"
        override val kind get() = JournalFilter.Climbs
    }

    /** Results logged on exercises that day. */
    data class Training(val date: LocalDate, val results: List<LoggedResult>) : TimelineEntry {
        override val key get() = "training|$date"
        override val kind get() = JournalFilter.Training
    }

    data class NoteEntry(val note: Note) : TimelineEntry {
        override val key get() = "note|${note.id}"
        override val kind get() = JournalFilter.Notes
    }
}

/** A day on the timeline with everything logged on it. */
data class TimelineDay(val date: LocalDate, val entries: List<TimelineEntry>)

enum class JournalFilter(val label: String, val icon: ImageVector) {
    All("All", Icons.Rounded.Timeline),
    Climbs("Climbs", Icons.Rounded.Landscape),
    Training("Training", Icons.Rounded.FitnessCenter),
    Notes("Notes", Icons.AutoMirrored.Rounded.Notes),
}

/** Newest day first; within a day, climbs, then training, then notes. */
fun buildTimeline(climbs: List<Climb>, results: List<LoggedResult>, notes: List<Note>): List<TimelineDay> {
    val entries = climbs.groupByDayAndPlace().map { it.date to TimelineEntry.Climbs(it) } +
        results.groupBy { it.record.date }.map { (date, list) -> date to TimelineEntry.Training(date, list) } +
        notes.sortedByDescending { it.id }.map { it.created to TimelineEntry.NoteEntry(it) }
    return entries.groupBy({ it.first }, { it.second })
        .toSortedMap(compareByDescending { it })
        .map { (date, list) -> TimelineDay(date, list.sortedBy { it.kind.ordinal }) }
}

data class JournalUiState(
    val isLoading: Boolean = true,
    val days: List<TimelineDay> = emptyList(),
) {
    fun count(filter: JournalFilter): Int = days.sumOf { day ->
        day.entries.filter { filter == JournalFilter.All || it.kind == filter }.sumOf { entry ->
            when (entry) {
                is TimelineEntry.Climbs -> entry.day.climbs.size
                is TimelineEntry.Training -> entry.results.size
                is TimelineEntry.NoteEntry -> 1
            }
        }
    }
}

@HiltViewModel
class JournalViewModel @Inject constructor(
    climbRepository: ClimbRepository,
    recordRepository: RecordRepository,
    noteRepository: NoteRepository,
) : ViewModel() {
    val uiState: StateFlow<JournalUiState> = combine(
        climbRepository.observeClimbs(),
        recordRepository.observeResults(),
        noteRepository.observeNotes(),
    ) { climbs, results, notes -> JournalUiState(isLoading = false, days = buildTimeline(climbs, results, notes)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState())
}

/** Where the journal's taps lead. */
data class JournalActions(
    val openClimb: (Long) -> Unit = {},
    val openNote: (Long) -> Unit = {},
    val openRecords: (Long) -> Unit = {},
)

@Composable
fun JournalScreen(
    actions: JournalActions,
    viewModel: JournalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(JournalFilter.All) }
    JournalContent(uiState = uiState, filter = filter, onFilter = { filter = it }, actions = actions)
}

private val RAIL = 52.dp
private val MonoLabel = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.2.sp)

/**
 * The journal as one timeline: a rail runs down the left with each day pinned to it like a
 * route card, and every climb, training result and note hangs off it. Filters at the top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalContent(
    uiState: JournalUiState,
    modifier: Modifier = Modifier,
    filter: JournalFilter = JournalFilter.All,
    onFilter: (JournalFilter) -> Unit = {},
    actions: JournalActions = JournalActions(),
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val space = CruxTheme.space
    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("screen_Journal"),
    ) {
        CruxTopAppBar(title = "Journal", scrollBehavior = scrollBehavior)
        if (!uiState.isLoading && uiState.days.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                headline = "Nothing logged yet",
                sentence = "Tap Log to add a climb or a note; they line up here by day.",
            )
            return
        }
        FilterBar(uiState, filter, onFilter, Modifier.padding(horizontal = space.s4, vertical = space.s2))
        val days = uiState.days.mapNotNull { day ->
            val shown = day.entries.filter { filter == JournalFilter.All || it.kind == filter }
            if (shown.isEmpty()) null else day.copy(entries = shown)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s2, bottom = space.s4 + LocalNavBarClearance.current),
            modifier = Modifier.testTag("journal_list"),
        ) {
            if (days.isEmpty() && !uiState.isLoading) {
                item(key = "empty") {
                    InlineEmptyState(
                        icon = filter.icon,
                        text = when (filter) {
                            JournalFilter.Training -> "No training yet. Results you log on exercises show here, and workout sessions will too once the session logger arrives."
                            JournalFilter.Notes -> "No notes yet. Add one from the Log button."
                            else -> "No climbs yet. Tap Log, then Log climb."
                        },
                    )
                }
            }
            days.forEachIndexed { index, day ->
                item(key = "day_${day.date}") { DayHeader(day, first = index == 0) }
                items(day.entries, key = { it.key }) { entry ->
                    when (entry) {
                        is TimelineEntry.Climbs -> ClimbsEntry(entry.day, actions.openClimb)
                        is TimelineEntry.Training -> TrainingEntry(entry, actions.openRecords)
                        is TimelineEntry.NoteEntry -> NoteEntry(entry.note) { actions.openNote(entry.note.id) }
                    }
                }
            }
        }
    }
}

/** Four pills with live counts; the picked one fills in. */
@Composable
private fun FilterBar(uiState: JournalUiState, filter: JournalFilter, onFilter: (JournalFilter) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    // Four equal pills that always fit the width; the picked one shows its icon.
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("journal_filters"),
    ) {
        JournalFilter.entries.forEach { option ->
            val selected = option == filter
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(if (selected) colors.primaryContainer else Color.Transparent)
                    .border(
                        if (selected) CruxTheme.size.borderEmphasis else CruxTheme.size.borderHairline,
                        if (selected) colors.primary else colors.outline,
                        CircleShape,
                    )
                    .clickable { onFilter(option) }
                    .padding(horizontal = 6.dp, vertical = 8.dp)
                    .testTag("journal_filter_${option.name}"),
            ) {
                val content = if (selected) colors.onPrimaryContainer else colors.onSurface
                if (selected) Icon(option.icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
                Text(option.label, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
                Text(uiState.count(option).toString(), style = MonoLabel, color = if (selected) colors.primary else colors.onSurfaceVariant)
            }
        }
    }
}

/** The rail: a line down the left column, with an optional marker drawn at [markerY]. */
private fun Modifier.rail(color: Color, top: Boolean = true, bottom: Boolean = true): Modifier = drawBehind {
    val x = RAIL.toPx() / 2
    val width = 2.dp.toPx()
    drawLine(color, Offset(x, if (top) 0f else size.height / 2), Offset(x, if (bottom) size.height else size.height / 2), width)
}

/** A row on the rail: [marker] sits in the left column, [content] hangs off to the right. */
@Composable
private fun RailRow(
    modifier: Modifier = Modifier,
    markerTop: Dp = 14.dp,
    marker: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .rail(colors.outlineVariant),
    ) {
        Box(Modifier.width(RAIL).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.padding(top = markerTop)) { marker() }
        }
        Box(Modifier.weight(1f).padding(bottom = CruxTheme.space.s3)) { content() }
    }
}

/** The day as a block on the rail: big date number, month, weekday and what happened. */
@Composable
private fun DayHeader(day: TimelineDay, first: Boolean) {
    val colors = MaterialTheme.colorScheme
    val climbs = day.entries.filterIsInstance<TimelineEntry.Climbs>().sumOf { it.day.climbs.size }
    val sends = day.entries.filterIsInstance<TimelineEntry.Climbs>().sumOf { e -> e.day.climbs.count { it.style.isSend } }
    val results = day.entries.filterIsInstance<TimelineEntry.Training>().sumOf { it.results.size }
    val notes = day.entries.count { it is TimelineEntry.NoteEntry }
    val summary = listOfNotNull(
        climbs.takeIf { it > 0 }?.let { "$it ${if (it == 1) "climb" else "climbs"} · $sends sent" },
        results.takeIf { it > 0 }?.let { "$it ${if (it == 1) "result" else "results"}" },
        notes.takeIf { it > 0 }?.let { "$it ${if (it == 1) "note" else "notes"}" },
    ).joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .rail(colors.outlineVariant, top = !first)
            .padding(top = if (first) 0.dp else CruxTheme.space.s2, bottom = CruxTheme.space.s3)
            .testTag("journal_day"),
    ) {
        // The date tile sits on the rail like a tag clipped to a rope.
        Box(Modifier.width(RAIL), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceContainerHigh)
                    .border(CruxTheme.size.borderHairline, colors.outlineVariant, RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Text(day.date.dayOfMonth.toString(), style = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 24.sp))
                Text(day.date.month.getDisplayName(DateTextStyle.SHORT, Locale.UK).take(3).uppercase(), style = MonoLabel.copy(fontSize = 10.sp), color = colors.onSurfaceVariant)
            }
        }
        Column(Modifier.padding(start = CruxTheme.space.s3)) {
            Text(day.date.dayLabel(), style = MaterialTheme.typography.titleMedium)
            Text(summary.uppercase(), style = MonoLabel, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Climbs at a place: the place on top, then each climb as a strip of route tape. */
@Composable
private fun ClimbsEntry(day: JournalDay, onOpen: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    RailRow(marker = { Marker(Icons.Rounded.Landscape, colors.primary) }) {
        Column {
            Text(
                listOfNotNull(day.place, day.venue.label).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
            )
            day.climbs.forEach { climb -> TapeRow(climb, onClick = { onOpen(climb.id) }) }
        }
    }
}

/**
 * One climb as a strip of tape: the tape colour (the gym's colour on local scales, else
 * green for a send and grey for a go), the grade, the name, how it went and how hard it felt.
 */
@Composable
private fun TapeRow(climb: Climb, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val sent = climb.style.isSend
    val tape = climb.gradeColour?.let { com.hardtekpt.crux.ui.components.input.argb(it) } ?: if (sent) CruxTheme.colors.success else colors.outline
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .testTag("journal_climb"),
    ) {
        Box(
            Modifier
                .width(6.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(tape),
        )
        Text(
            climb.grade,
            style = CruxTheme.type.grade.copy(fontSize = 18.sp),
            color = if (sent) colors.onSurface else colors.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
            maxLines = 1,
        )
        Column(Modifier.weight(1f)) {
            Text(climb.displayName(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    climb.style.label.uppercase(),
                    style = MonoLabel,
                    color = if (sent) CruxTheme.colors.success else colors.onSurfaceVariant,
                )
                if (!climb.style.singleAttempt) {
                    Text("× ${climb.attempts}", style = MonoLabel, color = colors.onSurfaceVariant)
                }
                climb.effort?.let { EffortTicks(it) }
            }
            climb.notes?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            climb.imagePath != null -> ImageThumbnail(climb.imagePath, "Photo", onClick = onClick, size = 40.dp)
            climb.videoPath != null -> VideoThumbnail(climb.videoPath, "Video", onClick = onClick, size = 40.dp)
        }
    }
}

/** Effort as ten small ticks, the felt ones lit. */
@Composable
private fun EffortTicks(effort: Int) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = 4.dp)) {
        repeat(10) { i ->
            Box(
                Modifier
                    .width(3.dp)
                    .height((5 + i * 0.6f).dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i < effort) colors.tertiary else colors.outlineVariant),
            )
        }
    }
}

@Composable
private fun TrainingEntry(entry: TimelineEntry.Training, onOpen: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    RailRow(marker = { Marker(Icons.Rounded.FitnessCenter, colors.tertiary) }) {
        Column {
            Text("Training", style = MaterialTheme.typography.labelLarge, color = colors.tertiary, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            entry.results.forEach { result ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpen(result.exercise.id) }
                        .padding(vertical = 6.dp)
                        .testTag("journal_training"),
                ) {
                    Text(result.exercise.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (result.isBest) {
                        Icon(Icons.Rounded.EmojiEvents, contentDescription = "Personal record", tint = colors.secondary, modifier = Modifier.size(16.dp))
                    }
                    Text(
                        result.record.describe(result.exercise.metric),
                        style = CruxTheme.type.gradeSmall,
                        color = if (result.isBest) colors.secondary else colors.onSurface,
                    )
                }
            }
        }
    }
}

/** A note as a torn-off strip: tag, first line, a line of the rest. */
@Composable
private fun NoteEntry(note: Note, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    RailRow(marker = { Marker(Icons.AutoMirrored.Rounded.Notes, colors.onSurfaceVariant) }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceContainerLow)
                .clickable(onClick = onClick)
                .height(IntrinsicSize.Min)
                .testTag("journal_note"),
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(colors.onSurfaceVariant.copy(alpha = 0.4f)))
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                note.tag?.let { Text(it.uppercase(), style = MonoLabel, color = colors.primary) }
                Text(note.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (note.body.isNotBlank()) {
                    Text(note.body, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A round marker on the rail, cut out of the line so the line seems to pass behind it. */
@Composable
private fun Marker(icon: ImageVector, tint: Color) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(colors.surface)
            .border(CruxTheme.size.borderEmphasis, tint, CircleShape),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
    }
}
