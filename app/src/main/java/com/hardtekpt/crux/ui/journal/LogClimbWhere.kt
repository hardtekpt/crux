package com.hardtekpt.crux.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.Place
import com.hardtekpt.crux.data.model.PlaceDetail
import com.hardtekpt.crux.data.model.PlaceSummary
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.model.Problem
import com.hardtekpt.crux.data.model.ProblemWithStats
import com.hardtekpt.crux.data.model.Venue
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxSegmentedButtons
import com.hardtekpt.crux.ui.components.CruxStepper
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.GradeBadge
import com.hardtekpt.crux.ui.components.GradeState
import com.hardtekpt.crux.ui.places.placeIcon
import com.hardtekpt.crux.ui.shortLabel
import com.hardtekpt.crux.ui.theme.CruxTheme

/** Everything the "Where" part of the log form can do. */
data class WhereActions(
    val selectPlace: (Long?) -> Unit,
    val createPlace: (String, PlaceType) -> Unit,
    val selectArea: (Long?) -> Unit,
    val setAngle: (Int) -> Unit,
    val setVenue: (Venue) -> Unit,
    val setPlaceText: (String) -> Unit,
    /** At a place with several parts: which one this climb was at. */
    val selectSection: (Long) -> Unit = {},
)

/** The part of [this] place the draft is at: the picked section, else the first. */
private fun Place.sectionFor(draft: LogClimbDraft) = sections.firstOrNull { it.id == draft.sectionId } ?: sections.firstOrNull()

/** The kind of climbing the draft is at, within [this] place. */
private fun Place.typeFor(draft: LogClimbDraft): PlaceType = sectionFor(draft)?.type ?: types.firstOrNull { it.venue == draft.venue } ?: type

private enum class WhereStep { PLACE, AREA }

/**
 * Where the climb was, as one read-only line ("Block Lab · Kilter · Benchmarks · 40°"), with
 * [trailing] (the day button) beside it. Tapping the line opens a sheet that walks through the
 * place, then its facility and wall; any step can be skipped or the sheet closed early.
 */
@Composable
fun WhereSection(draft: LogClimbDraft, places: List<PlaceSummary>, detail: PlaceDetail?, actions: WhereActions, trailing: @Composable () -> Unit = {}) {
    var open by rememberSaveable { mutableStateOf(false) }
    val place = detail?.place?.takeIf { it.id == draft.placeId }
    val area = place?.let { detail.areas.firstOrNull { it.id == draft.areaId } }

    val summary = listOfNotNull(
        place?.name ?: draft.place.takeIf { it.isNotBlank() },
        place?.takeIf { it.hasSeveralTypes }?.sectionFor(draft)?.name,
        area?.name,
        draft.angle?.takeIf { place != null && draft.venue == Venue.BOARD }?.let { "$it°" },
    ).joinToString(" · ")

    // Favourite places as one-tap picks; tapping the picked one again clears it.
    val favourites = places.filter { it.place.favourite }
    if (favourites.isNotEmpty()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
            modifier = Modifier
                .padding(bottom = CruxTheme.space.s2)
                .testTag("favourite_places"),
        ) {
            items(favourites, key = { it.place.id }) { summary ->
                val picked = draft.placeId == summary.place.id
                CruxFilterChip(
                    label = summary.place.name,
                    selected = picked,
                    onClick = { actions.selectPlace(if (picked) null else summary.place.id) },
                    modifier = Modifier.testTag("favourite_${summary.place.name}"),
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), modifier = Modifier.height(IntrinsicSize.Min)) {
        Box(Modifier.weight(1f)) {
            WhereRow(
                summary = summary.ifBlank { null },
                kind = place?.typeFor(draft)?.label ?: draft.venue.label,
                icon = place?.let { placeIcon(it.typeFor(draft)) } ?: Icons.Rounded.EditLocationAlt,
                onClick = { open = true },
            )
        }
        trailing()
    }
    if (open) {
        WhereSheet(draft, places, detail, actions, onClose = { open = false })
    }
}

@Composable
private fun WhereRow(summary: String?, kind: String, icon: ImageVector, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(colors.surfaceContainerLow, shape)
            .border(CruxTheme.size.borderHairline, colors.outlineVariant, shape)
            .clickable(role = Role.Button, onClickLabel = "Change where", onClick = onClick)
            .padding(horizontal = CruxTheme.space.s3, vertical = CruxTheme.space.s2)
            .testTag("where_summary"),
    ) {
        Icon(icon, contentDescription = null, tint = if (summary != null) colors.primary else colors.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(
                summary ?: "Where?",
                style = MaterialTheme.typography.titleMedium,
                color = if (summary != null) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (summary != null) kind else "$kind · optional: place, facility and wall",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Text(if (summary != null) "Change" else "Add", style = MaterialTheme.typography.labelLarge, color = colors.primary)
    }
}

@Composable
private fun WhereSheet(draft: LogClimbDraft, places: List<PlaceSummary>, detail: PlaceDetail?, actions: WhereActions, onClose: () -> Unit) {
    val place = detail?.place?.takeIf { it.id == draft.placeId }
    var step by rememberSaveable { mutableStateOf(WhereStep.PLACE) }
    var creatingPlace by rememberSaveable { mutableStateOf(false) }
    // With no place the later step has nothing to show.
    val current = if (place == null) WhereStep.PLACE else step
    // After the wall, the sheet has done its job.
    val next = {
        if (current == WhereStep.PLACE && (detail?.areas?.isNotEmpty() == true || place?.hasSeveralTypes == true)) step = WhereStep.AREA else onClose()
    }
    val stepLabel = { s: WhereStep ->
        when (s) {
            WhereStep.PLACE -> "Place"
            WhereStep.AREA -> place?.let { if (it.hasSeveralTypes) "Area" else it.type.areaLabel } ?: "Area"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.testTag("where_sheet"),
    ) {
        Column(Modifier.padding(horizontal = CruxTheme.space.s4)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Where", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                CruxButton("Done", onClose, size = CruxButtonSize.Small, modifier = Modifier.testTag("where_done"))
            }
            // The steps: tap one to go back to it. Later steps wait for a place.
            Row(
                horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s1),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = CruxTheme.space.s3),
            ) {
                WhereStep.entries.forEachIndexed { index, s ->
                    if (index > 0) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    val enabled = s == WhereStep.PLACE || place != null
                    Text(
                        stepLabel(s),
                        style = MaterialTheme.typography.labelLarge,
                        color = when {
                            s == current -> MaterialTheme.colorScheme.primary
                            enabled -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(enabled = enabled) { step = s }
                            .padding(CruxTheme.space.s1)
                            .testTag("where_step_${s.name}"),
                    )
                }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
                contentPadding = PaddingValues(bottom = CruxTheme.space.s8),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                // A place with several kinds: pick which part of it first; walls and problems follow.
                if (place != null && place.hasSeveralTypes && current != WhereStep.PLACE) {
                    item(key = "place_type") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
                            modifier = Modifier.padding(bottom = CruxTheme.space.s2).testTag("where_place_type"),
                        ) {
                            place.sections.forEach { section ->
                                CruxFilterChip(section.name, place.sectionFor(draft)?.id == section.id, {
                                    actions.selectSection(section.id)
                                }, Modifier.testTag("where_section_${section.name}"))
                            }
                        }
                    }
                }
                when (current) {
                    // The new place's walls may not have loaded yet, so decide from its summary.
                    WhereStep.PLACE -> placeStep(draft, places, actions, onPicked = { picked ->
                        if (picked.walls > 0 || picked.place.hasSeveralTypes) step = WhereStep.AREA else onClose()
                    }, onNew = { creatingPlace = true })

                    WhereStep.AREA -> if (detail != null) areaStep(draft, detail, actions, onPicked = onClose)
                }
                if (place == null) {
                    item(key = "venue") {
                        Column(Modifier.padding(top = CruxTheme.space.s3), verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
                            Eyebrow("No saved place · what kind of climbing?")
                            CruxSegmentedButtons(
                                options = listOf(Venue.GYM, Venue.CRAG, Venue.BOARD),
                                selected = draft.venue,
                                label = { it.label },
                                onSelect = actions.setVenue,
                                modifier = Modifier.testTag("venue"),
                            )
                        }
                    }
                }
                if (place != null && draft.venue == Venue.BOARD) {
                    item(key = "angle") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = CruxTheme.space.s3)) {
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
                if (place != null) {
                    item(key = "skip") {
                        CruxButton("Skip", next, variant = CruxButtonVariant.Text, modifier = Modifier.testTag("where_skip"))
                    }
                }
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
}

private fun LazyListScope.placeStep(
    draft: LogClimbDraft,
    places: List<PlaceSummary>,
    actions: WhereActions,
    onPicked: (PlaceSummary) -> Unit,
    onNew: () -> Unit,
) {
    item(key = "place_none") {
        PickRow("No saved place", selected = draft.placeId == null, tag = "place_none") { actions.selectPlace(null) }
    }
    items(places, key = { "place_${it.place.id}" }) { summary ->
        PickRow(
            title = summary.place.name,
            supporting = listOfNotNull(summary.place.typesLabel, summary.place.location).joinToString(" · "),
            icon = placeIcon(summary.place.type),
            selected = draft.placeId == summary.place.id,
            tag = "place_${summary.place.name}",
        ) {
            if (draft.placeId != summary.place.id) actions.selectPlace(summary.place.id)
            onPicked(summary)
        }
    }
    item(key = "place_new") {
        PickRow("New place", icon = Icons.Rounded.Add, selected = false, tag = "new_place_chip", onClick = onNew)
    }
}

private fun LazyListScope.areaStep(draft: LogClimbDraft, detail: PlaceDetail, actions: WhereActions, onPicked: () -> Unit) {
    val type = detail.place.typeFor(draft)
    val section = detail.place.sectionFor(draft)
    item(key = "area_any") {
        PickRow("Any ${type.areaLabel.lowercase()}", selected = draft.areaId == null, tag = "area_any") {
            actions.selectArea(null)
            onPicked()
        }
    }
    items(detail.areas.filter { section == null || detail.place.sectionOf(it)?.id == section.id }, key = { "area_${it.id}" }) { area ->
        PickRow(
            title = area.name,
            supporting = area.angle?.let { "$it°" },
            selected = draft.areaId == area.id,
            tag = "area_${area.name}",
        ) {
            actions.selectArea(area.id)
            onPicked()
        }
    }
}

@Composable
private fun PickRow(title: String, selected: Boolean, tag: String, supporting: String? = null, icon: ImageVector? = null, onClick: () -> Unit) {
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

/** `Cave · 12 goes · sent 3 Oct`, or `project · 8 goes`. */
internal fun problemLine(item: ProblemWithStats, detail: PlaceDetail?): String {
    val area = item.problem.areaId?.let { id -> detail?.areas?.firstOrNull { it.id == id }?.name }
    val stats = item.stats
    val progress = when {
        stats == null -> "no goes yet"
        stats.sent -> "${goes(stats.attempts)} · sent ${stats.firstSend!!.shortLabel()}"
        else -> "project · ${goes(stats.attempts)}"
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
                    placeholder = if (type == PlaceType.CRAG) {
                        "Arco"
                    } else if (type == PlaceType.BOARD) {
                        "Home Kilter"
                    } else {
                        "Block Lab"
                    },
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

/** A small dot in the climb's tape colour. */
@Composable
fun TapeDot(tape: Int, modifier: Modifier = Modifier) {
    val colors = CruxTheme.colors.tape
    Box(
        modifier
            .size(10.dp)
            .background(colors[tape.coerceIn(colors.indices)], CircleShape),
    )
}

/** "1 go", "4 goes". */
private fun goes(n: Int) = if (n == 1) "1 go" else "$n goes"
