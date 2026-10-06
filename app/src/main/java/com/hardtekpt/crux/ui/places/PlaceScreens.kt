package com.hardtekpt.crux.ui.places

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import com.hardtekpt.crux.data.images.AreaImageStore
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.ImageThumbnail
import com.hardtekpt.crux.ui.components.ImageViewer
import com.hardtekpt.crux.ui.components.areaImageFile
import com.hardtekpt.crux.ui.components.rememberLocalImage
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Place
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
import com.hardtekpt.crux.ui.components.CruxFilterChip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.PlaceRepository
import com.hardtekpt.crux.data.model.Area
import com.hardtekpt.crux.data.model.PlaceDetail
import com.hardtekpt.crux.data.model.PlaceSummary
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.model.ProblemWithStats
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.GradeBadge
import com.hardtekpt.crux.ui.components.GradeState
import com.hardtekpt.crux.ui.components.InlineEmptyState
import com.hardtekpt.crux.ui.journal.TapeDot
import com.hardtekpt.crux.ui.journal.problemLine
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.relativeLabel
import com.hardtekpt.crux.ui.shortLabel
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

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
    val shown = places?.filter { filter == null || it.place.type == filter }?.sortedByDescending { it.place.favourite }
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
        val today = LocalDate.now()
        CruxListRow(
            title = summary.place.name,
            supporting = listOfNotNull(
                summary.place.type.label,
                summary.place.location,
                when (summary.climbs) {
                    0 -> "no climbs yet"
                    1 -> "1 climb"
                    else -> "${summary.climbs} climbs"
                },
                summary.lastVisit?.let { "last ${it.relativeLabel(today).lowercase()}" },
            ).joinToString(" · "),
            leading = { Icon(placeIcon(summary.place.type), contentDescription = summary.place.type.label) },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (summary.place.favourite) {
                        Icon(Icons.Rounded.Star, contentDescription = "Favourite", tint = MaterialTheme.colorScheme.primary)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                }
            },
            onClick = { onOpen(summary.place.id) },
            modifier = Modifier.testTag("place_row"),
        )
    }
}

fun placeIcon(type: PlaceType) = when (type) {
    PlaceType.GYM -> Icons.Rounded.Place
    PlaceType.CRAG -> Icons.Rounded.Landscape
    PlaceType.BOARD -> Icons.Rounded.GridView
}

// ---- Place page ----------------------------------------------------------------------

@HiltViewModel
class PlaceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PlaceRepository,
    private val images: AreaImageStore,
) : ViewModel() {
    val placeId: Long = savedStateHandle.get<Long>("placeId") ?: 0L

    val detail: StateFlow<PlaceDetail?> = repository.observePlaceDetail(placeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Saves a wall; a replaced or removed image file is deleted. */
    fun saveArea(areaId: Long, name: String, angle: Int?, image: String?, previousImage: String?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.saveArea(placeId, areaId, name, angle, image)
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
    onLogHere: (Long) -> Unit,
    viewModel: PlaceDetailViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    var editingArea by remember { mutableStateOf<Area?>(null) }
    var viewingArea by remember { mutableStateOf<Area?>(null) }
    var addingArea by rememberSaveable { mutableStateOf(false) }
    var showRetired by rememberSaveable { mutableStateOf(false) }
    val space = CruxTheme.space
    val place = detail?.place

    Column(Modifier.fillMaxSize().testTag("screen_PlaceDetail")) {
        CruxTopAppBar(
            title = place?.name.orEmpty(),
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
        val areaLabel = current.place.type.areaLabel
        LazyColumn(
            contentPadding = PaddingValues(start = space.s4, end = space.s4, bottom = space.s4 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s3),
            modifier = Modifier.testTag("place_list"),
        ) {
            item {
                Text(
                    listOfNotNull(current.place.type.label, current.place.location, scaleLine(current)).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                    CruxButton(text = "Log here", onClick = { onLogHere(current.place.id) }, icon = Icons.Rounded.Add, modifier = Modifier.testTag("log_here"))
                    CruxButton(
                        text = "Add problem",
                        onClick = { onNewProblem(current.place.id) },
                        variant = CruxButtonVariant.Outlined,
                        modifier = Modifier.testTag("add_problem"),
                    )
                }
            }

            val visible = current.problems.filter { showRetired || !it.problem.retired }
            val groups: List<Pair<Area?, List<ProblemWithStats>>> =
                current.areas.map { area -> area to visible.filter { it.problem.areaId == area.id } } +
                    listOf<Pair<Area?, List<ProblemWithStats>>>(null to visible.filter { it.problem.areaId == null })

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
                            area?.let { a -> listOfNotNull(a.name, a.angle?.let { "$it°" }, a.resetDate?.let { "reset ${it.shortLabel()}" }).joinToString(" · ") }
                                ?: "No ${areaLabel.lowercase()}",
                            Modifier.weight(1f),
                        )
                        if (area != null) AreaMenu(area, onEdit = { editingArea = area }, onReset = { viewModel.resetArea(area.id) }, onDelete = { viewModel.deleteArea(area) })
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
                items(problems, key = { "problem_${it.problem.id}" }) { item -> ProblemRow(item, current, onOpenProblem) }
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
            label = place?.type?.areaLabel ?: "Wall",
            isBoard = place?.type == PlaceType.BOARD,
            viewModel = viewModel,
            onSave = { name, angle, image ->
                viewModel.saveArea(editingArea?.id ?: 0, name, angle, image, editingArea?.imagePath)
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
            DropdownMenuItem(text = { Text("Edit") }, onClick = { open = false; onEdit() })
            DropdownMenuItem(text = { Text("Reset (retire its problems)") }, onClick = { open = false; onReset() })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { open = false; onDelete() })
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
    label: String,
    isBoard: Boolean,
    viewModel: PlaceDetailViewModel,
    onSave: (String, Int?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(area?.name.orEmpty()) }
    var angle by rememberSaveable { mutableStateOf(area?.angle?.toString().orEmpty()) }
    var image by rememberSaveable { mutableStateOf(area?.imagePath) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    val noun = label.lowercase()

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
                            CruxButton("Replace", { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, variant = CruxButtonVariant.Text, size = CruxButtonSize.Small)
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
            TextButton(onClick = { onSave(name, angle.toIntOrNull(), image) }, enabled = name.isNotBlank() && !loading, modifier = Modifier.testTag("save_area")) {
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
