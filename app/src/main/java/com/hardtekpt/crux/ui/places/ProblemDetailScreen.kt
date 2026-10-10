package com.hardtekpt.crux.ui.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.ClimbRepository
import com.hardtekpt.crux.data.PlaceRepository
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.PlaceDetail
import com.hardtekpt.crux.data.model.ProblemWithStats
import com.hardtekpt.crux.data.model.Project
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.GradeBadge
import com.hardtekpt.crux.ui.components.GradeState
import com.hardtekpt.crux.ui.components.ImageThumbnail
import com.hardtekpt.crux.ui.components.ImageViewer
import com.hardtekpt.crux.ui.components.StatTile
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.gradeState
import com.hardtekpt.crux.ui.journal.TapeDot
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.outcomeLine
import com.hardtekpt.crux.ui.shortLabel
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProblemDetailViewModel @Inject constructor(savedStateHandle: SavedStateHandle, places: PlaceRepository, climbs: ClimbRepository) : ViewModel() {
    val problemId: Long = savedStateHandle.get<Long>("problemId") ?: 0L

    val problem: StateFlow<ProblemWithStats?> = places.observeProblem(problemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val place: StateFlow<PlaceDetail?> = places.observeProblem(problemId).filterNotNull().map { it.problem.placeId }
        .flatMapLatest { places.observePlaceDetail(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val history: StateFlow<List<Climb>> = climbs.observeClimbsForProblem(problemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** One problem: how it's going (sessions, goes, send) and every go you logged on it. */
@Composable
fun ProblemDetailScreen(
    onBack: () -> Unit,
    onEdit: (placeId: Long, problemId: Long) -> Unit,
    onLogGo: (Long) -> Unit,
    onOpenClimb: (Long) -> Unit,
    viewModel: ProblemDetailViewModel = hiltViewModel(),
) {
    val item by viewModel.problem.collectAsStateWithLifecycle()
    val place by viewModel.place.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val space = CruxTheme.space
    val problem = item?.problem
    var viewingImage by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().testTag("screen_ProblemDetail")) {
        CruxTopAppBar(
            title = problem?.name.orEmpty(),
            onBack = onBack,
            actions = {
                if (problem != null) {
                    IconButton(onClick = { onEdit(problem.placeId, problem.id) }, modifier = Modifier.testTag("edit_problem")) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                    }
                }
            },
        )
        val current = item ?: return@Column
        val stats = current.stats
        val area = current.problem.areaId?.let { id -> place?.areas?.firstOrNull { it.id == id } }
        val areaName = area?.name
        LazyColumn(
            contentPadding = PaddingValues(start = space.s4, end = space.s4, bottom = space.s4 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s3)) {
                    GradeBadge(current.problem.grade, if (stats?.sent == true) GradeState.Sent else GradeState.Attempted)
                    current.problem.tape?.let { TapeDot(it) }
                    Text(
                        listOfNotNull(place?.place?.name, areaName, current.problem.discipline.label, current.problem.setDate?.let { "set ${it.shortLabel()}" })
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(space.s3)) {
                    StatTile(
                        label = "Goes",
                        value = (stats?.attempts ?: 0).toString(),
                        delta = stats?.let { "over ${it.sessions} ${if (it.sessions == 1) "session" else "sessions"}" } ?: "not tried yet",
                        modifier = Modifier.weight(1f),
                        valueModifier = Modifier.testTag("problem_goes"),
                    )
                    StatTile(
                        label = if (stats?.sent == true) "Sent" else "Status",
                        value = stats?.firstSend?.shortLabel() ?: if (stats == null) "–" else "Project",
                        isPersonalBest = stats?.sent == true,
                        delta = if (stats?.sent == true) null else stats?.let { "last go ${it.lastGo.shortLabel()}" },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            area?.imagePath?.let { image ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s3)) {
                        ImageThumbnail(image, "${area.name} image", onClick = { viewingImage = true }, size = 64.dp)
                        Text(
                            "Where it is: ${area.name}. Tap to view.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (viewingImage) ImageViewer(image, area.name) { viewingImage = false }
                }
            }
            current.problem.notes?.let { notes ->
                item { Text(notes, style = MaterialTheme.typography.bodyLarge) }
            }
            item {
                CruxButton(
                    text = "Log a go",
                    onClick = { onLogGo(current.problem.id) },
                    icon = Icons.Rounded.Add,
                    modifier = Modifier.testTag("log_go"),
                )
            }
            item { Eyebrow("Your goes", Modifier.padding(top = space.s3)) }
            if (history.isEmpty()) {
                item {
                    Text(
                        "Nothing logged on it yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(history, key = { it.id }) { climb ->
                CruxListRow(
                    title = climb.date.dayLabel(),
                    supporting = listOfNotNull(climb.outcomeLine(), climb.notes).joinToString(" · "),
                    leading = { GradeBadge(climb.grade, climb.gradeState) },
                    onClick = { onOpenClimb(climb.id) },
                    modifier = Modifier.testTag("problem_go"),
                )
            }
        }
    }
}

/** An open project in a list: grade, where it is, and how many goes so far. */
@Composable
fun ProjectRow(
    project: Project,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    lastGo: com.hardtekpt.crux.data.LoggedGo? = null,
    onGo: ((Long) -> Unit)? = null,
    onUndo: () -> Unit = {},
) {
    val stats = project.stats
    CruxListRow(
        title = project.problem.name,
        supporting = listOfNotNull(
            listOfNotNull(project.placeName, project.areaName).joinToString(" · "),
            "${stats.attempts} ${if (stats.attempts == 1) "go" else "goes"} over ${stats.sessions} ${if (stats.sessions == 1) "session" else "sessions"}",
        ).joinToString(" · "),
        leading = { GradeBadge(project.problem.grade, GradeState.Attempted) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
                project.problem.tape?.let { TapeDot(it) }
                if (onGo != null) QuickGoButton(project.problem.id, lastGo, onGo, onUndo)
            }
        },
        onClick = { onOpen(project.problem.id) },
        modifier = modifier.testTag("project_row"),
    )
}

/**
 * "+1 go": logs an attempt on the problem today, straight from a list. For a few seconds after,
 * it turns into Undo.
 */
@Composable
fun QuickGoButton(problemId: Long, lastGo: com.hardtekpt.crux.data.LoggedGo?, onGo: (Long) -> Unit, onUndo: () -> Unit) {
    if (lastGo?.problemId == problemId) {
        CruxButton("Undo", onUndo, variant = CruxButtonVariant.Text, size = CruxButtonSize.Small, modifier = Modifier.testTag("project_undo"))
    } else {
        CruxButton("+1 go", { onGo(problemId) }, variant = CruxButtonVariant.Tonal, size = CruxButtonSize.Small, modifier = Modifier.testTag("project_go"))
    }
}
