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
import androidx.compose.foundation.layout.width
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
    val setVenue: (Venue) -> Unit,
    val setPlaceText: (String) -> Unit,
    /** At a place with several parts: which one this climb was at. */
    val selectSection: (Long) -> Unit = {},
    /** The Climb step: one of your climbs, a dataset's climb, or a new climb to type. */
    val pickProblem: (Problem) -> Unit = {},
    val pickDatasetClimb: (com.hardtekpt.crux.data.ClimbSource) -> Unit = {},
    val newClimb: () -> Unit = {},
    /** Whether a section uses any dataset, so picking it leads on to the Climb step. */
    val usesDatasets: (com.hardtekpt.crux.data.model.Section) -> Boolean = { false },
    /** The sheet opened by itself, for Log this climb. */
    val whereOpened: () -> Unit = {},
    /** A board catalogue's climbs whose name or setter has what's typed, at the draft's angle. */
    val searchCatalogue: suspend (com.hardtekpt.crux.data.Dataset, String) -> List<com.hardtekpt.crux.data.ClimbSource> = { _, _ -> emptyList() },
)

/** The part of [this] place the draft is at: the picked section, else the first. */
private fun Place.sectionFor(draft: LogClimbDraft) = sections.firstOrNull { it.id == draft.sectionId } ?: sections.firstOrNull()

/** The kind of climbing the draft is at, within [this] place. */
private fun Place.typeFor(draft: LogClimbDraft): PlaceType = sectionFor(draft)?.type ?: types.firstOrNull { it.venue == draft.venue } ?: type

private enum class WhereStep { PLACE, AREA, CLIMB }

/**
 * Where the climb was, as one read-only line ("Block Lab · Kilter · Benchmarks · 40°"), with
 * [trailing] (the day button) beside it. Tapping the line opens a sheet that walks through the
 * place, then its facility and wall; any step can be skipped or the sheet closed early.
 */
@Composable
fun WhereSection(
    draft: LogClimbDraft,
    places: List<PlaceSummary>,
    detail: PlaceDetail?,
    actions: WhereActions,
    trailing: @Composable () -> Unit = {},
    choices: ClimbChoices? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    // Log this climb, from a dataset used at several sections: the sheet opens on them.
    androidx.compose.runtime.LaunchedEffect(draft.openWhere) {
        if (draft.openWhere) {
            open = true
            actions.whereOpened()
        }
    }
    val place = detail?.place?.takeIf { it.id == draft.placeId }
    val area = place?.let { detail.areas.firstOrNull { it.id == draft.areaId } }

    val summary = listOfNotNull(
        place?.name ?: draft.place.takeIf { it.isNotBlank() },
        place?.takeIf { it.hasSeveralTypes }?.sectionFor(draft)?.name,
        area?.name,
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
                kind = place?.sectionFor(draft)?.boardLabel?.let { "Board · $it" } ?: place?.typeFor(draft)?.label ?: draft.venue.label,
                icon = place?.let { placeIcon(it.typeFor(draft)) } ?: Icons.Rounded.EditLocationAlt,
                onClick = { open = true },
            )
        }
        trailing()
    }
    if (open) {
        WhereSheet(draft, places, detail, actions, choices, onClose = { open = false })
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
private fun WhereSheet(
    draft: LogClimbDraft,
    places: List<PlaceSummary>,
    detail: PlaceDetail?,
    actions: WhereActions,
    choices: ClimbChoices?,
    onClose: () -> Unit,
) {
    val place = detail?.place?.takeIf { it.id == draft.placeId }
    var step by rememberSaveable { mutableStateOf(WhereStep.PLACE) }
    var creatingPlace by rememberSaveable { mutableStateOf(false) }
    var climbTab by rememberSaveable { mutableStateOf(YOURS) }
    var query by rememberSaveable { mutableStateOf("") }
    // With no place the later steps have nothing to show; Climb only where the section has datasets.
    val current = when {
        place == null -> WhereStep.PLACE
        step == WhereStep.CLIMB && choices == null -> WhereStep.AREA
        else -> step
    }
    val hasArea = detail?.areas?.isNotEmpty() == true || place?.hasSeveralTypes == true
    // After the wall (or the climb, where there are datasets), the sheet has done its job.
    val next = {
        when {
            current == WhereStep.PLACE && hasArea -> step = WhereStep.AREA
            current != WhereStep.CLIMB && choices != null -> step = WhereStep.CLIMB
            else -> onClose()
        }
    }
    val steps = WhereStep.entries.filter { it != WhereStep.CLIMB || choices != null }
    val stepLabel = { s: WhereStep ->
        when (s) {
            WhereStep.PLACE -> "Place"
            WhereStep.AREA -> place?.let { if (it.hasSeveralTypes) "Area" else it.type.areaLabel } ?: "Area"
            WhereStep.CLIMB -> "Climb"
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
                steps.forEachIndexed { index, s ->
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
                            place.sections.filter { draft.whereLimit == null || it.id in draft.whereLimit }.forEach { section ->
                                CruxFilterChip(section.name, place.sectionFor(draft)?.id == section.id, {
                                    actions.selectSection(section.id)
                                }, Modifier.testTag("where_section_${section.name}"))
                            }
                        }
                    }
                }
                when (current) {
                    // The new place's walls may not have loaded yet, so decide from its summary.
                    WhereStep.PLACE -> placeStep(
                        draft,
                        places.filter { summary -> draft.whereLimit == null || summary.place.sections.any { it.id in draft.whereLimit } },
                        actions,
                        onPicked = { picked ->
                            when {
                                picked.walls > 0 || picked.place.hasSeveralTypes -> step = WhereStep.AREA
                                picked.place.sections.firstOrNull()?.let(actions.usesDatasets) == true -> step = WhereStep.CLIMB
                                else -> onClose()
                            }
                        },
                        onNew = { creatingPlace = true },
                    )

                    WhereStep.AREA -> if (detail != null) {
                        areaStep(draft, detail, actions, onPicked = { area ->
                            val section = area?.let { detail.place.sectionOf(it) } ?: detail.place.section(draft.sectionId)
                            if (section != null && actions.usesDatasets(section)) step = WhereStep.CLIMB else onClose()
                        })
                    }

                    WhereStep.CLIMB -> if (choices != null) {
                        climbStep(draft, detail, choices, climbTab, { climbTab = it }, query, { query = it }, actions, onClose)
                    }
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

private fun LazyListScope.areaStep(draft: LogClimbDraft, detail: PlaceDetail, actions: WhereActions, onPicked: (com.hardtekpt.crux.data.model.Area?) -> Unit) {
    val type = detail.place.typeFor(draft)
    val section = detail.place.sectionFor(draft)
    item(key = "area_any") {
        PickRow("Any ${type.areaLabel.lowercase()}", selected = draft.areaId == null, tag = "area_any") {
            actions.selectArea(null)
            onPicked(null)
        }
    }
    items(detail.areas.filter { section == null || detail.place.sectionOf(it)?.id == section.id }, key = { "area_${it.id}" }) { area ->
        PickRow(
            title = area.name,
            selected = draft.areaId == area.id,
            tag = "area_${area.name}",
        ) {
            actions.selectArea(area.id)
            onPicked(area)
        }
    }
}

private const val YOURS = "yours"

/**
 * The Climb step: your climbs at the section first, then a chip per playlist and dataset, with a
 * search over the chip shown. Rows show the grade in the section's scale and how you've done on
 * the climb here. Picking fills the form; New climb leaves the name to type.
 */
private fun LazyListScope.climbStep(
    draft: LogClimbDraft,
    detail: PlaceDetail?,
    choices: ClimbChoices,
    tab: String,
    onTab: (String) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    actions: WhereActions,
    onClose: () -> Unit,
) {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    fun matches(vararg text: String?) = words.all { word -> text.any { it?.lowercase()?.contains(word) == true } }
    item(key = "climb_search") {
        CruxTextField(
            label = "",
            value = query,
            onValueChange = { onQuery(it.take(60)) },
            placeholder = "Search climbs",
            modifier = Modifier.fillMaxWidth().testTag("climb_search"),
        )
    }
    item(key = "climb_tabs") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), modifier = Modifier.testTag("climb_tabs")) {
            item { CruxFilterChip("Your climbs", tab == YOURS, { onTab(YOURS) }, Modifier.testTag("climb_tab_yours")) }
            items(choices.playlists, key = { "p${it.id}" }) { list ->
                CruxFilterChip(list.name, tab == "p${list.id}", { onTab("p${list.id}") }, Modifier.testTag("climb_tab_${list.name}"))
            }
            items(choices.datasets, key = { "d${it.id}" }) { dataset ->
                CruxFilterChip(dataset.name, tab == "d${dataset.id}", { onTab("d${dataset.id}") }, Modifier.testTag("climb_tab_${dataset.name}"))
            }
        }
    }
    val pick = { source: com.hardtekpt.crux.data.ClimbSource ->
        actions.pickDatasetClimb(source)
        onClose()
    }
    val dataset = choices.datasets.firstOrNull { "d${it.id}" == tab }
    val playlist = choices.playlists.firstOrNull { "p${it.id}" == tab }
    when {
        dataset != null && dataset.isCatalogue -> item(key = "catalogue_${dataset.id}") {
            // A board catalogue is searched, most sent first, rather than listed whole.
            val found by androidx.compose.runtime.produceState<List<com.hardtekpt.crux.data.ClimbSource>?>(null, dataset.id, query) {
                value = actions.searchCatalogue(dataset, query)
            }
            Column(verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
                found?.forEach { source -> DatasetClimbRow(source, choices, showDataset = false) { pick(it) } }
                if (found?.isEmpty() == true) NothingRow(false)
            }
        }

        dataset != null -> {
            val climbs = com.hardtekpt.crux.ui.datasets.sorted(dataset.climbs, com.hardtekpt.crux.ui.datasets.ClimbSort.GRADE)
                .filter { matches(it.name, it.setter, it.note) }
            items(climbs, key = { "dc${it.id}" }) { climb ->
                DatasetClimbRow(com.hardtekpt.crux.data.ClimbSource(climb, dataset), choices, showDataset = false) { pick(it) }
            }
            if (climbs.isEmpty()) item(key = "climb_none") { NothingRow(dataset.climbs.isEmpty()) }
        }

        playlist != null -> {
            val sources = playlist.items.mapNotNull { item -> item.climb?.let { c -> item.dataset?.let { d -> com.hardtekpt.crux.data.ClimbSource(c, d) } } }
                .filter { matches(it.climb.name, it.climb.setter, it.dataset.name) }
            items(sources, key = { "pc${it.climb.catalogueUuid ?: it.climb.id}" }) { source ->
                DatasetClimbRow(source, choices, showDataset = true) { pick(it) }
            }
            if (sources.isEmpty()) item(key = "climb_none") { NothingRow(false) }
        }

        else -> {
            val yours = choices.yours.filter { matches(it.problem.name, it.problem.setter) }
                .sortedWith(compareByDescending<ProblemWithStats> { it.stats?.lastGo }.thenBy { it.problem.name.lowercase() })
            items(yours, key = { "yc${it.problem.id}" }) { item ->
                CruxListRow(
                    title = item.problem.name,
                    supporting = problemLine(item, detail),
                    leading = { GradeText(item.problem.grade) },
                    trailing = { StatusDot(item.stats?.sent, item.stats != null) },
                    selected = draft.problemId == item.problem.id,
                    onClick = {
                        actions.pickProblem(item.problem)
                        onClose()
                    },
                    modifier = Modifier.testTag("climb_yours_${item.problem.name}"),
                )
            }
            if (yours.isEmpty()) item(key = "climb_none") { NothingRow(choices.yours.isEmpty(), yours = true) }
        }
    }
    if (!draft.isEditing) {
        item(key = "climb_new") {
            PickRow("New climb · type its name in the form", selected = false, tag = "climb_new", icon = Icons.Rounded.Add) {
                actions.newClimb()
                onClose()
            }
        }
    }
}

/** A dataset's climb in the Climb step: its grade in the section's scale, its setter and how you've done on it here. */
@Composable
private fun DatasetClimbRow(
    source: com.hardtekpt.crux.data.ClimbSource,
    choices: ClimbChoices,
    showDataset: Boolean,
    onPick: (com.hardtekpt.crux.data.ClimbSource) -> Unit,
) {
    val climb = source.climb
    // Yours here: linked to it, else of its name and type.
    val yours = choices.yours.firstOrNull {
        if (climb.catalogueUuid != null) it.problem.sourceCatalogueUuid == climb.catalogueUuid else it.problem.sourceDatasetClimbId == climb.id
    }
        ?: choices.yours.firstOrNull { it.problem.discipline == climb.discipline && it.problem.name.trim().equals(climb.name.trim(), ignoreCase = true) }
    val grade = climb.gradeScale?.let { scale ->
        climb.gradeIndex?.let { index -> com.hardtekpt.crux.data.convertGrade(scale, index, choices.system.scale)?.let(choices.system::label) }
    } ?: climb.gradeLabel ?: "–"
    val stats = yours?.stats
    val status = when {
        yours == null || stats == null -> "never logged"
        stats.sent -> "sent ${stats.firstSend!!.shortLabel()}"
        else -> "logged here, project"
    }
    CruxListRow(
        title = climb.name,
        supporting = listOfNotNull(source.dataset.name.takeIf { showDataset }, climb.setter, status).joinToString(" · "),
        leading = { GradeText(grade) },
        trailing = { StatusDot(stats?.sent, stats != null) },
        onClick = { onPick(source) },
        modifier = Modifier.testTag("climb_pick_${climb.name}"),
    )
}

@Composable
private fun GradeText(grade: String) {
    Text(grade, style = CruxTheme.type.grade, maxLines = 1, modifier = Modifier.width(44.dp))
}

/** Green once sent, amber while a project; nothing before a go. */
@Composable
private fun StatusDot(sent: Boolean?, tried: Boolean) {
    if (!tried) return
    Box(
        Modifier
            .size(8.dp)
            .background(if (sent == true) CruxTheme.colors.success else MaterialTheme.colorScheme.secondary, CircleShape),
    )
}

@Composable
private fun NothingRow(empty: Boolean, yours: Boolean = false) {
    Text(
        when {
            yours && empty -> "No climbs logged here yet. Pick one from a dataset, or start a new one."
            empty -> "This dataset has no climbs yet."
            else -> "Nothing matches."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = CruxTheme.space.s2),
    )
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
