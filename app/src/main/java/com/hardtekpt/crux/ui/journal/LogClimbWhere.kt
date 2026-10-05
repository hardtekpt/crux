package com.hardtekpt.crux.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hardtekpt.crux.data.model.PlaceDetail
import com.hardtekpt.crux.data.model.PlaceSummary
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.model.Problem
import com.hardtekpt.crux.data.model.ProblemWithStats
import com.hardtekpt.crux.data.model.Venue
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.shortLabel
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxSegmentedButtons
import com.hardtekpt.crux.ui.components.CruxStepper
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.GradeBadge
import com.hardtekpt.crux.ui.components.GradeState
import com.hardtekpt.crux.ui.theme.CruxTheme

/** Everything the "Where" part of the log form can do. */
data class WhereActions(
    val selectPlace: (Long?) -> Unit,
    val createPlace: (String, PlaceType) -> Unit,
    val selectArea: (Long?) -> Unit,
    val pickProblem: (Problem) -> Unit,
    val clearProblem: () -> Unit,
    val setSaveAsProblem: (Boolean) -> Unit,
    val setAngle: (Int) -> Unit,
    val setVenue: (Venue) -> Unit,
    val setPlaceText: (String) -> Unit,
)

/**
 * Place, wall and problem for the climb. With no saved place picked it falls back to the
 * old gym/crag switch and a typed place name.
 */
@Composable
fun WhereSection(
    draft: LogClimbDraft,
    places: List<PlaceSummary>,
    detail: PlaceDetail?,
    actions: WhereActions,
) {
    val space = CruxTheme.space
    var creatingPlace by rememberSaveable { mutableStateOf(false) }
    var pickingProblem by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(space.s2)) {
        Eyebrow("Where")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.testTag("place_picker")) {
            item {
                CruxFilterChip(label = "No saved place", selected = draft.placeId == null, onClick = { actions.selectPlace(null) })
            }
            items(places, key = { it.place.id }) { summary ->
                CruxFilterChip(
                    label = summary.place.name,
                    selected = draft.placeId == summary.place.id,
                    onClick = { actions.selectPlace(summary.place.id) },
                    modifier = Modifier.testTag("place_${summary.place.name}"),
                )
            }
            item {
                CruxFilterChip(label = "+ New place", selected = false, onClick = { creatingPlace = true }, modifier = Modifier.testTag("new_place_chip"))
            }
        }

        if (draft.placeId == null || detail == null) {
            CruxSegmentedButtons(
                options = listOf(Venue.GYM, Venue.CRAG, Venue.BOARD),
                selected = draft.venue,
                label = { it.label },
                onSelect = actions.setVenue,
            )
            CruxTextField(
                label = "Place",
                value = draft.place,
                onValueChange = actions.setPlaceText,
                placeholder = "Block Lab",
                helper = "Optional. Save places to pick walls and problems.",
                modifier = Modifier.testTag("field_place"),
            )
            return@Column
        }

        val place = detail.place
        if (detail.areas.isNotEmpty()) {
            Text(place.type.areaLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                item { CruxFilterChip(label = "Any", selected = draft.areaId == null, onClick = { actions.selectArea(null) }) }
                items(detail.areas, key = { it.id }) { area ->
                    CruxFilterChip(
                        label = area.name,
                        selected = draft.areaId == area.id,
                        onClick = { actions.selectArea(area.id) },
                        modifier = Modifier.testTag("area_${area.name}"),
                    )
                }
            }
        }

        if (place.type == PlaceType.BOARD) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Angle", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                CruxStepper(
                    value = draft.angle ?: LogClimbViewModel.DEFAULT_ANGLE,
                    onValueChange = actions.setAngle,
                    range = 0..70,
                    unit = "deg",
                    step = 5,
                    testTagPrefix = "angle",
                )
            }
        }

        val picked = detail.problems.firstOrNull { it.problem.id == draft.problemId }
        if (picked != null) {
            CruxListRow(
                title = picked.problem.name,
                supporting = problemLine(picked, detail),
                leading = { GradeBadge(picked.problem.grade, GradeState.Attempted) },
                trailing = {
                    IconButton(onClick = actions.clearProblem) { Icon(Icons.Rounded.Close, contentDescription = "Clear problem") }
                },
                selected = true,
                modifier = Modifier.testTag("picked_problem"),
            )
        } else {
            CruxButton(
                text = "Pick a ${if (place.type == PlaceType.CRAG) "route or problem" else "problem"}",
                onClick = { pickingProblem = true },
                variant = CruxButtonVariant.Outlined,
                icon = Icons.Rounded.Search,
                modifier = Modifier.testTag("pick_problem"),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = draft.saveAsProblem,
                    onCheckedChange = actions.setSaveAsProblem,
                    modifier = Modifier.testTag("save_as_problem"),
                )
                Text(
                    "Save it as a problem at ${place.name}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (creatingPlace) {
        NewPlaceDialog(
            onCreate = { name, type ->
                actions.createPlace(name, type)
                creatingPlace = false
            },
            onDismiss = { creatingPlace = false },
        )
    }
    if (pickingProblem && detail != null) {
        ProblemPickerSheet(
            detail = detail,
            areaId = draft.areaId,
            onPick = {
                actions.pickProblem(it)
                pickingProblem = false
            },
            onDismiss = { pickingProblem = false },
        )
    }
}

/** `Cave · 3 sessions · 12 goes · sent 3 Oct`, or `project · 8 goes`. */
internal fun problemLine(item: ProblemWithStats, detail: PlaceDetail?): String {
    val area = item.problem.areaId?.let { id -> detail?.areas?.firstOrNull { it.id == id }?.name }
    val stats = item.stats
    val progress = when {
        stats == null -> "not tried yet"
        stats.sent -> "${stats.attempts} goes · sent ${stats.firstSend!!.shortLabel()}"
        else -> "project · ${stats.attempts} goes"
    }
    return listOfNotNull(area, progress).joinToString(" · ")
}

@Composable
private fun NewPlaceDialog(onCreate: (String, PlaceType) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(PlaceType.GYM) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("New place", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s3)) {
                CruxSegmentedButtons(PlaceType.entries, type, { it.label }, { type = it })
                CruxTextField(
                    label = "Name",
                    value = name,
                    onValueChange = { name = it.take(LogClimbViewModel.MAX_TEXT) },
                    placeholder = if (type == PlaceType.CRAG) "Arco" else if (type == PlaceType.BOARD) "Home Kilter" else "Block Lab",
                    modifier = Modifier.testTag("field_new_place"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name, type) }, enabled = name.isNotBlank(), modifier = Modifier.testTag("create_place")) {
                Text("Add place")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ProblemPickerSheet(
    detail: PlaceDetail,
    areaId: Long?,
    onPick: (Problem) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = detail.problems
        .filter { !it.problem.retired }
        .filter { areaId == null || it.problem.areaId == areaId }
        .filter { query.isBlank() || it.problem.name.contains(query.trim(), ignoreCase = true) || it.problem.grade.equals(query.trim(), true) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(horizontal = CruxTheme.space.s4)) {
            Text("Problems at ${detail.place.name}", style = MaterialTheme.typography.headlineSmall)
            CruxTextField(
                label = "Search by name or grade",
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .padding(top = CruxTheme.space.s3)
                    .testTag("problem_search"),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
                contentPadding = PaddingValues(bottom = CruxTheme.space.s8),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                if (matches.isEmpty()) {
                    item {
                        Text(
                            if (detail.problems.isEmpty()) {
                                "No problems saved here yet. Log this climb with a name and tick \"Save it as a problem\"."
                            } else {
                                "Nothing matches."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(matches, key = { it.problem.id }) { item ->
                    CruxListRow(
                        title = item.problem.name,
                        supporting = problemLine(item, detail),
                        leading = {
                            Box(contentAlignment = Alignment.TopEnd) {
                                GradeBadge(item.problem.grade, GradeState.Attempted)
                                item.problem.tape?.let { TapeDot(it) }
                            }
                        },
                        onClick = { onPick(item.problem) },
                        modifier = Modifier.testTag("pick_${item.problem.name}"),
                    )
                }
            }
        }
    }
}

/** A small dot in the problem's tape colour. */
@Composable
fun TapeDot(tape: Int, modifier: Modifier = Modifier) {
    val colors = CruxTheme.colors.tape
    Box(
        modifier
            .size(10.dp)
            .background(colors[tape.coerceIn(colors.indices)], CircleShape),
    )
}
