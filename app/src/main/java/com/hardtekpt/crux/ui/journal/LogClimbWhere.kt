package com.hardtekpt.crux.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.hardtekpt.crux.ui.places.placeIcon
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
 * Place, wall and problem for the climb: three optional selectors, each narrowing the next.
 * Picking a problem fills in the climb's name and grade. With no place picked, a small
 * gym, crag or board switch says where it was.
 */
@Composable
fun WhereSection(
    draft: LogClimbDraft,
    places: List<PlaceSummary>,
    detail: PlaceDetail?,
    actions: WhereActions,
) {
    val space = CruxTheme.space
    var sheet by rememberSaveable { mutableStateOf<WhereSheet?>(null) }
    var creatingPlace by rememberSaveable { mutableStateOf(false) }
    val place = detail?.place?.takeIf { it.id == draft.placeId }
    val area = place?.let { detail.areas.firstOrNull { it.id == draft.areaId } }
    val problem = place?.let { detail.problems.firstOrNull { it.problem.id == draft.problemId } }
    val problemNoun = if (place?.type == PlaceType.CRAG || draft.discipline == com.hardtekpt.crux.data.model.Discipline.ROUTE) "Route" else "Problem"

    Column(verticalArrangement = Arrangement.spacedBy(space.s2)) {
        Eyebrow("Where · optional")
        SelectorField(
            label = "Place",
            value = when {
                place != null -> place.name
                draft.place.isNotBlank() -> "${draft.place} (not saved)"
                else -> null
            },
            placeholder = "None",
            leading = place?.let { { Icon(placeIcon(it.type), contentDescription = null) } },
            onClick = { sheet = WhereSheet.PLACE },
            onClear = if (place != null) ({ actions.selectPlace(null) }) else null,
            modifier = Modifier.testTag("select_place"),
        )
        SelectorField(
            label = place?.type?.areaLabel ?: "Area",
            value = area?.name,
            placeholder = if (place == null) "Pick a place first" else if (detail.areas.isEmpty()) "None set up" else "Any",
            enabled = place != null && detail.areas.isNotEmpty(),
            onClick = { sheet = WhereSheet.AREA },
            onClear = if (area != null) ({ actions.selectArea(null) }) else null,
            modifier = Modifier.testTag("select_area"),
        )
        SelectorField(
            label = problemNoun,
            value = when {
                problem != null -> "${problem.problem.name} · ${problem.problem.grade}"
                draft.saveAsProblem -> "New: saved from this climb"
                else -> null
            },
            placeholder = if (place == null) "Pick a place first" else "None",
            enabled = place != null,
            leading = problem?.problem?.tape?.let { tape -> { TapeDot(tape) } },
            onClick = { sheet = WhereSheet.PROBLEM },
            onClear = when {
                problem != null -> actions.clearProblem
                draft.saveAsProblem -> ({ actions.setSaveAsProblem(false) })
                else -> null
            },
            modifier = Modifier.testTag("select_problem"),
        )

        if (place == null) {
            CruxSegmentedButtons(
                options = listOf(Venue.GYM, Venue.CRAG, Venue.BOARD),
                selected = draft.venue,
                label = { it.label },
                onSelect = actions.setVenue,
                modifier = Modifier.testTag("venue"),
            )
        }
        if (place?.type == PlaceType.BOARD) {
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
    }

    when (sheet) {
        WhereSheet.PLACE -> PickerSheet(title = "Place", onDismiss = { sheet = null }) {
            item {
                PickRow("None", selected = draft.placeId == null, tag = "place_none") {
                    actions.selectPlace(null)
                    sheet = null
                }
            }
            items(places, key = { it.place.id }) { summary ->
                PickRow(
                    title = summary.place.name,
                    supporting = listOfNotNull(summary.place.type.label, summary.place.location).joinToString(" · "),
                    icon = placeIcon(summary.place.type),
                    selected = draft.placeId == summary.place.id,
                    tag = "place_${summary.place.name}",
                ) {
                    actions.selectPlace(summary.place.id)
                    sheet = null
                }
            }
            item {
                PickRow("New place", icon = Icons.Rounded.Add, selected = false, tag = "new_place_chip") {
                    sheet = null
                    creatingPlace = true
                }
            }
        }
        WhereSheet.AREA -> if (detail != null) {
            PickerSheet(title = detail.place.type.areaLabel, onDismiss = { sheet = null }) {
                item {
                    PickRow("Any", selected = draft.areaId == null, tag = "area_any") {
                        actions.selectArea(null)
                        sheet = null
                    }
                }
                items(detail.areas, key = { it.id }) { item ->
                    PickRow(
                        title = item.name,
                        supporting = item.angle?.let { "$it°" },
                        selected = draft.areaId == item.id,
                        tag = "area_${item.name}",
                    ) {
                        actions.selectArea(item.id)
                        sheet = null
                    }
                }
            }
        }
        WhereSheet.PROBLEM -> if (detail != null) {
            ProblemPickerSheet(
                detail = detail,
                areaId = draft.areaId,
                noun = problemNoun.lowercase(),
                onPick = {
                    actions.pickProblem(it)
                    sheet = null
                },
                onNone = {
                    actions.clearProblem()
                    actions.setSaveAsProblem(false)
                    sheet = null
                },
                onSaveNew = {
                    actions.clearProblem()
                    actions.setSaveAsProblem(true)
                    sheet = null
                },
                onDismiss = { sheet = null },
            )
        }
        null -> Unit
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
}

private enum class WhereSheet { PLACE, AREA, PROBLEM }

/** A field that opens a picker: label above, the pick (or a quiet placeholder), a chevron. */
@Composable
private fun SelectorField(
    label: String,
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onClear: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (enabled) colors.surfaceContainerLow else Color.Transparent, shape)
            .border(CruxTheme.size.borderHairline, if (enabled) colors.outline else colors.outlineVariant, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = CruxTheme.space.s3, end = CruxTheme.space.s1),
    ) {
        if (leading != null) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides colors.onSurfaceVariant) { leading() }
            }
        }
        Column(Modifier.weight(1f).padding(vertical = CruxTheme.space.s2)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(
                value ?: placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    !enabled -> colors.onSurfaceVariant.copy(alpha = 0.6f)
                    value == null -> colors.onSurfaceVariant
                    else -> colors.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClear != null) {
            IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, contentDescription = "Clear $label") }
        } else {
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = colors.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.padding(end = CruxTheme.space.s2),
            )
        }
    }
}

@Composable
private fun PickerSheet(title: String, onDismiss: () -> Unit, content: LazyListScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(horizontal = CruxTheme.space.s4)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = CruxTheme.space.s3))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
                contentPadding = PaddingValues(bottom = CruxTheme.space.s8),
                modifier = Modifier.navigationBarsPadding(),
                content = content,
            )
        }
    }
}

@Composable
private fun PickRow(
    title: String,
    selected: Boolean,
    tag: String,
    supporting: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    CruxListRow(
        title = title,
        supporting = supporting?.takeIf { it.isNotBlank() },
        leading = icon?.let { { Icon(it, contentDescription = null) } },
        trailing = if (selected) ({ Icon(Icons.Rounded.Check, contentDescription = "Picked", tint = MaterialTheme.colorScheme.primary) }) else null,
        selected = selected,
        onClick = onClick,
        modifier = Modifier.testTag(tag),
    )
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
    noun: String,
    onPick: (Problem) -> Unit,
    onNone: () -> Unit,
    onSaveNew: () -> Unit,
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
            Text("${noun.replaceFirstChar { it.uppercase() }}s at ${detail.place.name}", style = MaterialTheme.typography.headlineSmall)
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
                if (query.isBlank()) {
                    item { PickRow("None", selected = false, tag = "problem_none", onClick = onNone) }
                    item {
                        PickRow(
                            title = "Save this climb as a new $noun",
                            supporting = "Uses the name and grade you log",
                            icon = Icons.Rounded.Add,
                            selected = false,
                            tag = "save_as_problem",
                            onClick = onSaveNew,
                        )
                    }
                }
                if (matches.isEmpty()) {
                    item {
                        Text(
                            if (detail.problems.isEmpty()) "Nothing saved here yet." else "Nothing matches.",
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
