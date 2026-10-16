package com.hardtekpt.crux.ui.datasets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardtekpt.crux.data.ClashChoice
import com.hardtekpt.crux.data.Dataset
import com.hardtekpt.crux.data.DatasetClimb
import com.hardtekpt.crux.data.DatasetFiles
import com.hardtekpt.crux.data.convertGrade
import com.hardtekpt.crux.data.isSentIn
import com.hardtekpt.crux.data.model.BoardModel
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.boardSizeLabel
import com.hardtekpt.crux.data.prefs.GradeScales
import com.hardtekpt.crux.ui.components.ActionSheet
import com.hardtekpt.crux.ui.components.ClimbGrade
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxSegmentedButtons
import com.hardtekpt.crux.ui.components.CruxSheet
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.EmptyState
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.SheetButton
import com.hardtekpt.crux.ui.components.input.GradeStrip
import com.hardtekpt.crux.ui.components.listItemMotion
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.places.BoardSheet
import com.hardtekpt.crux.ui.places.BoardSizeSheet
import com.hardtekpt.crux.ui.places.ScaleChoice
import com.hardtekpt.crux.ui.theme.CruxTheme

// ---- Datasets page -------------------------------------------------------------------------

/** The Datasets page's two tabs. */
enum class DatasetsTab(val label: String) { DATASETS("Datasets"), PLAYLISTS("Playlists") }

/**
 * Your lists of climbs to pick from: a home board's problems, a spray wall, a gym circuit, and
 * your playlists of them. + makes a dataset or a playlist, or imports a CSV, Crux dataset or Crux
 * playlist file. Tap one to open it.
 */
@Composable
fun DatasetsScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit = {},
    viewModel: DatasetsViewModel = hiltViewModel(),
    initialTab: DatasetsTab = DatasetsTab.DATASETS,
    onOpenCatalogue: (Long) -> Unit = {},
) {
    val catalogueState by viewModel.catalogue.collectAsStateWithLifecycle()
    val boards by viewModel.boards.collectAsStateWithLifecycle()
    var catalogueSheet by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(catalogueState) {
        if (catalogueState is com.hardtekpt.crux.data.CatalogueImporter.State.Reading) catalogueSheet = true
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importDraft by viewModel.importer.draft.collectAsStateWithLifecycle()
    val importError by viewModel.importer.error.collectAsStateWithLifecycle()
    val playlistImported by viewModel.importer.playlist.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var creatingPlaylist by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.openFile(it) } }
    val openPicker = { pickFile.launch(arrayOf("*/*")) }
    val space = CruxTheme.space
    Column(Modifier.fillMaxSize().testTag("screen_Datasets")) {
        CruxTopAppBar(title = "Datasets", onBack = onBack) {
            IconButton(onClick = { adding = true }, modifier = Modifier.testTag("datasets_add")) {
                Icon(Icons.Rounded.Add, contentDescription = "New dataset or import")
            }
        }
        if (!uiState.isLoading && (uiState.datasets.isNotEmpty() || uiState.playlists.isNotEmpty())) {
            CruxSegmentedButtons(
                options = DatasetsTab.entries,
                selected = tab,
                label = { t ->
                    val count = if (t == DatasetsTab.DATASETS) uiState.datasets.size else uiState.playlists.size
                    "${t.label} · $count"
                },
                onSelect = { tab = it },
                modifier = Modifier.padding(horizontal = space.s4, vertical = space.s2).testTag("datasets_tabs"),
            )
        }
        if (!uiState.isLoading && tab == DatasetsTab.PLAYLISTS) {
            LazyColumn(
                contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s1, bottom = space.s4 + LocalNavBarClearance.current),
                verticalArrangement = Arrangement.spacedBy(space.s2),
                modifier = Modifier.testTag("playlists_list"),
            ) {
                if (uiState.playlists.isEmpty()) {
                    item(key = "no_playlists") {
                        Column(verticalArrangement = Arrangement.spacedBy(space.s3), modifier = Modifier.padding(vertical = space.s4)) {
                            Text(
                                "A playlist is an ordered pick of climbs from your datasets, like Tonight. Climb it in a session, one climb after another.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            CruxButton("New playlist", { creatingPlaylist = true }, icon = Icons.Rounded.Add, modifier = Modifier.testTag("playlists_new"))
                        }
                    }
                }
                items(uiState.playlists, key = { it.id }) { list -> PlaylistRow(list) { onOpenPlaylist(list.id) } }
            }
        } else if (!uiState.isLoading && uiState.datasets.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Rounded.ViewList,
                headline = "No datasets yet",
                sentence = "Keep lists of climbs for your home board, a spray wall or a gym circuit. You'll pick from them when you log a climb.",
                action = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(space.s2)) {
                        CruxButton("New dataset", { creating = true }, icon = Icons.Rounded.Add, modifier = Modifier.testTag("datasets_new"))
                        CruxButton("Import a file", openPicker, variant = CruxButtonVariant.Outlined, modifier = Modifier.testTag("datasets_import"))
                    }
                },
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s1, bottom = space.s4 + LocalNavBarClearance.current),
                verticalArrangement = Arrangement.spacedBy(space.s2),
                modifier = Modifier.testTag("datasets_list"),
            ) {
                // A catalogue being imported shows here too, so leaving its sheet loses nothing.
                (catalogueState as? com.hardtekpt.crux.data.CatalogueImporter.State.Importing)?.let { importing ->
                    item(key = "importing") {
                        CruxListRow(
                            title = "Importing ${importing.board.label}",
                            supporting = "${"%,d".format(importing.done)} of ${"%,d".format(importing.total)} climbs read",
                            onClick = { catalogueSheet = true },
                            modifier = Modifier.testTag("catalogue_importing"),
                        )
                    }
                }
                val catalogues = uiState.datasets.filter { it.isCatalogue }
                if (catalogues.isNotEmpty()) {
                    item(key = "catalogues") { Eyebrow("Board catalogues", Modifier.padding(bottom = space.s1)) }
                    items(catalogues, key = { "c${it.id}" }) { dataset ->
                        CruxListRow(
                            title = dataset.name,
                            supporting = datasetLine(dataset),
                            leading = { DatasetBadge(dataset) },
                            trailing = { Text("read-only", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { onOpenCatalogue(dataset.id) },
                            modifier = Modifier.testTag("catalogue_row"),
                        )
                    }
                }
                item(key = "yours") { Eyebrow("Yours", Modifier.padding(top = space.s2, bottom = space.s1)) }
                items(uiState.datasets.filter { !it.isCatalogue }, key = { it.id }) { dataset ->
                    CruxListRow(
                        title = dataset.name,
                        supporting = datasetLine(dataset),
                        leading = { DatasetBadge(dataset) },
                        onClick = { onOpen(dataset.id) },
                        modifier = Modifier.testTag("dataset_row"),
                    )
                }
                item(key = "later") {
                    Text(
                        "Import a Kilter or MoonBoard catalogue from a Boardsesh snapshot file with + → Import a file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = space.s2),
                    )
                }
            }
        }
    }
    if (adding) {
        MenuSheet("Datasets", { adding = false }) {
            CruxListRow(
                title = "New dataset",
                supporting = "Name it, then add climbs",
                onClick = {
                    adding = false
                    creating = true
                },
                modifier = Modifier.testTag("menu_new_dataset"),
            )
            CruxListRow(
                title = "New playlist",
                supporting = "Name it, then add climbs from your datasets",
                onClick = {
                    adding = false
                    creatingPlaylist = true
                },
                modifier = Modifier.testTag("menu_new_playlist"),
            )
            CruxListRow(
                title = "Import a file",
                supporting = "A CSV, a Climb Logger dataset or playlist file, or a Boardsesh board catalogue",
                onClick = {
                    adding = false
                    openPicker()
                },
                modifier = Modifier.testTag("menu_import_dataset"),
            )
        }
    }
    if (creatingPlaylist) {
        PlaylistDetailsSheet(
            title = "New playlist",
            confirm = "Create",
            initialName = "",
            initialNote = "",
            onSave = { name, note ->
                creatingPlaylist = false
                viewModel.createPlaylist(name, note, onOpenPlaylist)
            },
            onDismiss = { creatingPlaylist = false },
        )
    }
    playlistImported?.let { imported ->
        AlertDialog(
            onDismissRequest = viewModel.importer::playlistSeen,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Imported ${imported.name}", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    if (imported.matched == imported.total) {
                        "All ${imported.total} climbs were found in your datasets."
                    } else {
                        "${imported.matched} of ${imported.total} climbs were found in your datasets. The rest wait, greyed, until their dataset is here."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importer.playlistSeen()
                    onOpenPlaylist(imported.id)
                }, modifier = Modifier.testTag("playlist_imported_open")) { Text("Open") }
            },
        )
    }
    if (creating) {
        DatasetDetailsSheet(
            initial = Dataset(name = ""),
            title = "New dataset",
            confirm = "Create",
            onSave = { dataset ->
                creating = false
                viewModel.create(dataset, onOpen)
            },
            onDismiss = { creating = false },
        )
    }
    importDraft?.let { draft ->
        ImportSheet(
            draft = draft,
            datasets = uiState.datasets,
            settings = uiState.scales,
            canPickInto = true,
            onChange = viewModel.importer::update,
            onImport = { scaleFor -> viewModel.importer.import(scaleFor) },
            onDone = { id ->
                viewModel.importer.dismiss()
                onOpen(id)
            },
            onDismiss = viewModel.importer::dismiss,
        )
    }
    importError?.let { message -> ErrorDialog(message, viewModel.importer::clearError) }
    if (catalogueSheet && catalogueState !is com.hardtekpt.crux.data.CatalogueImporter.State.Idle &&
        catalogueState !is com.hardtekpt.crux.data.CatalogueImporter.State.Failed
    ) {
        CatalogueImportSheet(
            state = catalogueState,
            yours = boards,
            onImport = viewModel::importCatalogue,
            onOpen = { id ->
                catalogueSheet = false
                viewModel.dismissCatalogue()
                onOpenCatalogue(id)
            },
            onDismiss = {
                catalogueSheet = false
                if (catalogueState !is com.hardtekpt.crux.data.CatalogueImporter.State.Importing) viewModel.dismissCatalogue()
            },
        )
    }
    (catalogueState as? com.hardtekpt.crux.data.CatalogueImporter.State.Failed)?.let { failed ->
        ErrorDialog(failed.message) { viewModel.dismissCatalogue() }
    }
}

/** "Kilter Original 12×12 · 12 climbs · V scale". */
private fun datasetLine(dataset: Dataset): String = listOfNotNull(
    dataset.boardLabel,
    "${"%,d".format(dataset.size)} ${if (dataset.size == 1) "climb" else "climbs"}",
    dataset.boulderScale?.label,
).joinToString(" · ")

/** A square with the board's initials in its maker's colour: Kilter purple, MoonBoard amber, others teal. */
@Composable
internal fun DatasetBadge(dataset: Dataset, size: androidx.compose.ui.unit.Dp = 36.dp) {
    val (fill, ink) = when (dataset.board?.maker) {
        "Kilter" -> Color(0xFF43307A) to Color(0xFFE8DDFF)
        "MoonBoard" -> Color(0xFF6B3D0C) to Color(0xFFFFE0A8)
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    val initials = dataset.name.split(' ', '-', '_').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "·" }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(fill)) {
        Text(initials, style = CruxTheme.type.code, color = ink)
    }
}

// ---- One dataset ---------------------------------------------------------------------------

/**
 * A dataset's climbs, searchable by name, setter and note and sorted by grade, name or newest.
 * Add climb opens the climb sheet; ⋯ holds Edit details, Import climbs, Export and Delete.
 */
@Composable
fun DatasetScreen(onBack: () -> Unit, onLogClimb: (Long) -> Unit = {}, viewModel: DatasetViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    // Long-press picks climbs to add to a playlist together.
    var selected by rememberSaveable { mutableStateOf(emptyList<Long>()) }
    var addingTo by remember { mutableStateOf<List<Long>?>(null) }
    val importDraft by viewModel.importer.draft.collectAsStateWithLifecycle()
    val importError by viewModel.importer.error.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(ClimbSort.GRADE) }
    var filtering by rememberSaveable { mutableStateOf(false) }
    var hideSent by rememberSaveable { mutableStateOf(false) }
    var gradeFrom by rememberSaveable { mutableStateOf<Int?>(null) }
    var gradeTo by rememberSaveable { mutableStateOf<Int?>(null) }
    var menu by rememberSaveable { mutableStateOf(false) }
    var editingDetails by rememberSaveable { mutableStateOf(false) }
    var exporting by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmClimbDelete by remember { mutableStateOf<DatasetClimb?>(null) }
    var editing by remember { mutableStateOf<DatasetClimb?>(null) }
    var addingClimb by rememberSaveable { mutableStateOf(false) }
    var exportCrux by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val pickFile =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.importer.open(it, viewModel.datasetId) } }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri -> uri?.let { viewModel.export(it, exportCrux) } }
    val space = CruxTheme.space

    // Gone (deleted here or elsewhere): step back.
    var seen by rememberSaveable { mutableStateOf(false) }
    val dataset = uiState.dataset
    if (dataset != null) seen = true
    if (seen && dataset == null && !uiState.isLoading) LaunchedEffect(Unit) { onBack() }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        viewModel.messageShown()
        snackbar.showSnackbar(text, duration = SnackbarDuration.Short)
    }
    LaunchedEffect(undo) {
        val climb = undo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar("Deleted ${climb.name}", actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.clearUndo()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().testTag("screen_Dataset")) {
            if (selected.isNotEmpty()) {
                CruxTopAppBar(title = "${selected.size} selected", onBack = { selected = emptyList() }) {
                    TextButton(onClick = { addingTo = selected }, modifier = Modifier.testTag("selection_add_to_playlist")) { Text("Add to playlist") }
                }
            } else {
                CruxTopAppBar(title = dataset?.name.orEmpty(), onBack = onBack) {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("dataset_menu")) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Dataset options")
                    }
                }
            }
            val ds = dataset ?: return@Column
            val range = GradeRange(datasetScale(ds.climbs), gradeFrom, gradeTo)
            val climbs = remember(ds, query, sort, gradeFrom, gradeTo, hideSent, uiState.sent) {
                sorted(unsent(gradeFiltered(filtered(ds.climbs, query), range), uiState.sent, hideSent), sort)
            }
            LazyColumn(
                contentPadding = PaddingValues(start = space.s4, end = space.s4, bottom = space.s4 + 72.dp + LocalNavBarClearance.current),
                verticalArrangement = Arrangement.spacedBy(space.s2),
                modifier = Modifier.weight(1f).testTag("dataset_climbs"),
            ) {
                item(key = "line") {
                    Text(datasetLine(ds), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item(key = "search") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                        CruxTextField(
                            label = "",
                            value = query,
                            onValueChange = { query = it.take(60) },
                            placeholder = "Search name, setter or note",
                            modifier = Modifier.weight(1f).testTag("dataset_search"),
                        )
                        if (range.scale != null || ds.climbs.isNotEmpty()) {
                            FiltersButton(
                                active = listOf(range.active, hideSent).count { it },
                                onClick = { filtering = true },
                                modifier = Modifier.testTag("dataset_open_filters"),
                            )
                        }
                    }
                }
                if (range.active || hideSent) {
                    item(key = "applied") {
                        Text(
                            listOfNotNull(
                                range.label()?.let {
                                    "Grades $it"
                                },
                                "hiding sent climbs".takeIf { hideSent },
                            ).joinToString(" · ").replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("dataset_grade_applied"),
                        )
                    }
                }
                // Add sits with the list it adds to, as New plan does on Train; filled stays for the
                // nav bar's Log.
                item(key = "add") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Eyebrow("Climbs · ${ds.climbs.size}", Modifier.weight(1f))
                        CruxButton(
                            "Add climb",
                            { addingClimb = true },
                            variant = CruxButtonVariant.Tonal,
                            icon = Icons.Rounded.Add,
                            modifier = Modifier.testTag("dataset_add_climb"),
                        )
                    }
                }
                item(key = "sort") {
                    Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                        ClimbSort.entries.forEach { option ->
                            CruxFilterChip(option.label, sort == option, { sort = option }, Modifier.testTag("sort_${option.name}"))
                        }
                    }
                }
                if (ds.climbs.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "No climbs yet. Add one, or import a CSV from ⋯.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = space.s4),
                        )
                    }
                } else if (climbs.isEmpty()) {
                    item(key = "nothing") {
                        Text(
                            "Nothing matches.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = space.s4),
                        )
                    }
                }
                items(climbs, key = { it.id }) { climb ->
                    CruxListRow(
                        title = climb.name,
                        supporting = listOfNotNull(
                            climb.setter,
                            climb.note?.lineSequence()?.firstOrNull(),
                            "Route".takeIf {
                                climb.discipline ==
                                    Discipline.ROUTE
                            },
                        )
                            .joinToString(" · ").ifEmpty { if (climb.gradeLabel == null) "no grade yet" else null },
                        leading = { ClimbGrade(climb.gradeLabel) },
                        selected = climb.id in selected,
                        onClick = {
                            when {
                                selected.isEmpty() -> editing = climb
                                climb.id in selected -> selected = selected - climb.id
                                else -> selected = selected + climb.id
                            }
                        },
                        onLongClick = { selected = if (climb.id in selected) selected - climb.id else selected + climb.id },
                        modifier = listItemMotion().testTag("dataset_climb"),
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = space.s4 + LocalNavBarClearance.current))
    }

    val ds = dataset ?: return
    if (menu) {
        MenuSheet(ds.name, { menu = false }) {
            CruxListRow("Edit details", supporting = "Name, board and grades", onClick = {
                menu = false
                editingDetails = true
            }, modifier = Modifier.testTag("dataset_edit_details"))
            CruxListRow("Import climbs", supporting = "Add from a CSV or Climb Logger file", onClick = {
                menu = false
                pickFile.launch(arrayOf("text/*", "application/json", "application/octet-stream"))
            }, modifier = Modifier.testTag("dataset_import"))
            CruxListRow("Export", supporting = "CSV or Climb Logger dataset file", onClick = {
                menu = false
                exporting = true
            }, modifier = Modifier.testTag("dataset_export"))
            CruxListRow("Delete dataset", supporting = "Asks first", onClick = {
                menu = false
                confirmDelete = true
            }, modifier = Modifier.testTag("dataset_delete"))
        }
    }
    if (exporting) {
        MenuSheet("Export ${ds.name}", { exporting = false }) {
            CruxListRow("CSV", supporting = "name, grade, type, setter, note; opens in any spreadsheet", onClick = {
                exporting = false
                exportCrux = false
                saveFile.launch("${fileBase(ds.name)}.csv")
            }, modifier = Modifier.testTag("export_csv"))
            CruxListRow("Climb Logger dataset file", supporting = "Keeps the board and grades; imports straight back into Climb Logger", onClick = {
                exporting = false
                exportCrux = true
                saveFile.launch("${fileBase(ds.name)}.crux-dataset.json")
            }, modifier = Modifier.testTag("export_crux"))
        }
    }
    if (editingDetails) {
        DatasetDetailsSheet(
            initial = ds,
            title = "Edit details",
            confirm = "Save",
            onSave = {
                editingDetails = false
                viewModel.saveDetails(it)
            },
            onDismiss = { editingDetails = false },
        )
    }
    if (filtering) {
        val ds = uiState.dataset
        if (ds != null) {
            val all = GradeRange(datasetScale(ds.climbs), gradeFrom, gradeTo)
            GradeFilterSheet(
                range = all,
                shown = unsent(gradeFiltered(filtered(ds.climbs, query), all), uiState.sent, hideSent).size,
                hideSent = hideSent,
                onHideSent = { hideSent = it },
                bounds = gradeBounds(ds.climbs, all.scale),
                onRange = {
                    gradeFrom = it.from
                    gradeTo = it.to
                },
                onDismiss = { filtering = false },
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Delete ${ds.name}?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "Its ${ds.climbs.size} ${if (ds.climbs.size == 1) "climb goes" else "climbs go"} too. Climbs you logged in Climb Logger are never touched.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteDataset(onBack)
                }, modifier = Modifier.testTag("confirm_delete_dataset")) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
    val sheetClimb = editing
    if (addingClimb || sheetClimb != null) {
        ClimbSheet(
            initial = sheetClimb,
            dataset = ds,
            settings = uiState.scales,
            onSave = { climb ->
                addingClimb = false
                editing = null
                viewModel.saveClimb(climb)
            },
            onDelete = sheetClimb?.let { climb ->
                {
                    editing = null
                    // A climb with a note asks first; otherwise it goes, with Undo.
                    if (climb.note.isNullOrBlank()) viewModel.deleteClimb(climb) else confirmClimbDelete = climb
                }
            },
            onDismiss = {
                addingClimb = false
                editing = null
            },
            onLog = sheetClimb?.let { climb ->
                {
                    editing = null
                    onLogClimb(climb.id)
                }
            },
            onPlaylist = sheetClimb?.let { climb ->
                {
                    editing = null
                    addingTo = listOf(climb.id)
                }
            },
        )
    }
    addingTo?.let { ids ->
        AddToPlaylistSheet(
            count = ids.size,
            playlists = uiState.playlists,
            onPick = { id ->
                addingTo = null
                selected = emptyList()
                viewModel.addToPlaylist(id, null, ids)
            },
            onNew = { name ->
                addingTo = null
                selected = emptyList()
                viewModel.addToPlaylist(null, name, ids)
            },
            onDismiss = { addingTo = null },
        )
    }
    confirmClimbDelete?.let { climb ->
        AlertDialog(
            onDismissRequest = { confirmClimbDelete = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Delete ${climb.name}?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("Its note goes with it.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClimbDelete = null
                    viewModel.deleteClimb(climb)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClimbDelete = null }) { Text("Keep") } },
        )
    }
    importDraft?.let { draft ->
        ImportSheet(
            draft = draft,
            datasets = uiState.datasets,
            settings = uiState.scales,
            canPickInto = false,
            onChange = viewModel.importer::update,
            onImport = { scaleFor -> viewModel.importer.import(scaleFor) },
            onDone = { viewModel.importer.dismiss() },
            onDismiss = viewModel.importer::dismiss,
        )
    }
    importError?.let { message -> ErrorDialog(message, viewModel.importer::clearError) }
}

private fun fileBase(name: String) = name.trim().replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').lowercase().ifEmpty { "dataset" }

/** Climbs whose name, setter or note has every word of [query]. */
internal fun filtered(climbs: List<DatasetClimb>, query: String): List<DatasetClimb> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return climbs
    return climbs.filter { climb ->
        val text = listOfNotNull(climb.name, climb.setter, climb.note).joinToString(" ").lowercase()
        words.all { it in text }
    }
}

/** The scale most of a dataset's graded climbs are in, which its grade filter reads in; null when none is graded. */
internal fun datasetScale(climbs: List<DatasetClimb>): GradeScale? =
    climbs.mapNotNull { it.gradeScale?.takeIf { s -> !s.isLocal && it.gradeIndex != null } }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

/** [climbs] without the ones you have sent, when [hide] is on. */
internal fun unsent(climbs: List<DatasetClimb>, sent: Set<String>, hide: Boolean): List<DatasetClimb> =
    if (hide) climbs.filter { !it.isSentIn(sent) } else climbs

/** The easiest and hardest grade in the dataset, as indices in [scale]; the slider spans just these. */
internal fun gradeBounds(climbs: List<DatasetClimb>, scale: GradeScale?): IntRange? {
    scale ?: return null
    val indexes = climbs.mapNotNull { c -> c.gradeScale?.let { s -> c.gradeIndex?.let { convertGrade(s, it, scale) } } }
    return if (indexes.isEmpty() || indexes.min() == indexes.max()) null else indexes.min()..indexes.max()
}

/** Climbs in [range], their grades read in its scale (converted when they are in another); an ungraded climb is out while a range is set. */
internal fun gradeFiltered(climbs: List<DatasetClimb>, range: GradeRange): List<DatasetClimb> {
    val scale = range.scale ?: return climbs
    if (!range.active) return climbs
    return climbs.filter { climb ->
        val from = climb.gradeScale ?: return@filter false
        val index = climb.gradeIndex ?: return@filter false
        convertGrade(from, index, scale)?.let(range::contains) == true
    }
}

/** Grade (easiest first, ungraded last), name, or newest first. */
internal fun sorted(climbs: List<DatasetClimb>, sort: ClimbSort): List<DatasetClimb> = when (sort) {
    ClimbSort.GRADE -> climbs.sortedWith(compareBy<DatasetClimb>({ it.gradeIndex == null }, { it.discipline }, { it.gradeIndex }, { it.name.lowercase() }))
    ClimbSort.NAME -> climbs.sortedBy { it.name.lowercase() }
    ClimbSort.NEWEST -> climbs.sortedWith(compareByDescending<DatasetClimb> { it.createdAtMillis }.thenByDescending { it.id })
}

// ---- Sheets ----------------------------------------------------------------------------------

/** A bottom sheet of rows under a title, as the app's other menus look. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ActionSheet(title, onDismiss, Modifier.testTag("dataset_sheet")) { content() }
}

/** A dataset's name, board (with Kilter size) and grade scales, for New dataset and Edit details. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatasetDetailsSheet(initial: Dataset, title: String, confirm: String, onSave: (Dataset) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var pickingBoard by remember { mutableStateOf(false) }
    var sizing by remember { mutableStateOf(false) }
    CruxSheet(
        onDismiss = onDismiss,
        modifier = Modifier.testTag("dataset_details"),
        title = title,
        confirm = SheetButton(confirm, {
            if (draft.name.isBlank()) {
                nameError = "Give the dataset a name"
            } else {
                onSave(draft)
            }
        }, tag = "dataset_details_save"),
    ) {
        CruxTextField(
            label = "Name",
            value = draft.name,
            onValueChange = {
                draft = draft.copy(name = it.take(60))
                nameError = null
            },
            placeholder = "Garage board, Block Lab circuit",
            error = nameError,
            modifier = Modifier.testTag("field_dataset_name"),
        )
        DetailRow("Board", draft.boardLabel, "dataset_board") { pickingBoard = true }
        draft.board?.takeIf { it.needsSize }?.let { board ->
            DetailRow(
                "Size",
                listOfNotNull(
                    draft.boardSize?.takeIf {
                        it in board.sizes
                    }?.let { boardSizeLabel(it) } ?: "Pick",
                    draft.boardHolds?.label,
                ).joinToString(" · "),
                "dataset_board_size",
            ) { sizing = true }
        }
        ScaleChoice("Boulder grades", Discipline.BOULDER, draft.boulderScale, "_dataset", allowLocal = false) { draft = draft.copy(boulderScale = it) }
        ScaleChoice("Route grades", Discipline.ROUTE, draft.routeScale, "_dataset", allowLocal = false) { draft = draft.copy(routeScale = it) }
        Text(
            "Board is optional. Pick one to offer this list when you log on that board.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (pickingBoard) {
        BoardSheet(
            selected = draft.board,
            noBoardSelected = draft.board == null && !draft.customBoard,
            onPick = { picked ->
                pickingBoard = false
                draft = draft.copy(
                    board = picked,
                    customBoard = picked == null,
                    boardSize = draft.boardSize?.takeIf { picked?.sizes?.contains(it) == true },
                    boardHolds = draft.boardHolds?.takeIf { picked?.hasHolds == true },
                )
                if (picked?.needsSize == true) sizing = true
            },
            onNoBoard = {
                pickingBoard = false
                draft = draft.copy(board = null, customBoard = false, boardSize = null, boardHolds = null)
            },
            onDismiss = { pickingBoard = false },
        )
    }
    val board: BoardModel? = draft.board
    if (sizing && board != null) {
        BoardSizeSheet(
            board = board,
            size = draft.boardSize,
            holds = draft.boardHolds,
            onChange = { size, holds -> draft = draft.copy(boardSize = size, boardHolds = holds) },
            onDismiss = { sizing = false },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, tag: String, onClick: () -> Unit) {
    com.hardtekpt.crux.ui.components.SettingRow(label, value, tag, onClick)
}

/**
 * Add or edit a climb: name (a name already in the dataset warns but is allowed), Boulder or
 * Route, the grade strip (or No grade), setter and note. Delete only when editing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClimbSheet(
    initial: DatasetClimb?,
    dataset: Dataset,
    settings: GradeScales,
    onSave: (DatasetClimb) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    /** Log this climb, and add it to a playlist; only for a climb already saved. */
    onLog: (() -> Unit)? = null,
    onPlaylist: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var discipline by remember { mutableStateOf(initial?.discipline ?: Discipline.BOULDER) }
    val scale = dataset.scaleFor(discipline) ?: settings.forDiscipline(discipline)
    // A grade in another scale (the dataset's changed since) starts ungraded rather than wrong.
    var index by remember { mutableStateOf(initial?.gradeIndex?.takeIf { initial.gradeScale == scale }) }
    var setter by remember { mutableStateOf(initial?.setter.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var nameError by remember { mutableStateOf<String?>(null) }
    val duplicate =
        name.isNotBlank() && dataset.climbs.any { it.id != initial?.id && it.discipline == discipline && it.name.trim().equals(name.trim(), ignoreCase = true) }
    CruxSheet(
        onDismiss = onDismiss,
        modifier = Modifier.testTag("climb_sheet"),
        title = if (initial == null) "Add climb" else "Edit climb",
        confirm = SheetButton("Save", {
            if (name.isBlank()) {
                nameError = "Give the climb a name"
            } else {
                onSave(
                    DatasetClimb(
                        id = initial?.id ?: 0,
                        name = name,
                        discipline = discipline,
                        gradeScale = scale.takeIf { index != null },
                        gradeIndex = index,
                        setter = setter,
                        note = note,
                        createdAtMillis = initial?.createdAtMillis ?: 0,
                    ),
                )
            }
        }, tag = "climb_sheet_save"),
    ) {
        CruxTextField(
            label = "Name",
            value = name,
            onValueChange = {
                name = it.take(60)
                nameError = null
            },
            placeholder = "Roof to pinch",
            error = nameError,
            helper = "Already in this dataset".takeIf { duplicate },
            modifier = Modifier.testTag("field_climb_name"),
        )
        CruxSegmentedButtons(
            options = Discipline.entries,
            selected = discipline,
            label = { it.label },
            onSelect = {
                discipline = it
                index = null
            },
            modifier = Modifier.fillMaxWidth().testTag("climb_discipline"),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Grade · ${scale.label}", Modifier.weight(1f))
            CruxFilterChip("No grade", index == null, { index = null }, Modifier.testTag("climb_no_grade"))
        }
        GradeStrip(
            grades = scale.grades,
            selectedIndex = index ?: scale.defaultIndex,
            onSelect = { index = it },
            tagPrefix = "dataset_grade",
        )
        CruxTextField(label = "Setter", value = setter, onValueChange = {
            setter = it.take(40)
        }, placeholder = "Optional", modifier = Modifier.testTag("field_climb_setter"))
        CruxTextField(label = "Note", value = note, onValueChange = {
            note = it.take(500)
        }, placeholder = "Optional", singleLine = false, minLines = 2, modifier = Modifier.testTag("field_climb_note"))
        if (onLog != null || onPlaylist != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), modifier = Modifier.padding(top = CruxTheme.space.s2)) {
                onLog?.let {
                    CruxButton(
                        "Log this climb",
                        it,
                        icon = Icons.Rounded.Check,
                        variant = CruxButtonVariant.Tonal,
                        modifier = Modifier.weight(1f).testTag("climb_sheet_log"),
                    )
                }
                onPlaylist?.let {
                    CruxButton(
                        "Playlist",
                        it,
                        icon = Icons.Rounded.Add,
                        variant = CruxButtonVariant.Outlined,
                        modifier = Modifier.weight(1f).testTag("climb_sheet_playlist"),
                    )
                }
            }
        }
        onDelete?.let {
            TextButton(onClick = it, modifier = Modifier.testTag("climb_sheet_delete")) { Text("Delete climb", color = MaterialTheme.colorScheme.error) }
        }
    }
}

/**
 * What a file holds before anything is saved: how many climbs, what couldn't be read, where they
 * go (a new dataset, or one you have) in which scales, what happens to names already there, and
 * the first rows. Import saves; then it says what was added.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(
    draft: ImportDraft,
    datasets: List<Dataset>,
    settings: GradeScales,
    canPickInto: Boolean,
    onChange: ((ImportDraft) -> ImportDraft) -> Unit,
    onImport: ((Discipline) -> GradeScale) -> Unit,
    onDone: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val into = datasets.firstOrNull { it.id == draft.intoId }
    val scaleFor = { d: Discipline -> draft.scaleFor(d, settings, into) }
    val unknown = remember(draft, into, settings) { DatasetFiles.unknownGrades(draft.parsed.climbs, scaleFor) }
    val space = CruxTheme.space
    val result = draft.result
    CruxSheet(
        onDismiss = onDismiss,
        modifier = Modifier.testTag("import_sheet"),
        title = if (result != null) "Imported" else "Import ${draft.fileName}",
        confirm = if (result != null) {
            SheetButton("Done", { onDone(draft.intoId ?: datasets.firstOrNull { it.name == draft.name.trim() }?.id ?: 0) }, tag = "import_done")
        } else {
            SheetButton("Import", { onImport(scaleFor) }, enabled = draft.parsed.climbs.isNotEmpty(), tag = "import_confirm")
        },
    ) {
        if (result != null) {
            Text(
                listOfNotNull(
                    "${result.added} added",
                    "${result.replaced} replaced".takeIf { result.replaced > 0 },
                    "${result.skipped} already there, skipped".takeIf { result.skipped > 0 },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("import_result"),
            )
            return@CruxSheet
        }
        Text(
            listOfNotNull(
                "${draft.parsed.climbs.size} ${if (draft.parsed.climbs.size == 1) "climb" else "climbs"} found",
                "${unknown.size} ${if (unknown.size == 1) "grade" else "grades"} not recognised, kept without a grade".takeIf { unknown.isNotEmpty() },
                "${draft.parsed.skipped} ${if (draft.parsed.skipped == 1) "row" else "rows"} without a name left out".takeIf { draft.parsed.skipped > 0 },
                "columns ignored: ${draft.parsed.ignoredColumns.joinToString(", ")}".takeIf { draft.parsed.ignoredColumns.isNotEmpty() },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("import_summary"),
        )
        if (canPickInto && datasets.isNotEmpty()) {
            Eyebrow("Into", Modifier.padding(top = space.s1))
            CruxSegmentedButtons(
                options = listOf(false, true),
                selected = draft.intoId != null,
                label = { if (it) "A dataset you have" else "New dataset" },
                onSelect = { existing -> onChange { it.copy(intoId = if (existing) datasets.first().id else null) } },
                modifier = Modifier.fillMaxWidth().testTag("import_into"),
            )
        }
        if (draft.intoId == null) {
            CruxTextField(label = "Name", value = draft.name, onValueChange = { name ->
                onChange { it.copy(name = name.take(60)) }
            }, modifier = Modifier.testTag("import_name"))
            ScaleChoice("Boulder grades", Discipline.BOULDER, draft.dataset.boulderScale, "_import", allowLocal = false) { scale ->
                onChange { it.copy(dataset = it.dataset.copy(boulderScale = scale)) }
            }
            ScaleChoice("Route grades", Discipline.ROUTE, draft.dataset.routeScale, "_import", allowLocal = false) { scale ->
                onChange { it.copy(dataset = it.dataset.copy(routeScale = scale)) }
            }
        } else {
            if (canPickInto) {
                datasets.forEach { dataset ->
                    CruxListRow(
                        title = dataset.name,
                        supporting = datasetLine(dataset),
                        selected = dataset.id == draft.intoId,
                        onClick = { onChange { it.copy(intoId = dataset.id) } },
                        modifier = Modifier.testTag("import_into_${dataset.name}"),
                    )
                }
            }
            Eyebrow("A climb already there, by name and type", Modifier.padding(top = space.s1))
            Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                ClashChoice.entries.forEach { choice ->
                    CruxFilterChip(choice.label, draft.clash == choice, {
                        onChange { it.copy(clash = choice) }
                    }, Modifier.testTag("import_clash_${choice.name}"))
                }
            }
        }
        Eyebrow("Preview", Modifier.padding(top = space.s1))
        draft.parsed.climbs.take(PREVIEW_ROWS).forEach { raw ->
            val scale = scaleFor(raw.discipline)
            val index = raw.grade?.let { DatasetFiles.gradeIndex(scale, it) }
            val known = raw.grade == null || index != null
            CruxListRow(
                title = raw.name,
                supporting = if (known) raw.discipline.label else "grade \"${raw.grade}\" not recognised",
                leading = {
                    ClimbGrade(
                        index?.let(scale::label) ?: if (raw.grade == null) null else "?",
                        color = if (known) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                    )
                },
            )
        }
        if (draft.parsed.climbs.size > PREVIEW_ROWS) {
            Text(
                "and ${draft.parsed.climbs.size - PREVIEW_ROWS} more",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val PREVIEW_ROWS = 5

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("Can't import this file", style = MaterialTheme.typography.headlineSmall) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("import_error_ok")) { Text("OK") } },
    )
}
