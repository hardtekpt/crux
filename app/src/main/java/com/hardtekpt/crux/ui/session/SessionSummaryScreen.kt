package com.hardtekpt.crux.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.PlaceRepository
import com.hardtekpt.crux.data.Session
import com.hardtekpt.crux.data.SessionItem
import com.hardtekpt.crux.data.SessionRepository
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.Place
import com.hardtekpt.crux.data.prefs.UnitSystem
import com.hardtekpt.crux.ui.LocalUnits
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.input.argb
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.displayName
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SessionSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessions: SessionRepository,
    places: PlaceRepository,
    private val clock: Clock,
) : ViewModel() {
    private val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L

    /** Null while loading, or once the session is deleted. */
    val session: StateFlow<Session?> = sessions.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val place: StateFlow<Place?> = session.map { it?.placeId }.distinctUntilChanged().map { id -> id?.let { places.getPlace(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val zone get() = clock.zone

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            sessions.discard(sessionId)
            onDone()
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

/**
 * A finished session at a glance: when, where and for how long, how it felt, what got done
 * against the plan, every set of every exercise, the climbs and the note.
 */
@Composable
fun SessionSummaryScreen(onBack: () -> Unit, onOpenClimb: (Long) -> Unit, viewModel: SessionSummaryViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val place by viewModel.place.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val space = CruxTheme.space
    val colors = MaterialTheme.colorScheme
    val imperial = LocalUnits.current == UnitSystem.IMPERIAL

    Column(Modifier.fillMaxSize().testTag("screen_SessionSummary")) {
        CruxTopAppBar(
            title = session?.name.orEmpty(),
            onBack = onBack,
            actions = {
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("delete_session")) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Delete session")
                }
            },
        )
        val current = session ?: return@Column
        val start = Instant.ofEpochMilli(current.startedAtMillis).atZone(viewModel.zone)
        val end = current.endedAtMillis?.let { Instant.ofEpochMilli(it).atZone(viewModel.zone) }
        LazyColumn(
            contentPadding = PaddingValues(start = space.s4, end = space.s4, bottom = space.s6 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s3),
            modifier = Modifier.testTag("session_summary_list"),
        ) {
            item(key = "when") {
                Text(
                    listOfNotNull(
                        start.toLocalDate().dayLabel(),
                        "${start.format(TIME)}–${end?.format(TIME) ?: "now"}",
                        place?.name,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            // The headline numbers.
            item(key = "figures") {
                val sent = current.climbGroups.count { it.style.isSend }
                Row(horizontalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.height(IntrinsicSize.Min).testTag("session_summary_figures")) {
                    SummaryFigure(durationLabel(current.durationMillis(current.startedAtMillis)), "long", Modifier.weight(1f))
                    if (current.items.isNotEmpty()) {
                        SummaryFigure("${current.setsDone}/${current.setsPlanned}", "sets done", Modifier.weight(1f))
                    }
                    SummaryFigure(
                        current.climbGroups.size.toString(),
                        if (current.climbGroups.size == 1) "climb, $sent sent" else "climbs, $sent sent",
                        Modifier.weight(1f),
                    )
                    current.effort?.let { SummaryFigure("$it/10", "felt", Modifier.weight(1f)) }
                }
            }
            current.notes?.let { notes ->
                item(key = "notes") {
                    Text(notes, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("session_summary_notes"))
                }
            }
            // Every exercise with each set as it went.
            if (current.items.isNotEmpty()) {
                item(key = "exercises_label") { Eyebrow("Exercises", Modifier.padding(top = space.s2)) }
                items(current.items, key = { "item_${it.id}" }) { item -> ExerciseSummary(item, imperial) }
            }
            if (current.climbGroups.isNotEmpty()) {
                item(key = "climbs_label") { Eyebrow("Climbs", Modifier.padding(top = space.s2)) }
                item(key = "climbs") {
                    Panel {
                        current.climbGroups.sortedBy { it.id }.forEachIndexed { index, climb ->
                            if (index > 0) HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))
                            ClimbSummaryLine(climb, onClick = { onOpenClimb(climb.id) })
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Delete this session?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Its sets are deleted. Climbs you logged stay in the journal.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }, modifier = Modifier.testTag("confirm_delete_session")) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun SummaryFigure(value: String, label: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainerLow)
            .border(CruxTheme.size.borderHairline, colors.outlineVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(value, style = CruxTheme.type.metricMedium.copy(fontSize = 22.sp), maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

/** An exercise: name, how many sets got done of the planned, and each set as logged. */
@Composable
private fun ExerciseSummary(item: SessionItem, imperial: Boolean) {
    val colors = MaterialTheme.colorScheme
    Panel(Modifier.testTag("session_summary_exercise")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) {
            Text(
                item.exercise.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (item.target.sets == 1) (if (item.done == 1) "Done" else "") else "${item.done} of ${item.target.sets} sets",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        if (item.sets.isEmpty()) {
            Text("Not done", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 10.dp))
        } else {
            Column(Modifier.padding(bottom = 8.dp)) {
                item.sets.forEach { set ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(
                            "Set ${set.setIndex + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.width(56.dp),
                        )
                        Text(
                            if (set.skipped) "Skipped" else describeSet(item.exercise.metric, set.reps, set.seconds, set.loadKg, imperial),
                            style = CruxTheme.type.gradeSmall,
                            color = if (set.skipped) colors.onSurfaceVariant else colors.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClimbSummaryLine(climb: Climb, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val sent = climb.style.isSend
    val tape = climb.gradeColour?.let { argb(it) } ?: if (sent) CruxTheme.colors.success else colors.outline
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp).testTag("session_summary_climb"),
    ) {
        Box(Modifier.width(4.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(tape))
        Text(climb.grade, style = CruxTheme.type.grade, modifier = Modifier.width(44.dp), maxLines = 1)
        Text(climb.displayName(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            climb.style.label + if (!climb.style.singleAttempt) " · ${climb.attempts}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = if (sent) CruxTheme.colors.success else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerLow)
            .border(CruxTheme.size.borderHairline, colors.outlineVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = CruxTheme.space.s3),
    ) { content() }
}
