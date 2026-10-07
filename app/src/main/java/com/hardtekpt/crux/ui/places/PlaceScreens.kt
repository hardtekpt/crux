package com.hardtekpt.crux.ui.places

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Domain
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.PlaceRepository
import com.hardtekpt.crux.data.images.AreaImageStore
import com.hardtekpt.crux.data.model.Area
import com.hardtekpt.crux.data.model.PlaceDetail
import com.hardtekpt.crux.data.model.PlaceSummary
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.model.ProblemWithStats
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.GradeBadge
import com.hardtekpt.crux.ui.components.GradeState
import com.hardtekpt.crux.ui.components.ImageThumbnail
import com.hardtekpt.crux.ui.components.ImageViewer
import com.hardtekpt.crux.ui.components.InlineEmptyState
import com.hardtekpt.crux.ui.components.areaImageFile
import com.hardtekpt.crux.ui.components.rememberLocalImage
import com.hardtekpt.crux.ui.journal.TapeDot
import com.hardtekpt.crux.ui.journal.problemLine
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.relativeLabel
import com.hardtekpt.crux.ui.shortLabel
import com.hardtekpt.crux.ui.theme.Archivo
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---- Places list (Journal › Places) --------------------------------------------------

@HiltViewModel
class PlacesViewModel @Inject constructor(repository: PlaceRepository) : ViewModel() {
    val places: StateFlow<List<PlaceSummary>?> = repository.observePlaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** The Places half of the Journal tab, as list items. */
fun LazyListScope.placesList(
    places: List<PlaceSummary>?,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    filter: PlaceType? = null,
    onFilter: (PlaceType?) -> Unit = {},
) {
    // No chip picked shows every place; tapping the picked chip again clears it.
    item(key = "place_filters") {
        Row(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), modifier = Modifier.testTag("place_filters")) {
            listOf(PlaceType.CRAG, PlaceType.GYM, PlaceType.BOARD).forEach { type ->
                CruxFilterChip(
                    label = type.label,
                    selected = filter == type,
                    onClick = { onFilter(if (filter == type) null else type) },
                    modifier = Modifier.testTag("filter_${type.name}"),
                )
            }
        }
    }
    // Favourites first, then the rest in their usual order.
    val shown = places?.filter { filter == null || filter in it.place.types }?.sortedByDescending { it.place.favourite }
    item {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Eyebrow(
                (filter?.let { "${it.label}s" } ?: "Gyms, crags and boards") + " · ${shown?.size ?: 0}",
                Modifier.weight(1f),
            )
            CruxButton(
                text = "New place",
                onClick = onNew,
                variant = CruxButtonVariant.Tonal,
                icon = Icons.Rounded.Add,
                modifier = Modifier.testTag("new_place"),
            )
        }
    }
    if (shown != null && shown.isEmpty()) {
        item {
            InlineEmptyState(
                icon = Icons.Rounded.Place,
                text = if (places.isEmpty()) {
                    "No places yet. Add your gym, crag or board to log walls and problems there."
                } else {
                    "No ${filter?.label?.lowercase()}s yet. Tap ${filter?.label} again to see every place."
                },
            )
        }
    }
    items(shown.orEmpty(), key = { "place_${it.place.id}" }) { summary ->
        PlaceCard(summary, onOpen = { onOpen(summary.place.id) }, modifier = Modifier.testTag("place_row"))
    }
}

fun placeIcon(type: PlaceType) = when (type) {
    PlaceType.GYM -> Icons.Rounded.Domain
    PlaceType.CRAG -> Icons.Rounded.Landscape
    PlaceType.BOARD -> Icons.Rounded.GridView
}

// ---- Place page ----------------------------------------------------------------------

@HiltViewModel
class PlaceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PlaceRepository,
    private val images: AreaImageStore,
    climbRepository: com.hardtekpt.crux.data.ClimbRepository,
) : ViewModel() {
    val placeId: Long = savedStateHandle.get<Long>("placeId") ?: 0L

    val detail: StateFlow<PlaceDetail?> = repository.observePlaceDetail(placeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Climbs logged here, for the header's numbers and last visit. */
    val climbs: StateFlow<List<com.hardtekpt.crux.data.model.Climb>> = climbRepository.observeClimbsAtPlace(placeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saves a wall; a replaced or removed image file is deleted. */
    fun saveArea(areaId: Long, name: String, angle: Int?, image: String?, previousImage: String?, sectionId: Long?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.saveArea(placeId, areaId, name, angle, image, sectionId)
            if (previousImage != image) images.delete(previousImage)
        }
    }

    fun deleteArea(area: Area) {
        viewModelScope.launch {
            repository.deleteArea(area.id)
            images.delete(area.imagePath)
        }
    }

    /** Copies a picked or captured image into the app; [onReady] gets its file name. */
    fun importImage(uri: Uri, onReady: (String) -> Unit, onFailed: () -> Unit) {
        viewModelScope.launch {
            runCatching { images.importFrom(uri) }.onSuccess(onReady).onFailure { onFailed() }
        }
    }

    /** An image added in the dialog and then cancelled, or swapped for another. */
    fun discardImage(name: String?) {
        viewModelScope.launch { images.delete(name) }
    }

    fun captureUri(): Uri = images.newCaptureUri()

    fun resetArea(id: Long) {
        viewModelScope.launch { repository.resetArea(id) }
    }
}

/** A place's walls and the problems on them, with how you've done on each. */
@Composable
fun PlaceDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenProblem: (Long) -> Unit,
    onNewProblem: (Long) -> Unit,
    onLogHere: (placeId: Long, sectionId: Long?) -> Unit,
    viewModel: PlaceDetailViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val climbs by viewModel.climbs.collectAsStateWithLifecycle()
    // The facility the list shows; null shows every one.
    var facility by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingArea by remember { mutableStateOf<Area?>(null) }
    var viewingArea by remember { mutableStateOf<Area?>(null) }
    var addingArea by rememberSaveable { mutableStateOf(false) }
    var showRetired by rememberSaveable { mutableStateOf(false) }
    val space = CruxTheme.space
    val place = detail?.place

    Column(Modifier.fillMaxSize().testTag("screen_PlaceDetail")) {
        CruxTopAppBar(
            title = "",
            onBack = onBack,
            actions = {
                if (place != null) {
                    IconButton(onClick = { onEdit(place.id) }, modifier = Modifier.testTag("edit_place")) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit place")
                    }
                }
            },
        )
        val current = detail ?: return@Column
        // One kind of place says Wall, Sector or Set; a mixed place says Area.
        val areaLabel = if (current.place.hasSeveralTypes) "Area" else current.place.type.areaLabel
        val sections = current.place.sections
        val picked = sections.firstOrNull { it.id == facility }
        LazyColumn(
            contentPadding = PaddingValues(start = space.s4, end = space.s4, bottom = space.s4 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s3),
            modifier = Modifier.testTag("place_list"),
        ) {
            // The banner, with the name and where it is on it.
            item(key = "banner") {
                Box(
                    Modifier
                        .padding(horizontal = 0.dp)
                        .fillMaxWidth()
                        .height(156.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .testTag("place_banner"),
                ) {
                    PlaceBannerArt(current.place, current.areas.sortedBy { it.id }.firstNotNullOfOrNull { it.imagePath })
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))))
                            .padding(horizontal = space.s4, vertical = space.s3),
                    ) {
                        Text(
                            current.place.name,
                            style = TextStyle(
                                fontFamily = Archivo,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 28.sp,
                                lineHeight = 32.sp,
                                letterSpacing = (-0.4).sp,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val where = current.place.location ?: current.place.mapLocation?.address
                        where?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            // The numbers, each with a plain label and what it means.
            item(key = "stats") {
                val live = current.problems.filter { !it.problem.retired }
                val projects = live.count { it.isProject }
                val lastVisit = climbs.maxOfOrNull { it.date }
                Row(horizontalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.height(IntrinsicSize.Min).testTag("place_stats")) {
                    PlaceFigure(
                        value = climbs.size,
                        label = if (climbs.size == 1) "climb logged" else "climbs logged",
                        detail = lastVisit?.let { "last ${it.relativeLabel(LocalDate.now()).lowercase()}" } ?: "none yet",
                        modifier = Modifier.weight(1f),
                    )
                    PlaceFigure(
                        value = live.size,
                        label = if (live.size == 1) "problem up" else "problems up",
                        detail = run {
                            val n = current.areas.size
                            val noun = if (current.place.hasSeveralTypes) "area" else current.place.type.areaLabel.lowercase()
                            "on $n ${if (n == 1) noun else noun + "s"}"
                        },
                        modifier = Modifier.weight(1f),
                    )
                    PlaceFigure(
                        value = projects,
                        label = if (projects == 1) "open project" else "open projects",
                        detail = "tried, not sent",
                        highlight = projects > 0,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // One tile per facility. With several, a tile filters the list and Log goes there.
            if (sections.isNotEmpty()) {
                item(key = "facilities") {
                    Column(verticalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.testTag("facilities")) {
                        Eyebrow(if (sections.size > 1) "Facilities · tap one to filter" else "Facility")
                        sections.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.height(IntrinsicSize.Min)) {
                                pair.forEach { section ->
                                    val areas = current.areas.filter { current.place.sectionOf(it)?.id == section.id }
                                    val problems = current.problems.count { p -> !p.problem.retired && areas.any { it.id == p.problem.areaId } }
                                    FacilityTile(
                                        section = section,
                                        areas = areas.size,
                                        problems = problems,
                                        angle = current.place.defaultAngle.takeIf { section.type == PlaceType.BOARD },
                                        scales = listOfNotNull(
                                            current.place.boulderScale?.label,
                                            current.place.routeScale?.label.takeIf { section.type != PlaceType.BOARD },
                                        ),
                                        selected = sections.size > 1 && facility == section.id,
                                        onClick = { if (sections.size > 1) facility = if (facility == section.id) null else section.id },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                // A lone facility takes the full width; an odd one out keeps the grid.
                                if (pair.size == 1 && sections.size > 1) Box(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            item(key = "actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalAlignment = Alignment.CenterVertically) {
                    CruxButton(
                        text = picked?.let { "Log at ${it.name}" } ?: "Log here",
                        onClick = { onLogHere(current.place.id, picked?.id) },
                        icon = Icons.Rounded.Add,
                        modifier = Modifier.weight(1f, fill = false).testTag("log_here"),
                    )
                    CruxButton(
                        text = "Add problem",
                        onClick = { onNewProblem(current.place.id) },
                        variant = CruxButtonVariant.Outlined,
                        modifier = Modifier.testTag("add_problem"),
                    )
                }
            }
            current.place.mapLocation?.let { location ->
                item(key = "map") {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(space.s2),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { openInMaps(context, location, current.place.name) }
                            .padding(vertical = space.s1)
                            .testTag("place_map_location"),
                    ) {
                        Icon(Icons.Rounded.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(
                            location.address ?: "%.5f, %.5f".format(location.latitude, location.longitude),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text("Open in Maps", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            val visible = current.problems.filter { showRetired || !it.problem.retired }
            // With a facility picked, only its walls and their problems.
            val shownAreas = current.areas.filter { picked == null || current.place.sectionOf(it)?.id == picked.id }
            val groups: List<Pair<Area?, List<ProblemWithStats>>> =
                shownAreas.map { area -> area to visible.filter { it.problem.areaId == area.id } } +
                    listOf<Pair<Area?, List<ProblemWithStats>>>(null to visible.filter { picked == null && it.problem.areaId == null })

            groups.forEach { (area, problems) ->
                if (area == null && problems.isEmpty()) return@forEach
                item(key = "area_${area?.id ?: "none"}") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(space.s3),
                        modifier = Modifier.padding(top = space.s3),
                    ) {
                        area?.imagePath?.let { image ->
                            ImageThumbnail(
                                name = image,
                                description = "${area.name} image",
                                onClick = { viewingArea = area },
                                size = 44.dp,
                                modifier = Modifier.testTag("area_image_${area.name}"),
                            )
                        }
                        Eyebrow(
                            area?.let { a ->
                                listOfNotNull(
                                    a.name,
                                    current.place.sectionOf(a)?.name?.takeIf { current.place.hasSeveralTypes },
                                    a.angle?.let { "$it°" },
                                    a.resetDate?.let { "reset ${it.shortLabel()}" },
                                ).joinToString(" · ")
                            }
                                ?: "No ${areaLabel.lowercase()}",
                            Modifier.weight(1f),
                        )
                        if (area !=
                            null
                        ) {
                            AreaMenu(area, onEdit = {
                                editingArea = area
                            }, onReset = { viewModel.resetArea(area.id) }, onDelete = { viewModel.deleteArea(area) })
                        }
                    }
                }
                if (area != null && problems.isEmpty()) {
                    item(key = "area_empty_${area.id}") {
                        Text(
                            "Nothing saved on this ${areaLabel.lowercase()} yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (problems.isNotEmpty()) {
                    item(key = "problems_${area?.id ?: "none"}") {
                        // A wall's problems as flat lines on one panel.
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .border(CruxTheme.size.borderHairline, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                .padding(horizontal = space.s3),
                        ) {
                            problems.forEachIndexed { index, item ->
                                if (index > 0) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                ProblemLine(item, onOpenProblem)
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.padding(top = space.s3)) {
                    CruxButton(
                        text = "Add ${areaLabel.lowercase()}",
                        onClick = { addingArea = true },
                        variant = CruxButtonVariant.Text,
                        icon = Icons.Rounded.Add,
                        modifier = Modifier.testTag("add_area"),
                    )
                    if (current.problems.any { it.problem.retired }) {
                        CruxButton(
                            text = if (showRetired) "Hide retired" else "Show retired",
                            onClick = { showRetired = !showRetired },
                            variant = CruxButtonVariant.Text,
                        )
                    }
                }
            }
        }
    }

    if (addingArea || editingArea != null) {
        AreaDialog(
            area = editingArea,
            sections = place?.sections.orEmpty(),
            initialSection = place?.sectionOf(editingArea),
            fallbackType = place?.type ?: PlaceType.GYM,
            viewModel = viewModel,
            onSave = { name, angle, image, sectionId ->
                viewModel.saveArea(editingArea?.id ?: 0, name, angle, image, editingArea?.imagePath, sectionId)
                addingArea = false
                editingArea = null
            },
            onDismiss = {
                addingArea = false
                editingArea = null
            },
        )
    }
    viewingArea?.let { area ->
        area.imagePath?.let { image -> ImageViewer(image, area.name) { viewingArea = null } }
    }
}

private fun scaleLine(detail: PlaceDetail): String? {
    val scales = listOfNotNull(detail.place.boulderScale?.label, detail.place.routeScale?.label)
    return if (scales.isEmpty()) null else "grades in ${scales.joinToString(" / ")}"
}

/** One of the place's numbers: the figure, what it counts in words, and a line of context. */
@Composable
private fun PlaceFigure(value: Int, label: String, detail: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(14.dp))
            .background(if (highlight) colors.secondaryContainer else colors.surfaceContainerLow)
            .border(CruxTheme.size.borderHairline, if (highlight) colors.secondary else colors.outlineVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(value.toString(), style = CruxTheme.type.metricMedium, color = if (highlight) colors.onSecondaryContainer else colors.onSurface)
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (highlight) colors.onSecondaryContainer else colors.onSurface)
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = if (highlight) colors.onSecondaryContainer.copy(alpha = 0.8f) else colors.onSurfaceVariant,
        )
    }
}

/** A facility: its kind's icon and colour, name, and what's there. Picked, it lights up. */
@Composable
private fun FacilityTile(
    section: com.hardtekpt.crux.data.model.Section,
    areas: Int,
    problems: Int,
    angle: Int?,
    scales: List<String>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val tint = kindAccent(section.type)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxHeight()
            .clip(shape)
            .background(if (selected) tint.copy(alpha = 0.16f) else colors.surfaceContainerLow)
            .border(if (selected) CruxTheme.size.borderEmphasis else CruxTheme.size.borderHairline, if (selected) tint else colors.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag("facility_${section.name}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(placeIcon(section.type), contentDescription = section.type.label, tint = tint, modifier = Modifier.size(20.dp))
            Text(section.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            listOfNotNull(
                section.type.label.takeIf { !it.equals(section.name, ignoreCase = true) },
                "$areas ${if (areas == 1) section.type.areaLabel.lowercase() else section.type.areaLabel.lowercase() + "s"}",
                angle?.let { "$it°" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Text(
            listOfNotNull(
                "$problems ${if (problems == 1) "problem" else "problems"}",
                scales.joinToString(" / ").takeIf {
                    it.isNotEmpty()
                },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

/** A problem as a flat line: its tape, grade, name and how it's going. */
@Composable
private fun ProblemLine(item: ProblemWithStats, onOpen: (Long) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val stats = item.stats
    val sent = stats?.sent == true
    val tape = item.problem.gradeColour?.let { com.hardtekpt.crux.ui.components.input.argb(it) }
        ?: item.problem.tape?.let { CruxTheme.colors.tape[it.coerceIn(CruxTheme.colors.tape.indices)] }
        ?: if (sent) CruxTheme.colors.success else colors.outline
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(item.problem.id) }
            .padding(vertical = 10.dp)
            .testTag("problem_row"),
    ) {
        Box(Modifier.width(4.dp).height(32.dp).clip(RoundedCornerShape(2.dp)).background(tape))
        Text(item.problem.grade, style = CruxTheme.type.grade, modifier = Modifier.width(44.dp), maxLines = 1)
        Column(Modifier.weight(1f)) {
            Text(
                item.problem.name + if (item.problem.retired) " (retired)" else "",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    stats == null -> "Not tried yet"
                    stats.sent -> "${if (stats.attempts == 1) "1 go" else "${stats.attempts} goes"} · sent ${stats.firstSend!!.shortLabel()}"
                    else -> "Project · ${if (stats.attempts == 1) "1 go" else "${stats.attempts} goes"}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (stats != null && !sent) colors.secondary else colors.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
    }
}

@Composable
private fun ProblemRow(item: ProblemWithStats, detail: PlaceDetail, onOpen: (Long) -> Unit) {
    val stats = item.stats
    val state = when {
        stats?.sent == true -> GradeState.Sent
        else -> GradeState.Attempted
    }
    CruxListRow(
        title = item.problem.name + if (item.problem.retired) " (retired)" else "",
        supporting = problemLine(item, detail.copy(areas = emptyList())),
        leading = {
            Box(contentAlignment = Alignment.TopEnd) {
                GradeBadge(item.problem.grade, state)
                item.problem.tape?.let { TapeDot(it) }
            }
        },
        trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
        onClick = { onOpen(item.problem.id) },
        modifier = Modifier.testTag("problem_row"),
    )
}

@Composable
private fun AreaMenu(area: Area, onEdit: () -> Unit, onReset: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "${area.name} options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Edit") }, onClick = {
                open = false
                onEdit()
            })
            DropdownMenuItem(text = { Text("Reset (retire its problems)") }, onClick = {
                open = false
                onReset()
            })
            DropdownMenuItem(text = { Text("Delete") }, onClick = {
                open = false
                onDelete()
            })
        }
    }
}

/**
 * Add or edit a wall: name, angle and an optional image, a photo of the wall or a map of the
 * gym with it marked. Picked from the gallery or taken with the camera.
 */
@Composable
private fun AreaDialog(
    area: Area?,
    sections: List<com.hardtekpt.crux.data.model.Section>,
    initialSection: com.hardtekpt.crux.data.model.Section?,
    fallbackType: PlaceType,
    viewModel: PlaceDetailViewModel,
    onSave: (String, Int?, String?, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var sectionId by rememberSaveable { mutableStateOf(initialSection?.id) }
    val type = sections.firstOrNull { it.id == sectionId }?.type ?: fallbackType
    val label = type.areaLabel
    val isBoard = type == PlaceType.BOARD
    var name by rememberSaveable { mutableStateOf(area?.name.orEmpty()) }
    var angle by rememberSaveable { mutableStateOf(area?.angle?.toString().orEmpty()) }
    var image by rememberSaveable { mutableStateOf(area?.imagePath) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    // A mixed place says "area" in the title; the chips say which kind it is.
    val noun = if (sections.size > 1) "area" else label.lowercase()

    // A new image replaces one added earlier in this dialog; the wall's saved image is only
    // dropped once the dialog is saved.
    val accept = { uri: Uri ->
        loading = true
        failed = false
        viewModel.importImage(
            uri,
            onReady = { file ->
                if (image != area?.imagePath) viewModel.discardImage(image)
                image = file
                loading = false
            },
            onFailed = {
                loading = false
                failed = true
            },
        )
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(accept) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) captureUri?.let { accept(Uri.parse(it)) }
    }
    val cancel = {
        if (image != area?.imagePath) viewModel.discardImage(image)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = cancel,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(if (area == null) "Add $noun" else "Edit $noun", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                // A mixed place: say which part of it this is.
                if (sections.size > 1) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
                        modifier = Modifier.testTag("area_sections"),
                    ) {
                        sections.forEach { section ->
                            CruxFilterChip(section.name, section.id == sectionId, { sectionId = section.id }, Modifier.testTag("area_section_${section.name}"))
                        }
                    }
                }
                CruxTextField(
                    label = "Name",
                    value = name,
                    onValueChange = { name = it.take(40) },
                    placeholder = if (isBoard) "Benchmarks" else "Cave",
                    modifier = Modifier.testTag("field_area_name"),
                )
                CruxTextField(
                    label = "Angle",
                    value = angle,
                    onValueChange = { angle = it.filter(Char::isDigit).take(2) },
                    helper = "degrees, optional",
                    keyboardType = KeyboardType.Number,
                )
                Eyebrow("Image · optional", Modifier.padding(top = CruxTheme.space.s2))
                Text(
                    "A photo of the $noun, or a map of the place with it marked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val current = image
                when {
                    loading -> Text("Adding image…", style = MaterialTheme.typography.bodyMedium)

                    current != null -> {
                        val preview = rememberLocalImage(areaImageFile(current), 800)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .testTag("area_image_preview"),
                        ) {
                            preview?.let {
                                Image(it, contentDescription = "$label image", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
                            CruxButton("Replace", {
                                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }, variant = CruxButtonVariant.Text, size = CruxButtonSize.Small)
                            CruxButton(
                                "Remove",
                                {
                                    if (image != area?.imagePath) viewModel.discardImage(image)
                                    image = null
                                },
                                variant = CruxButtonVariant.Text,
                                size = CruxButtonSize.Small,
                                modifier = Modifier.testTag("remove_area_image"),
                            )
                        }
                    }

                    else -> Row(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
                        CruxButton(
                            "Choose",
                            { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            variant = CruxButtonVariant.Outlined,
                            size = CruxButtonSize.Small,
                            icon = Icons.Rounded.Image,
                            modifier = Modifier.testTag("choose_area_image"),
                        )
                        CruxButton(
                            "Take photo",
                            {
                                val uri = viewModel.captureUri()
                                captureUri = uri.toString()
                                camera.launch(uri)
                            },
                            variant = CruxButtonVariant.Outlined,
                            size = CruxButtonSize.Small,
                            icon = Icons.Rounded.PhotoCamera,
                            modifier = Modifier.testTag("take_area_photo"),
                        )
                    }
                }
                if (failed) {
                    Text("That image couldn't be added. Try another.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(name, angle.toIntOrNull(), image, sectionId)
            }, enabled = name.isNotBlank() && !loading, modifier = Modifier.testTag("save_area")) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = cancel) { Text("Cancel") } },
    )
}

/** Small card used on the problem page to sum up progress. */
@Composable
internal fun StatLine(label: String, value: String, modifier: Modifier = Modifier) {
    CruxCard(modifier = modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = CruxTheme.type.metricMedium)
    }
}
