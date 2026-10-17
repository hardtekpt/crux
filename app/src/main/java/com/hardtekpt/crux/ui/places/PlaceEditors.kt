package com.hardtekpt.crux.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.PlaceInput
import com.hardtekpt.crux.data.PlaceRepository
import com.hardtekpt.crux.data.ProblemInput
import com.hardtekpt.crux.data.SectionInput
import com.hardtekpt.crux.data.model.Area
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.GradeSystem
import com.hardtekpt.crux.data.model.LocalGrade
import com.hardtekpt.crux.data.model.LocalKind
import com.hardtekpt.crux.data.model.LocalScale
import com.hardtekpt.crux.data.model.MapLocation
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.prefs.GradeScales
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.CruxCardFill
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxSegmentedButtons
import com.hardtekpt.crux.ui.components.CruxSheet
import com.hardtekpt.crux.ui.components.CruxStepper
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.SheetButton
import com.hardtekpt.crux.ui.components.input.GradeStrip
import com.hardtekpt.crux.ui.components.input.TapeSwatch
import com.hardtekpt.crux.ui.components.input.argb
import com.hardtekpt.crux.ui.components.input.bleed
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MAX_NAME = 40

// ---- Place editor --------------------------------------------------------------------

/** One part of the place: its kind as chips, a name, and a remove button when there are others. */
@Composable
private fun SectionEditor(
    section: SectionDraft,
    index: Int,
    removable: Boolean,
    onChange: ((SectionDraft) -> SectionDraft) -> Unit,
    onRemove: () -> Unit,
    datasets: List<com.hardtekpt.crux.data.Dataset> = emptyList(),
    onNewDataset: ((com.hardtekpt.crux.data.Dataset) -> Unit)? = null,
    lights: LightsActions? = null,
) {
    val space = CruxTheme.space
    CruxCard(fill = CruxCardFill.Low, modifier = Modifier.testTag("section_$index")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.weight(1f)) {
                PlaceType.entries.forEach { t ->
                    CruxFilterChip(
                        label = t.label,
                        selected = section.type == t,
                        onClick = { onChange { it.copy(type = t) } },
                        modifier = Modifier.testTag("section_${index}_${t.name}"),
                    )
                }
            }
            if (removable) {
                IconButton(onClick = onRemove, modifier = Modifier.testTag("remove_section_$index")) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove this part")
                }
            }
        }
        CruxTextField(
            label = "",
            value = section.name,
            onValueChange = { v -> onChange { it.copy(name = v.take(40)) } },
            placeholder = when (section.type) {
                PlaceType.GYM -> "Main gym"
                PlaceType.CRAG -> "Main crag"
                PlaceType.BOARD -> "Moonboard"
            },
            helper = "Name, optional: \"${section.type.label}\" if left blank",
            modifier = Modifier.testTag("section_name_$index"),
        )
        // Which board it is, and for Kilter its size (and a Homewall's holds).
        if (section.type == PlaceType.BOARD) BoardChoice(section, index, onChange)
        // The grades climbs here use; boards hold boulders only.
        val tag = if (index == 0) "" else "_$index"
        ScaleChoice("Boulder grades", Discipline.BOULDER, section.boulderScale, tag) { scale -> onChange { it.copy(boulderScale = scale) } }
        if (section.type != PlaceType.BOARD) {
            ScaleChoice("Route grades", Discipline.ROUTE, section.routeScale, tag) { scale -> onChange { it.copy(routeScale = scale) } }
        }
        // The lists of climbs Log climb offers here: those for its board, and any linked by hand.
        if (onNewDataset != null) DatasetsChoice(section, index, datasets, onChange, onNewDataset)
        // A Kilter or MoonBoard's lights, paired on this phone.
        if (lights != null && section.type == PlaceType.BOARD && section.board != null) LightsChoice(section, index, lights)
    }
}

/** A section as the place it will be, for telling which datasets are made for its board. */
internal fun SectionDraft.asSection() = com.hardtekpt.crux.data.model.Section(
    id = id,
    placeId = 0,
    type = type,
    name = name,
    board = board,
    boardSize = boardSize,
    boardHolds = boardHolds,
    lights = lights,
)

/** The Datasets row: "None", "Kilter warm-ups" or "2 datasets"; it opens the checkbox sheet. */
@Composable
private fun DatasetsChoice(
    section: SectionDraft,
    index: Int,
    datasets: List<com.hardtekpt.crux.data.Dataset>,
    onChange: ((SectionDraft) -> SectionDraft) -> Unit,
    onNewDataset: (com.hardtekpt.crux.data.Dataset) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val used = com.hardtekpt.crux.data.datasetsFor(section.asSection(), datasets, section.datasetIds.orEmpty())
    ChoiceRow(
        "Datasets",
        when (used.size) {
            0 -> "None"
            1 -> used.single().name
            else -> "${used.size} datasets"
        },
        "section_datasets_$index",
    ) { open = true }
    if (open) {
        DatasetLinksSheet(
            section = section,
            datasets = datasets,
            onToggle = { id, on -> onChange { it.copy(datasetIds = it.datasetIds.orEmpty().let { ids -> if (on) ids + id else ids - id }) } },
            onNew = onNewDataset,
            onDismiss = { open = false },
        )
    }
}

/**
 * Every dataset with a checkbox: ticked ones are offered when you log here. Those made for this
 * section's board are ticked and locked ("Same board"); the rest are linked by hand.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DatasetLinksSheet(
    section: SectionDraft,
    datasets: List<com.hardtekpt.crux.data.Dataset>,
    onToggle: (Long, Boolean) -> Unit,
    onNew: (com.hardtekpt.crux.data.Dataset) -> Unit,
    onDismiss: () -> Unit,
) {
    val asSection = section.asSection()
    CruxSheet(
        onDismiss = onDismiss,
        modifier = Modifier.testTag("dataset_links_sheet"),
        title = "Datasets · ${section.name.ifBlank { section.type.label }}",
        action = SheetButton("Done", onDismiss, tag = "dataset_links_done"),
    ) {
        Text(
            "Ticked datasets are offered when you log a climb here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (datasets.isEmpty()) {
            Text("You have no datasets yet.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = CruxTheme.space.s2))
        }
        datasets.forEach { dataset ->
            val auto = dataset.fitsBoard(asSection)
            val ticked = auto || dataset.id in section.datasetIds.orEmpty()
            CruxListRow(
                title = dataset.name,
                supporting = listOfNotNull(
                    "Same board".takeIf { auto },
                    dataset.boardLabel,
                    "${dataset.climbs.size} ${if (dataset.climbs.size == 1) "climb" else "climbs"}",
                ).joinToString(" · "),
                leading = {
                    androidx.compose.material3.Checkbox(
                        checked = ticked,
                        onCheckedChange = null,
                        enabled = !auto,
                    )
                },
                onClick = if (auto) null else ({ onToggle(dataset.id, !ticked) }),
                modifier = Modifier
                    .semantics { toggleableState = androidx.compose.ui.state.ToggleableState(ticked) }
                    .testTag("dataset_link_${dataset.name}"),
            )
        }
        CruxListRow(
            title = "New dataset for this ${if (section.type == PlaceType.BOARD) "board" else section.type.label.lowercase()}",
            leading = { Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            onClick = {
                onNew(
                    com.hardtekpt.crux.data.Dataset(
                        name = section.name.ifBlank { section.type.label },
                        board = section.board.takeIf { section.type == PlaceType.BOARD },
                        customBoard = section.type == PlaceType.BOARD && section.board == null,
                        boardSize = section.boardSize.takeIf { section.type == PlaceType.BOARD },
                        boardHolds = section.boardHolds.takeIf { section.type == PlaceType.BOARD },
                        boulderScale = section.boulderScale?.takeIf { !it.isLocal },
                        routeScale = section.routeScale?.takeIf { !it.isLocal && section.type != PlaceType.BOARD },
                    ),
                )
            },
            modifier = Modifier.testTag("dataset_link_new"),
        )
    }
}

data class PlaceDraft(
    val id: Long = 0,
    val name: String = "",
    /** The named parts of the place, in order: a kind and a name each. At least one. */
    val sections: List<SectionDraft> = listOf(SectionDraft(type = PlaceType.GYM)),
    val location: String = "",
    val notes: String = "",
    val favourite: Boolean = false,
    val mapLocation: MapLocation? = null,
    val nameError: String? = null,
    val confirmDelete: Boolean = false,
    /** Set once saved or deleted: the id to open, or 0 after a delete. */
    val doneId: Long? = null,
) {
    val isNew: Boolean get() = id == 0L
    val types: List<PlaceType> get() = sections.map { it.type }.distinct()
    val type: PlaceType get() = types.first()
    val hasBoard: Boolean get() = PlaceType.BOARD in types

    /** Only a board: no routes, and "where it is" rather than a city. */
    val onlyBoard: Boolean get() = types == listOf(PlaceType.BOARD)

    fun addSection(type: PlaceType) = copy(sections = sections + SectionDraft(type = type, key = (sections.maxOfOrNull { it.key } ?: 0) + 1))
    fun updateSection(key: Int, change: (SectionDraft) -> SectionDraft) = copy(sections = sections.map { if (it.key == key) change(it) else it })

    /** The last section can't be removed. */
    fun removeSection(key: Int) = if (sections.size > 1) copy(sections = sections.filterNot { it.key == key }) else this
}

/** One section in the place form. [key] tells rows apart before they have an id. */
data class SectionDraft(
    val id: Long = 0,
    val type: PlaceType,
    val name: String = "",
    val key: Int = 0,
    /** Null = use my settings. */
    val boulderScale: GradeScale? = null,
    val routeScale: GradeScale? = null,
    /** This part's own grades; kept while editing even if no discipline uses them. */
    val localScale: LocalScale = LocalScale.DEFAULT_COLOURS,
    val localError: String? = null,
    /** Board sections: which board (null = custom), its Kilter size and Homewall holds. */
    val board: com.hardtekpt.crux.data.model.BoardModel? = null,
    val boardSize: String? = null,
    val boardHolds: com.hardtekpt.crux.data.model.BoardHolds? = null,
    val boardError: String? = null,
    /** Datasets linked by hand; null until the place's links have loaded. */
    val datasetIds: Set<Long>? = null,
    /** The board's lights paired on this phone; saved as soon as they're paired, not with the place. */
    val lights: com.hardtekpt.crux.data.model.PairedLights? = null,
) {
    /** Boards hold boulders only. */
    val usesLocal: Boolean get() = boulderScale?.isLocal == true || (type != PlaceType.BOARD && routeScale?.isLocal == true)
}

@HiltViewModel
class PlaceEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PlaceRepository,
    private val datasetRepository: com.hardtekpt.crux.data.DatasetRepository? = null,
    private val link: com.hardtekpt.crux.data.BoardLink? = null,
) : ViewModel() {
    /** What the board's lights are doing, for the Lights sheet. */
    val lightsState: StateFlow<com.hardtekpt.crux.data.BoardLink.State> =
        link?.state ?: MutableStateFlow(com.hardtekpt.crux.data.BoardLink.State.Off)

    /** Pairs a board section's lights; a Kilter's protocol comes from its name, a MoonBoard's is the text one. */
    fun pairLights(sectionKey: Int, found: com.hardtekpt.crux.ui.components.FoundBoard) {
        val section = _draft.value.sections.firstOrNull { it.key == sectionKey } ?: return
        if (section.id == 0L) return
        val kilter = section.board?.maker == "Kilter"
        val lights = com.hardtekpt.crux.data.model.PairedLights(
            address = found.address,
            name = found.name,
            protocol = if (kilter) com.hardtekpt.crux.data.BoardLights.auroraVersion(found.name) else com.hardtekpt.crux.data.BoardLights.MOONBOARD,
        )
        saveLights(section, lights)
    }

    fun forgetLights(sectionKey: Int) {
        val section = _draft.value.sections.firstOrNull { it.key == sectionKey } ?: return
        viewModelScope.launch { link?.clear(section.asSection()) }
        saveLights(section, null)
    }

    /** The MoonBoard check: which corner LED 0 lit, so its LED order is known. */
    fun setMoonOrder(sectionKey: Int, order: com.hardtekpt.crux.data.BoardLights.MoonOrder) {
        val section = _draft.value.sections.firstOrNull { it.key == sectionKey } ?: return
        val lights = section.lights ?: return
        saveLights(section, lights.copy(order = order.ordinal))
        viewModelScope.launch { link?.clear(section.asSection()) }
    }

    fun testLights(sectionKey: Int) {
        val section = _draft.value.sections.firstOrNull { it.key == sectionKey } ?: return
        viewModelScope.launch { link?.test(section.asSection()) }
    }

    fun lightFirst(sectionKey: Int) {
        val section = _draft.value.sections.firstOrNull { it.key == sectionKey } ?: return
        viewModelScope.launch { link?.lightFirst(section.asSection(), com.hardtekpt.crux.data.BoardLights.MoonOrder.FROM_A1) }
    }

    fun lightsOff(sectionKey: Int) {
        val section = _draft.value.sections.firstOrNull { it.key == sectionKey } ?: return
        viewModelScope.launch { link?.clear(section.asSection()) }
    }

    private fun saveLights(section: SectionDraft, lights: com.hardtekpt.crux.data.model.PairedLights?) {
        _draft.update { d -> d.updateSection(section.key) { it.copy(lights = lights) } }
        viewModelScope.launch { repository.setLights(section.id, lights) }
    }
    private val placeId: Long = savedStateHandle.get<Long>("placeId") ?: 0L
    private val _draft = MutableStateFlow(PlaceDraft(id = placeId))
    val draft: StateFlow<PlaceDraft> = _draft.asStateFlow()

    /** Every dataset, for the Datasets rows; empty where datasets aren't offered. */
    val datasets: StateFlow<List<com.hardtekpt.crux.data.Dataset>> = (datasetRepository?.observeDatasets() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Makes a dataset from a section's sheet; one not made for its board is linked to it. */
    fun createDataset(sectionKey: Int, dataset: com.hardtekpt.crux.data.Dataset) {
        val repo = datasetRepository ?: return
        viewModelScope.launch {
            val id = repo.saveDataset(dataset.copy(id = 0))
            update { d ->
                d.updateSection(sectionKey) { s -> if (dataset.fitsBoard(s.asSection())) s else s.copy(datasetIds = s.datasetIds.orEmpty() + id) }
            }
        }
    }

    init {
        if (placeId != 0L) {
            viewModelScope.launch {
                val links = datasetRepository?.observeLinks()?.first().orEmpty()
                repository.getPlace(placeId)?.let { p ->
                    _draft.update {
                        it.copy(
                            name = p.name,
                            sections = p.sections.ifEmpty { p.types.map { t -> com.hardtekpt.crux.data.model.Section(0, p.id, t, t.label) } }
                                .mapIndexed { index, section ->
                                    SectionDraft(
                                        id = section.id,
                                        type = section.type,
                                        name = section.name,
                                        key = index,
                                        boulderScale = section.boulderScale,
                                        routeScale = section.routeScale,
                                        localScale = section.localScale ?: LocalScale.DEFAULT_COLOURS,
                                        board = section.board,
                                        boardSize = section.boardSize,
                                        boardHolds = section.boardHolds,
                                        datasetIds = links[section.id].orEmpty().takeIf { datasetRepository != null },
                                        lights = section.lights,
                                    )
                                },
                            location = p.location.orEmpty(),
                            notes = p.notes.orEmpty(),
                            favourite = p.favourite,
                            mapLocation = p.mapLocation,
                        )
                    }
                }
            }
        }
    }

    fun update(change: (PlaceDraft) -> PlaceDraft) = _draft.update { d ->
        change(d).let { it.copy(nameError = null, sections = it.sections.map { s -> s.copy(localError = null, boardError = null) }) }
    }

    fun save() {
        val d = _draft.value
        val error = when {
            d.name.isBlank() -> "Give the place a name"
            d.name.trim().length > MAX_NAME -> "Keep the name under $MAX_NAME characters"
            else -> null
        }
        // Each part with local grades needs a usable list.
        val sections = d.sections.map { section ->
            val local = section.localScale.copy(grades = section.localScale.grades.map { it.copy(name = it.name.trim()) })
            val localError = when {
                !section.usesLocal -> null
                local.grades.size < 2 -> "A local scale needs at least two grades"
                local.grades.any { it.name.isBlank() } -> "Name every grade"
                local.grades.map { it.name.lowercase() }.toSet().size < local.grades.size -> "Each grade needs a different name"
                else -> null
            }
            // A Kilter board needs its size; a Homewall its holds too.
            val boardError = when {
                section.type != PlaceType.BOARD -> null
                section.board?.needsSize == true && section.boardSize !in section.board.sizes -> "Pick a size"
                section.board?.hasHolds == true && section.boardHolds == null -> "Pick the holds"
                else -> null
            }
            section.copy(localScale = local, localError = localError, boardError = boardError)
        }
        if (error != null || sections.any { it.localError != null || it.boardError != null }) {
            _draft.update { it.copy(nameError = error, sections = sections) }
            return
        }
        viewModelScope.launch {
            val id = repository.savePlace(
                PlaceInput(
                    id = d.id,
                    name = d.name,
                    sections = sections.map {
                        SectionInput(
                            id = it.id,
                            type = it.type,
                            name = it.name,
                            boulderScale = it.boulderScale,
                            routeScale = it.routeScale,
                            localScale = it.localScale.takeIf { _ -> it.usesLocal },
                            board = it.board,
                            boardSize = it.boardSize,
                            boardHolds = it.boardHolds,
                            datasetIds = it.datasetIds,
                        )
                    },
                    location = d.location,
                    notes = d.notes,
                    favourite = d.favourite,
                    mapLocation = d.mapLocation,
                ),
            )
            _draft.update { it.copy(doneId = id) }
        }
    }

    fun requestDelete() = _draft.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = _draft.update { it.copy(confirmDelete = false) }
    fun confirmDelete() {
        viewModelScope.launch {
            repository.deletePlace(placeId)
            _draft.update { it.copy(confirmDelete = false, doneId = 0) }
        }
    }
}

/**
 * Create or edit a gym, crag or board. [onSaved] gets the place id, or 0 when it was deleted.
 */
@Composable
fun PlaceEditorScreen(onBack: () -> Unit, onSaved: (Long) -> Unit, viewModel: PlaceEditorViewModel = hiltViewModel()) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val datasets by viewModel.datasets.collectAsStateWithLifecycle()
    val lightsState by viewModel.lightsState.collectAsStateWithLifecycle()
    var newDatasetFor by remember { mutableStateOf<Pair<Int, com.hardtekpt.crux.data.Dataset>?>(null) }
    LaunchedEffect(draft.doneId) { draft.doneId?.let(onSaved) }
    val space = CruxTheme.space

    Column(Modifier.fillMaxSize().imePadding().testTag("screen_PlaceEditor")) {
        CruxTopAppBar(
            title = if (draft.isNew) "New place" else "Edit place",
            onBack = onBack,
            actions = {
                // Favourites show as quick picks on Log climb.
                IconButton(
                    onClick = { viewModel.update { it.copy(favourite = !it.favourite) } },
                    modifier = Modifier.testTag("place_favourite"),
                ) {
                    Icon(
                        if (draft.favourite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (draft.favourite) "Remove from favourites" else "Add to favourites",
                        tint = if (draft.favourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!draft.isNew) {
                    IconButton(onClick = viewModel::requestDelete, modifier = Modifier.testTag("delete_place")) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete place")
                    }
                }
            },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.s4)
                .padding(top = space.s2, bottom = space.s6),
            verticalArrangement = Arrangement.spacedBy(space.s6),
        ) {
            FormSection("Place") {
                CruxTextField(
                    label = "Name",
                    value = draft.name,
                    onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                    placeholder = when (draft.type) {
                        PlaceType.GYM -> "Block Lab"
                        PlaceType.CRAG -> "Arco"
                        PlaceType.BOARD -> "Home Kilter"
                    },
                    error = draft.nameError,
                    modifier = Modifier.testTag("field_place_name"),
                )
            }

            FormSection("What's here", "Each part of the place, like a main gym, a spray wall and a Moonboard, and the grades it uses") {
                draft.sections.forEachIndexed { index, section ->
                    val change = { change: (SectionDraft) -> SectionDraft -> viewModel.update { it.updateSection(section.key, change) } }
                    SectionEditor(
                        section = section,
                        index = index,
                        removable = draft.sections.size > 1,
                        onChange = change,
                        onRemove = { viewModel.update { it.removeSection(section.key) } },
                        datasets = datasets,
                        onNewDataset = { newDatasetFor = section.key to it },
                        lights = LightsActions(
                            state = lightsState,
                            pair = { found -> viewModel.pairLights(section.key, found) },
                            forget = { viewModel.forgetLights(section.key) },
                            test = { viewModel.testLights(section.key) },
                            lightFirst = { viewModel.lightFirst(section.key) },
                            setOrder = { viewModel.setMoonOrder(section.key, it) },
                            off = { viewModel.lightsOff(section.key) },
                        ),
                    )
                    if (section.usesLocal) {
                        LocalScaleEditor(
                            scale = section.localScale,
                            error = section.localError,
                            onChange = { scale -> change { it.copy(localScale = scale) } },
                        )
                    }
                }
                // Add another part, starting from a kind.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(space.s2),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("add_section"),
                ) {
                    Text("Add", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PlaceType.entries.forEach { t ->
                        CruxFilterChip(
                            label = "+ ${t.label}",
                            selected = false,
                            onClick = { viewModel.update { it.addSection(t) } },
                            modifier = Modifier.testTag("add_section_${t.name}"),
                        )
                    }
                }
            }

            FormSection("Where · optional") {
                CruxTextField(
                    label = if (draft.onlyBoard) "Where it is" else "City or area",
                    value = draft.location,
                    onValueChange = { v -> viewModel.update { it.copy(location = v.take(MAX_NAME)) } },
                    placeholder = if (draft.onlyBoard) "Home" else "Lisbon",
                )
                MapLocationField(
                    location = draft.mapLocation,
                    placeName = draft.name,
                    onChange = { loc -> viewModel.update { it.copy(mapLocation = loc) } },
                )
            }

            FormSection("Notes · optional") {
                CruxTextField(
                    label = "",
                    value = draft.notes,
                    onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                    placeholder = "Opening hours, parking, conditions",
                    singleLine = false,
                    minLines = 3,
                )
            }
        }
        CruxButton(
            text = if (draft.isNew) "Add place" else "Save place",
            onClick = viewModel::save,
            icon = Icons.Rounded.Check,
            size = CruxButtonSize.Large,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(space.s4)
                .testTag("save_place"),
        )
    }

    newDatasetFor?.let { (key, initial) ->
        com.hardtekpt.crux.ui.datasets.DatasetDetailsSheet(
            initial = initial,
            title = "New dataset",
            confirm = "Create",
            onSave = { dataset ->
                newDatasetFor = null
                viewModel.createDataset(key, dataset)
            },
            onDismiss = { newDatasetFor = null },
        )
    }
    if (draft.confirmDelete) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Delete ${draft.name}?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "Its walls go. Your climbs and every go on them stay in your journal, with no place.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete, modifier = Modifier.testTag("confirm_delete")) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Keep") } },
        )
    }
}

/** A titled group of fields; sections are spaced apart so the form reads in calm blocks. */
@Composable
private fun FormSection(title: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s3)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Eyebrow(title)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

/**
 * Where the place is on the map. Empty, it is one inviting row; set, it shows a small map
 * with the pin, the address, and quiet actions under it.
 */
@Composable
private fun MapLocationField(location: MapLocation?, placeName: String, onChange: (MapLocation?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    if (location == null) {
        CruxListRow(
            title = "Add a map pin",
            supporting = "Search an address, or drop a pin",
            leading = { Icon(Icons.Rounded.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
            onClick = { picking = true },
            modifier = Modifier.testTag("pick_map_location"),
        )
    } else {
        val shape = MaterialTheme.shapes.large
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(CruxTheme.size.borderHairline, MaterialTheme.colorScheme.outlineVariant, shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .testTag("map_location"),
        ) {
            MapPreview(location, Modifier.fillMaxWidth().height(140.dp).clickable { picking = true })
            Column(Modifier.padding(start = CruxTheme.space.s4, end = CruxTheme.space.s2, top = CruxTheme.space.s3, bottom = CruxTheme.space.s1)) {
                Text(
                    location.address ?: "%.5f, %.5f".format(location.latitude, location.longitude),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                Row {
                    CruxButton("Change", { picking = true }, variant = CruxButtonVariant.Text, size = CruxButtonSize.Small)
                    CruxButton("Open in Maps", {
                        openInMaps(context, location, placeName.ifBlank { "Place" })
                    }, variant = CruxButtonVariant.Text, size = CruxButtonSize.Small)
                    Box(Modifier.weight(1f))
                    CruxButton("Remove", {
                        onChange(null)
                    }, variant = CruxButtonVariant.Text, size = CruxButtonSize.Small, modifier = Modifier.testTag("remove_map_location"))
                }
            }
        }
    }
    if (picking) {
        LocationPickerDialog(
            initial = location,
            placeName = placeName,
            onPick = {
                onChange(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * One discipline's grading here, as a label and the current choice; tapping opens the
 * choices. Keeps the section to two quiet lines instead of rows of chips.
 */
@Composable
internal fun ScaleChoice(
    label: String,
    discipline: Discipline,
    selected: GradeScale?,
    tagSuffix: String = "",
    allowLocal: Boolean = true,
    onSelect: (GradeScale?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val options: List<Pair<GradeScale?, String>> =
        listOf<Pair<GradeScale?, String>>(null to "My settings") +
            discipline.scales.map { it to it.label } +
            listOfNotNull((discipline.localScale to "Local grades").takeIf { allowLocal })
    val current = options.firstOrNull { it.first == selected }?.second ?: "My settings"
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = "Change $label grades") { open = true }
                .padding(vertical = CruxTheme.space.s1)
                .testTag("scale_${discipline.name}$tagSuffix"),
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(current, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Anchored at the right, under the current choice.
        Box(Modifier.align(Alignment.BottomEnd)) {
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (scale, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        trailingIcon = if (scale == selected) ({ Icon(Icons.Rounded.Check, contentDescription = null) }) else null,
                        onClick = {
                            onSelect(scale)
                            open = false
                        },
                        modifier = Modifier.testTag(if (scale?.isLocal == true) "local_${discipline.name}$tagSuffix" else "scale_option_$name"),
                    )
                }
            }
        }
    }
}

/**
 * The place's own grades, easiest first: a run of numbers with a range, or named tape
 * colours. Each colour row is just its swatch and name; reordering and removing live in
 * the row's menu so the list stays calm.
 */
@Composable
private fun LocalScaleEditor(scale: LocalScale, error: String?, onChange: (LocalScale) -> Unit) {
    val space = CruxTheme.space
    var pickingColourFor by remember { mutableStateOf<Int?>(null) }
    CruxCard(fill = CruxCardFill.Low, modifier = Modifier.testTag("local_scale_editor")) {
        Text("Local grades", style = MaterialTheme.typography.titleMedium)
        Text(
            "Easiest first. Never converted to Font or French.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = space.s3),
        )
        CruxSegmentedButtons(
            options = LocalKind.entries,
            selected = scale.kind,
            label = { it.label },
            onSelect = { kind ->
                if (kind != scale.kind) onChange(if (kind == LocalKind.NUMBERS) LocalScale.DEFAULT_NUMBERS else LocalScale.DEFAULT_COLOURS)
            },
        )
        Spacer(Modifier.height(space.s3))
        when (scale.kind) {
            LocalKind.NUMBERS -> {
                val from = scale.grades.firstOrNull()?.name?.toIntOrNull() ?: 1
                val to = scale.grades.lastOrNull()?.name?.toIntOrNull() ?: 10
                Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Easiest", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        CruxStepper(from, { onChange(LocalScale.numbers(it, maxOf(to, it + 1))) }, 0..49, "from", testTagPrefix = "local_from")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Hardest", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        CruxStepper(to, { onChange(LocalScale.numbers(from, it)) }, (from + 1)..50, "to", testTagPrefix = "local_to")
                    }
                    Text(
                        "${scale.grades.size} grades: ${scale.labels.first()} to ${scale.labels.last()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LocalKind.COLOURS -> {
                Column(verticalArrangement = Arrangement.spacedBy(space.s1)) {
                    scale.grades.forEachIndexed { index, grade ->
                        LocalColourRow(
                            index = index,
                            grade = grade,
                            isFirst = index == 0,
                            isLast = index == scale.grades.lastIndex,
                            canRemove = scale.grades.size > 2,
                            onRename = { name ->
                                onChange(scale.copy(grades = scale.grades.toMutableList().also { it[index] = grade.copy(name = name.take(16)) }))
                            },
                            onPickColour = { pickingColourFor = index },
                            onMove = { by ->
                                val list = scale.grades.toMutableList()
                                list.add(index + by, list.removeAt(index))
                                onChange(scale.copy(grades = list))
                            },
                            onRemove = { onChange(scale.copy(grades = scale.grades.filterIndexed { i, _ -> i != index })) },
                        )
                    }
                    CruxButton(
                        text = "Add a colour",
                        onClick = {
                            val unused = LocalScale.PALETTE.firstOrNull { (_, c) -> scale.grades.none { it.colour == c } } ?: LocalScale.PALETTE.last()
                            onChange(scale.copy(grades = scale.grades + LocalGrade(unused.first, unused.second)))
                        },
                        variant = CruxButtonVariant.Text,
                        icon = Icons.Rounded.Add,
                        enabled = scale.grades.size < 20,
                        modifier = Modifier.testTag("local_add_colour"),
                    )
                }
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }

    pickingColourFor?.let { index ->
        // Picking: tap a colour and the sheet closes.
        com.hardtekpt.crux.ui.components.CruxSheet(
            onDismiss = { pickingColourFor = null },
            modifier = Modifier.testTag("tape_colour_sheet"),
            title = "Tape colour",
        ) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3), verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s3)) {
                LocalScale.PALETTE.forEach { (name, colour) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(56.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                val grade = scale.grades[index]
                                // A grade still named after its old colour takes the new name too.
                                val renamed = if (LocalScale.PALETTE.any { it.first == grade.name } || grade.name.isBlank()) name else grade.name
                                onChange(scale.copy(grades = scale.grades.toMutableList().also { it[index] = LocalGrade(renamed, colour) }))
                                pickingColourFor = null
                            }
                            .padding(CruxTheme.space.s1),
                    ) {
                        TapeSwatch(argb(colour), 32.dp)
                        Text(name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

/** One tape grade: its colour (tap to change), its name, and a menu to move or remove it. */
@Composable
private fun LocalColourRow(
    index: Int,
    grade: LocalGrade,
    isFirst: Boolean,
    isLast: Boolean,
    canRemove: Boolean,
    onRename: (String) -> Unit,
    onPickColour: () -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClickLabel = "Change colour", onClick = onPickColour)
                .semantics { contentDescription = "Tape colour: " + (LocalScale.PALETTE.firstOrNull { it.second == grade.colour }?.first ?: "custom") }
                .testTag("local_colour_$index"),
        ) { TapeSwatch(argb(grade.colour ?: 0xFF9E9E9EL), 28.dp) }
        CruxTextField(
            label = "",
            value = grade.name,
            onValueChange = onRename,
            accessibilityLabel = "Grade ${index + 1} name",
            modifier = Modifier
                .weight(1f)
                .testTag("local_name_$index"),
        )
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "${grade.name} options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Move easier") }, enabled = !isFirst, onClick = {
                    menu = false
                    onMove(-1)
                })
                DropdownMenuItem(text = { Text("Move harder") }, enabled = !isLast, onClick = {
                    menu = false
                    onMove(1)
                })
                DropdownMenuItem(text = { Text("Remove") }, enabled = canRemove, onClick = {
                    menu = false
                    onRemove()
                })
            }
        }
    }
}

/** A small, still map centred on the pin, for showing where a place is. */
@Composable
fun MapPreview(location: MapLocation, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Clipped and sized here: a map view will happily draw past its bounds otherwise.
    Box(modifier.clipToBounds()) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = {
                org.osmdroid.config.Configuration.getInstance().userAgentValue = context.packageName
                org.osmdroid.views.MapView(context).apply {
                    setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                    isTilesScaledToDpi = true
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    setMultiTouchControls(false)
                    // A picture, not a control: touches go to the card around it.
                    setOnTouchListener { _, _ -> true }
                    controller.setZoom(16.0)
                }
            },
            update = { it.controller.setCenter(org.osmdroid.util.GeoPoint(location.latitude, location.longitude)) },
            onRelease = { it.onDetach() },
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
        )
        Icon(
            Icons.Rounded.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.Center)
                .size(32.dp)
                .offset(y = (-16).dp),
        )
    }
}

/**
 * A board section's Board row ("Custom board" until one is picked) and, for Kilter, a Size row;
 * each opens a sheet. A Homewall picks its holds in the Size sheet.
 */
@Composable
private fun BoardChoice(section: SectionDraft, index: Int, onChange: ((SectionDraft) -> SectionDraft) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    var sizing by remember { mutableStateOf(false) }
    val board = section.board
    ChoiceRow("Board", board?.label ?: "Custom board", "board_$index") { picking = true }
    if (board?.needsSize == true) {
        ChoiceRow(
            "Size",
            listOfNotNull(
                section.boardSize?.takeIf { it in board.sizes }?.let { com.hardtekpt.crux.data.model.boardSizeLabel(it) } ?: "Pick",
                section.boardHolds?.takeIf { board.hasHolds }?.label,
            ).joinToString(" · "),
            "board_size_$index",
        ) { sizing = true }
    }
    section.boardError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    if (picking) {
        BoardSheet(
            selected = board,
            onPick = { picked ->
                picking = false
                onChange {
                    it.copy(
                        board = picked,
                        // A size or holds the new board doesn't have goes.
                        boardSize = it.boardSize?.takeIf { size -> picked?.sizes?.contains(size) == true },
                        boardHolds = it.boardHolds?.takeIf { _ -> picked?.hasHolds == true },
                    )
                }
                if (picked?.needsSize == true && section.boardSize !in picked.sizes) sizing = true
            },
            onDismiss = { picking = false },
        )
    }
    if (sizing && board?.needsSize == true) {
        BoardSizeSheet(
            board = board,
            size = section.boardSize,
            holds = section.boardHolds,
            onChange = { size, holds -> onChange { it.copy(boardSize = size, boardHolds = holds) } },
            onDismiss = { sizing = false },
        )
    }
}

/** A "label · value ▾" row like the grade rows. */
@Composable
private fun ChoiceRow(label: String, value: String, tag: String, onClick: () -> Unit) {
    com.hardtekpt.crux.ui.components.SettingRow(label, value, tag, onClick)
}

/** Which board: Kilter, then MoonBoard, then Custom board, each with a line on what it covers. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun BoardSheet(
    selected: com.hardtekpt.crux.data.model.BoardModel?,
    onPick: (com.hardtekpt.crux.data.model.BoardModel?) -> Unit,
    onDismiss: () -> Unit,
    /** Datasets: also offer No board, at the top; picking it calls this. */
    onNoBoard: (() -> Unit)? = null,
    noBoardSelected: Boolean = false,
) {
    CruxSheet(
        onDismiss = onDismiss,
        modifier = Modifier.testTag("board_sheet"),
        title = "Board",
    ) {
        onNoBoard?.let {
            CruxListRow(
                title = "No board",
                supporting = "A gym circuit or a crag's climbs",
                selected = noBoardSelected,
                onClick = it,
                modifier = Modifier.testTag("board_option_none"),
            )
        }
        com.hardtekpt.crux.data.model.BoardModel.entries.groupBy { it.maker }.forEach { (maker, models) ->
            Eyebrow(maker, Modifier.padding(top = CruxTheme.space.s2))
            models.forEach { model ->
                CruxListRow(
                    title = model.label,
                    supporting = model.hint,
                    selected = model == selected,
                    onClick = { onPick(model) },
                    modifier = Modifier.testTag("board_option_${model.key}"),
                )
            }
        }
        Eyebrow("Other", Modifier.padding(top = CruxTheme.space.s2))
        CruxListRow(
            title = "Custom board",
            supporting = "Spray wall, home board or anything else",
            selected = selected == null && !noBoardSelected,
            onClick = { onPick(null) },
            modifier = Modifier.testTag("board_option_custom"),
        )
    }
}

/** A Kilter board's size as chips; a Homewall adds Full ride or Mainline only in the same sheet. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun BoardSizeSheet(
    board: com.hardtekpt.crux.data.model.BoardModel,
    size: String?,
    holds: com.hardtekpt.crux.data.model.BoardHolds?,
    onChange: (String?, com.hardtekpt.crux.data.model.BoardHolds?) -> Unit,
    onDismiss: () -> Unit,
) {
    CruxSheet(
        onDismiss = onDismiss,
        modifier = Modifier.testTag("board_size_sheet"),
        title = "Size · ${board.label}",
        confirm = SheetButton("Done", onDismiss, tag = "board_size_done"),
        spacing = CruxTheme.space.s3,
        scroll = false,
    ) {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
            board.sizes.forEach { option ->
                CruxFilterChip(
                    label = com.hardtekpt.crux.data.model.boardSizeLabel(option),
                    selected = option == size,
                    onClick = { onChange(option, holds) },
                    modifier = Modifier.testTag("board_size_$option"),
                )
            }
        }
        if (board.hasHolds) {
            Eyebrow("Holds")
            com.hardtekpt.crux.data.model.BoardHolds.entries.forEach { option ->
                CruxListRow(
                    title = option.label,
                    supporting = option.hint,
                    selected = option == holds,
                    onClick = { onChange(size, option) },
                    modifier = Modifier.testTag("board_holds_${option.key}"),
                )
            }
        }
    }
}

/** What a board section's Lights row can do. */
class LightsActions(
    val state: com.hardtekpt.crux.data.BoardLink.State,
    val pair: (com.hardtekpt.crux.ui.components.FoundBoard) -> Unit,
    val forget: () -> Unit,
    val test: () -> Unit,
    val lightFirst: () -> Unit,
    val setOrder: (com.hardtekpt.crux.data.BoardLights.MoonOrder) -> Unit,
    val off: () -> Unit,
)

/** The Lights row: "Pair a board", or the paired board's name; its sheet pairs, tests, checks and forgets. */
@Composable
private fun LightsChoice(section: SectionDraft, index: Int, actions: LightsActions) {
    var open by remember { mutableStateOf(false) }
    val lights = section.lights
    ChoiceRow("Lights", lights?.name ?: if (section.id == 0L) "Save the place first" else "Pair a board", "section_lights_$index") { open = true }
    if (open) LightsSheet(section, actions) { open = false }
}

/**
 * Pairing a board's lights through Android's own picker, and once paired: Test lights (the four
 * corners), for a MoonBoard the one-hold check that learns its LED order, and Forget.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LightsSheet(section: SectionDraft, actions: LightsActions, onDismiss: () -> Unit) {
    val space = CruxTheme.space
    val kilter = section.board?.maker == "Kilter"
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val pair = com.hardtekpt.crux.ui.components.rememberBoardPairing(
        onFound = {
            error = null
            actions.pair(it)
        },
        onFailed = { error = it },
    )
    CruxSheet(
        onDismiss = {
            if (checking) actions.off()
            onDismiss()
        },
        modifier = Modifier.testTag("lights_sheet"),
        title = "Lights · ${section.name.ifBlank { section.board?.label.orEmpty() }}",
        spacing = space.s3,
    ) {
        val lights = section.lights
        when {
            section.id == 0L -> Text("Save the place first, then pair its board here.", style = MaterialTheme.typography.bodyMedium)

            lights == null -> {
                Text(
                    "Climb Logger lights a climb's holds on your ${section.board?.label} when you tap Light. Android lists the boards nearby; " +
                        "pick yours. Climb Logger asks for Nearby devices, never your location.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                CruxButton("Pair a board", { pair(kilter) }, modifier = Modifier.fillMaxWidth().testTag("lights_pair"))
            }

            checking -> {
                Text("Climb Logger has lit one hold on your MoonBoard. Which one lit up on the wall?", style = MaterialTheme.typography.bodyMedium)
                val rows = if (section.board?.fixedAngle == 40) 12 else 18
                FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalArrangement = Arrangement.spacedBy(space.s2)) {
                    listOf(
                        "A1, bottom left" to com.hardtekpt.crux.data.BoardLights.MoonOrder.FROM_A1,
                        "K1, bottom right" to com.hardtekpt.crux.data.BoardLights.MoonOrder.FROM_K1,
                        "A$rows, top left" to com.hardtekpt.crux.data.BoardLights.MoonOrder.FROM_A_TOP,
                    ).forEach { (label, order) ->
                        CruxButton(label, {
                            checking = false
                            actions.setOrder(order)
                        }, variant = CruxButtonVariant.Outlined, size = CruxButtonSize.Small)
                    }
                    CruxButton("None", {
                        checking = false
                        actions.off()
                        error = "Nothing lit: check the board is on and close by, then try again."
                    }, variant = CruxButtonVariant.Text, size = CruxButtonSize.Small)
                }
            }

            else -> {
                Text(
                    listOfNotNull(
                        lights.name,
                        when (lights.protocol) {
                            com.hardtekpt.crux.data.BoardLights.AURORA_V3 -> "protocol 3"
                            com.hardtekpt.crux.data.BoardLights.AURORA_V2 -> "protocol 2"
                            else -> "MoonBoard LED box"
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalArrangement = Arrangement.spacedBy(space.s2)) {
                    val small = CruxButtonSize.Small
                    CruxButton("Test lights", actions.test, variant = CruxButtonVariant.Outlined, size = small, modifier = Modifier.testTag("lights_test"))
                    if (!kilter) {
                        CruxButton("Check a hold", {
                            checking = true
                            actions.lightFirst()
                        }, variant = CruxButtonVariant.Outlined, size = CruxButtonSize.Small, modifier = Modifier.testTag("lights_check"))
                    }
                    CruxButton("Lights off", actions.off, variant = CruxButtonVariant.Outlined, size = CruxButtonSize.Small)
                    CruxButton("Forget", actions.forget, variant = CruxButtonVariant.Text, size = small, modifier = Modifier.testTag("lights_forget"))
                }
                Text(
                    if (kilter) {
                        "Test lights lights the four corners of your size."
                    } else {
                        "Test lights lights the four corners; Check a hold makes sure Climb Logger counts your box's LEDs the right way round."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when (val state = actions.state) {
            is com.hardtekpt.crux.data.BoardLink.State.Failed -> Text(
                state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            is com.hardtekpt.crux.data.BoardLink.State.Sending -> Text("Sending…", style = MaterialTheme.typography.bodySmall)

            is com.hardtekpt.crux.data.BoardLink.State.Lit -> Text("Lit ${state.climb}.", style = MaterialTheme.typography.bodySmall)

            else -> Unit
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}
