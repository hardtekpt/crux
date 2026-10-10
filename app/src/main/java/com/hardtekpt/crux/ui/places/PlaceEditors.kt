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
import com.hardtekpt.crux.ui.components.CruxStepper
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
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
private fun SectionEditor(section: SectionDraft, index: Int, removable: Boolean, onChange: ((SectionDraft) -> SectionDraft) -> Unit, onRemove: () -> Unit) {
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
        // The grades climbs here use; boards hold boulders only.
        val tag = if (index == 0) "" else "_$index"
        ScaleChoice("Boulder grades", Discipline.BOULDER, section.boulderScale, tag) { scale -> onChange { it.copy(boulderScale = scale) } }
        if (section.type != PlaceType.BOARD) {
            ScaleChoice("Route grades", Discipline.ROUTE, section.routeScale, tag) { scale -> onChange { it.copy(routeScale = scale) } }
        }
    }
}

data class PlaceDraft(
    val id: Long = 0,
    val name: String = "",
    /** The named parts of the place, in order: a kind and a name each. At least one. */
    val sections: List<SectionDraft> = listOf(SectionDraft(type = PlaceType.GYM)),
    val location: String = "",
    val defaultAngle: Int = 40,
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
) {
    /** Boards hold boulders only. */
    val usesLocal: Boolean get() = boulderScale?.isLocal == true || (type != PlaceType.BOARD && routeScale?.isLocal == true)
}

@HiltViewModel
class PlaceEditorViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repository: PlaceRepository) : ViewModel() {
    private val placeId: Long = savedStateHandle.get<Long>("placeId") ?: 0L
    private val _draft = MutableStateFlow(PlaceDraft(id = placeId))
    val draft: StateFlow<PlaceDraft> = _draft.asStateFlow()

    init {
        if (placeId != 0L) {
            viewModelScope.launch {
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
                                    )
                                },
                            location = p.location.orEmpty(),
                            defaultAngle = p.defaultAngle ?: 40,
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
        change(d).let { it.copy(nameError = null, sections = it.sections.map { s -> s.copy(localError = null) }) }
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
            section.copy(localScale = local, localError = localError)
        }
        if (error != null || sections.any { it.localError != null }) {
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
                        )
                    },
                    location = d.location,
                    defaultAngle = d.defaultAngle.takeIf { d.hasBoard },
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
                if (draft.hasBoard) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Usual angle", style = MaterialTheme.typography.titleMedium)
                            Text("Where new climbs here start", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        CruxStepper(draft.defaultAngle, { a -> viewModel.update { it.copy(defaultAngle = a) } }, 0..70, "deg", step = 5)
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
private fun ScaleChoice(label: String, discipline: Discipline, selected: GradeScale?, tagSuffix: String = "", onSelect: (GradeScale?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val options: List<Pair<GradeScale?, String>> =
        listOf<Pair<GradeScale?, String>>(null to "My settings") +
            discipline.scales.map { it to it.label } +
            (discipline.localScale to "Local grades")
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
        AlertDialog(
            onDismissRequest = { pickingColourFor = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Tape colour", style = MaterialTheme.typography.headlineSmall) },
            text = {
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
            },
            confirmButton = { TextButton(onClick = { pickingColourFor = null }) { Text("Cancel") } },
        )
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
                .testTag("local_colour_$index"),
        ) { TapeSwatch(argb(grade.colour ?: 0xFF9E9E9EL), 28.dp) }
        CruxTextField(
            label = "",
            value = grade.name,
            onValueChange = onRename,
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

// ---- Problem editor ------------------------------------------------------------------

data class ProblemDraft(
    val id: Long = 0,
    val placeId: Long,
    val areaId: Long? = null,
    val name: String = "",
    val discipline: Discipline = Discipline.BOULDER,
    val gradeScale: GradeScale = GradeScale.FONT,
    val gradeIndex: Int = GradeScale.FONT.defaultIndex,
    /** The place's local grades, when [gradeScale] is local. */
    val local: LocalScale? = null,
    val tape: Int? = null,
    val notes: String = "",
    val retired: Boolean = false,
    val nameError: String? = null,
    val confirmDelete: Boolean = false,
    val done: Boolean = false,
    val deleted: Boolean = false,
) {
    val isNew: Boolean get() = id == 0L
    val system: GradeSystem get() = GradeSystem(gradeScale, local.takeIf { gradeScale.isLocal })
}

@HiltViewModel
class ProblemEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PlaceRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {
    private val placeId: Long = savedStateHandle.get<Long>("placeId") ?: 0L
    private val problemId: Long = savedStateHandle.get<Long>("problemId") ?: 0L
    private var settings = GradeScales()
    private val _draft = MutableStateFlow(ProblemDraft(id = problemId, placeId = placeId))
    val draft: StateFlow<ProblemDraft> = _draft.asStateFlow()

    val areas: StateFlow<List<Area>> = repository.observePlaceDetail(placeId).map { it?.areas.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val place: StateFlow<com.hardtekpt.crux.data.model.Place?> = repository.observePlaceDetail(placeId).map { it?.place }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            settings = preferences.gradeScales.first()
            val problem = problemId.takeIf { it != 0L }?.let { repository.getProblem(it) }
            val local = sectionOf(problem?.areaId)?.localScale
            _draft.update { it.copy(local = local) }
            if (problem != null) {
                _draft.update {
                    it.copy(
                        placeId = problem.placeId ?: 0L, areaId = problem.areaId, name = problem.name,
                        discipline = problem.discipline, gradeScale = problem.gradeScale, gradeIndex = problem.gradeIndex,
                        tape = problem.tape, notes = problem.notes.orEmpty(), retired = problem.retired,
                    )
                }
            } else {
                val scale = scaleFor(Discipline.BOULDER)
                _draft.update { it.copy(gradeScale = scale).let { d -> d.copy(gradeIndex = d.system.defaultIndex) } }
            }
        }
    }

    /** The part of the place a wall is in (the first part without a wall). */
    private suspend fun sectionOf(areaId: Long?): com.hardtekpt.crux.data.model.Section? {
        val detail = repository.observePlaceDetail(placeId).first() ?: return null
        return detail.place.sectionOf(detail.areas.firstOrNull { it.id == areaId })
    }

    /** That part's scale for a discipline, else the climber's setting. */
    private suspend fun scaleFor(discipline: Discipline): GradeScale =
        sectionOf(_draft.value.areaId)?.scaleFor(discipline) ?: settings.forDiscipline(discipline)

    fun setDiscipline(discipline: Discipline) {
        viewModelScope.launch {
            val scale = scaleFor(discipline)
            _draft.update { it.copy(discipline = discipline, gradeScale = scale).let { d -> d.copy(gradeIndex = d.system.defaultIndex) } }
        }
    }

    fun update(change: (ProblemDraft) -> ProblemDraft) {
        val before = _draft.value.areaId
        _draft.update { change(it).copy(nameError = null) }
        // A wall in another part of the place can grade differently.
        if (_draft.value.areaId != before && _draft.value.id == 0L) {
            viewModelScope.launch {
                val scale = scaleFor(_draft.value.discipline)
                val local = sectionOf(_draft.value.areaId)?.localScale
                _draft.update { d ->
                    if (d.gradeScale == scale &&
                        d.local == local
                    ) {
                        d
                    } else {
                        d.copy(gradeScale = scale, local = local).let { it.copy(gradeIndex = it.system.defaultIndex) }
                    }
                }
            }
        }
    }

    fun save() {
        val d = _draft.value
        val error = when {
            d.name.isBlank() -> "Give the climb a name, even just the colour and wall"
            d.name.trim().length > MAX_NAME -> "Keep the name under $MAX_NAME characters"
            else -> null
        }
        if (error != null) {
            _draft.update { it.copy(nameError = error) }
            return
        }
        viewModelScope.launch {
            val id = repository.saveProblem(
                ProblemInput(
                    id = d.id, placeId = d.placeId.takeIf { it != 0L }, areaId = d.areaId, name = d.name, discipline = d.discipline,
                    gradeScale = d.gradeScale, gradeIndex = d.gradeIndex, tape = d.tape, notes = d.notes,
                    gradeLabel = d.system.label(d.gradeIndex), gradeColour = d.system.colour(d.gradeIndex),
                ),
            )
            if (!d.isNew) repository.setRetired(id, d.retired)
            _draft.update { it.copy(done = true) }
        }
    }

    fun requestDelete() = _draft.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = _draft.update { it.copy(confirmDelete = false) }
    fun confirmDelete() {
        viewModelScope.launch {
            repository.deleteProblem(problemId)
            _draft.update { it.copy(confirmDelete = false, done = true, deleted = true) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProblemEditorScreen(onDone: (deleted: Boolean) -> Unit, viewModel: ProblemEditorViewModel = hiltViewModel()) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val areas by viewModel.areas.collectAsStateWithLifecycle()
    val place by viewModel.place.collectAsStateWithLifecycle()
    LaunchedEffect(draft.done) { if (draft.done) onDone(draft.deleted) }
    val space = CruxTheme.space
    val noun = "climb"

    Column(Modifier.fillMaxSize().imePadding().testTag("screen_ProblemEditor")) {
        CruxTopAppBar(
            title = if (draft.isNew) "New $noun" else "Edit $noun",
            onBack = { onDone(false) },
            actions = {
                if (!draft.isNew) {
                    IconButton(onClick = viewModel::requestDelete, modifier = Modifier.testTag("delete_problem")) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete $noun")
                    }
                }
            },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.s4),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            if (place?.types != listOf(PlaceType.BOARD)) {
                CruxSegmentedButtons(Discipline.entries, draft.discipline, { it.label }, viewModel::setDiscipline)
            }
            CruxTextField(
                label = "Name",
                value = draft.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v.take(MAX_NAME)) } },
                placeholder = "Yellow dyno",
                error = draft.nameError,
                modifier = Modifier.testTag("field_problem_name"),
            )
            Eyebrow("Grade · ${draft.system.name}")
            GradeStrip(
                grades = draft.system.labels,
                colours = draft.system.local?.grades?.map { it.colour },
                selectedIndex = draft.gradeIndex,
                onSelect = { index -> viewModel.update { it.copy(gradeIndex = index) } },
                tagPrefix = "problem_grade",
                modifier = Modifier.bleed(space.s4),
            )
            if (areas.isNotEmpty()) {
                Eyebrow(place?.let { if (it.hasSeveralTypes) "Area" else it.type.areaLabel } ?: "Wall")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalArrangement = Arrangement.spacedBy(space.s2)) {
                    CruxFilterChip("None", draft.areaId == null, { viewModel.update { it.copy(areaId = null) } })
                    areas.forEach { area ->
                        CruxFilterChip(area.name, draft.areaId == area.id, { viewModel.update { it.copy(areaId = area.id) } })
                    }
                }
            }
            Eyebrow("Tape colour")
            TapePicker(draft.tape) { t -> viewModel.update { it.copy(tape = t) } }
            CruxTextField(
                label = "Notes",
                value = draft.notes,
                onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                placeholder = "Beta, start holds, who set it",
                helper = "Optional",
                singleLine = false,
                minLines = 2,
            )
            if (!draft.isNew) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Taken down", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = draft.retired, onCheckedChange = { r -> viewModel.update { it.copy(retired = r) } })
                }
            }
        }
        CruxButton(
            text = if (draft.isNew) "Add $noun" else "Save $noun",
            onClick = viewModel::save,
            icon = Icons.Rounded.Check,
            size = CruxButtonSize.Large,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(space.s4)
                .testTag("save_problem"),
        )
    }

    if (draft.confirmDelete) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Delete ${draft.name}?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Every go you logged on it is deleted too, and it leaves your journal.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete, modifier = Modifier.testTag("confirm_delete")) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Keep") } },
        )
    }
}

private val TAPE_NAMES = listOf("Red", "Orange", "Yellow", "Green", "Blue", "Purple")

/** The gym's route tape, as swatches; tap again to clear. */
@Composable
private fun TapePicker(selected: Int?, onSelect: (Int?) -> Unit) {
    val tape = CruxTheme.colors.tape
    Row(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
        tape.forEachIndexed { index, color ->
            val isSelected = selected == index
            Box(
                Modifier
                    .size(36.dp)
                    .border(
                        if (isSelected) 2.dp else 1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    )
                    .padding(4.dp)
                    .border(18.dp, color, CircleShape)
                    .clickable { onSelect(if (isSelected) null else index) }
                    .testTag("tape_${TAPE_NAMES[index]}"),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Rounded.Check, contentDescription = TAPE_NAMES[index], modifier = Modifier.size(16.dp))
            }
        }
    }
}
