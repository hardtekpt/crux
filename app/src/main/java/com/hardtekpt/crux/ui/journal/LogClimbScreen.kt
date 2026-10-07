package com.hardtekpt.crux.ui.journal

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.Venue
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxSegmentedButtons
import com.hardtekpt.crux.ui.components.CruxStepper
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.ImageThumbnail
import com.hardtekpt.crux.ui.components.ImageViewer
import com.hardtekpt.crux.ui.components.VideoPlayer
import com.hardtekpt.crux.ui.components.VideoThumbnail
import com.hardtekpt.crux.ui.components.input.DayStrip
import com.hardtekpt.crux.ui.components.input.EffortScale
import com.hardtekpt.crux.ui.components.input.GradeStrip
import com.hardtekpt.crux.ui.components.input.PastDayDialog
import com.hardtekpt.crux.ui.components.input.bleed
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.theme.CruxTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun LogClimbScreen(onDone: () -> Unit, viewModel: LogClimbViewModel = hiltViewModel()) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val detail by viewModel.placeDetail.collectAsStateWithLifecycle()
    LaunchedEffect(draft.saved) { if (draft.saved) onDone() }
    val whereActions = WhereActions(
        selectPlace = viewModel::selectPlace,
        createPlace = viewModel::createPlace,
        selectArea = viewModel::selectArea,
        pickProblem = viewModel::pickProblem,
        clearProblem = viewModel::clearProblem,
        setSaveAsProblem = viewModel::setSaveAsProblem,
        setAngle = viewModel::setAngle,
        setVenue = viewModel::setVenue,
        setPlaceText = viewModel::setPlace,
        selectSection = viewModel::selectSection,
    )
    LogClimbContent(
        draft = draft,
        onBack = onDone,
        where = { WhereSection(draft, places, detail, whereActions) },
        onDelete = viewModel::requestDelete,
        onDiscipline = viewModel::setDiscipline,
        onGrade = viewModel::setGrade,
        onStyle = viewModel::setStyle,
        onAttempts = viewModel::setAttempts,
        onDate = viewModel::setDate,
        onEffort = viewModel::setEffort,
        onVenue = viewModel::setVenue,
        onName = viewModel::setName,
        onPlace = viewModel::setPlace,
        onNotes = viewModel::setNotes,
        onSave = viewModel::save,
        media = {
            ClimbMedia(
                draft = draft,
                onAttach = viewModel::attachImage,
                onRemove = viewModel::removeImage,
                captureUri = viewModel::captureUri,
                onAttachVideo = viewModel::attachVideo,
                onRemoveVideo = viewModel::removeVideo,
                videoCaptureUri = viewModel::videoCaptureUri,
            )
        },
    )
    if (draft.confirmDelete) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Delete this climb?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("It is removed from your journal, bests and charts.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete, modifier = Modifier.testTag("confirm_delete")) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Keep") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogClimbContent(
    draft: LogClimbDraft,
    onBack: () -> Unit,
    onDiscipline: (Discipline) -> Unit,
    onGrade: (Int) -> Unit,
    onStyle: (AscentStyle) -> Unit,
    onAttempts: (Int) -> Unit,
    onDate: (LocalDate) -> Unit,
    onVenue: (Venue) -> Unit,
    onName: (String) -> Unit,
    onPlace: (String) -> Unit,
    onNotes: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    where: @Composable () -> Unit = {},
    onDelete: () -> Unit = {},
    onEffort: (Int?) -> Unit = {},
    media: @Composable () -> Unit = {},
    today: LocalDate = remember { LocalDate.now() },
) {
    val space = CruxTheme.space
    var pickingDate by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .testTag("screen_LogClimb"),
    ) {
        CruxTopAppBar(
            title = if (draft.isEditing) "Edit climb" else "Log climb",
            onBack = onBack,
            actions = {
                if (draft.isEditing) {
                    IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_climb")) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete climb")
                    }
                }
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.s4),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            where()
            Eyebrow("Climb", Modifier.padding(top = space.s3))
            CruxSegmentedButtons(
                options = Discipline.entries,
                selected = draft.discipline,
                label = { it.label },
                onSelect = onDiscipline,
            )

            Eyebrow("Grade · ${draft.system.name}", Modifier.padding(top = space.s3))
            GradeStrip(
                grades = draft.system.labels,
                colours = draft.system.local?.grades?.map { it.colour },
                selectedIndex = draft.gradeIndex,
                onSelect = onGrade,
                modifier = Modifier.bleed(space.s4),
            )

            Eyebrow("Style", Modifier.padding(top = space.s3))
            Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                draft.styles.forEach { style ->
                    CruxFilterChip(
                        label = style.label,
                        selected = draft.style == style,
                        onClick = { onStyle(style) },
                        modifier = Modifier.testTag("style_${style.name}"),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = space.s3),
            ) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Attempts")
                    Text(
                        if (draft.attemptsLocked) "${draft.style.label} means first go" else "Including the send",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CruxStepper(
                    value = draft.attempts,
                    onValueChange = onAttempts,
                    range = LogClimbViewModel.ATTEMPTS,
                    unit = if (draft.attempts == 1) "go" else "goes",
                    enabled = !draft.attemptsLocked,
                )
            }

            EffortScale(draft.effort, onEffort, Modifier.padding(top = space.s3))

            Eyebrow("Day · ${draft.date.dayLabel()}", Modifier.padding(top = space.s3))
            DayStrip(
                selected = draft.date,
                today = today,
                onSelect = onDate,
                onOpenCalendar = { pickingDate = true },
                modifier = Modifier.bleed(space.s4),
            )
            draft.dateError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            CruxTextField(
                label = "Name",
                value = draft.name,
                onValueChange = onName,
                placeholder = "Yellow dyno",
                helper = "Optional",
                error = draft.nameError,
                modifier = Modifier
                    .padding(top = space.s3)
                    .testTag("field_name"),
            )
            CruxTextField(
                label = "Notes",
                value = draft.notes,
                onValueChange = onNotes,
                placeholder = "Beta, conditions, how it felt",
                singleLine = false,
                minLines = 3,
                modifier = Modifier.testTag("field_notes"),
            )
            media()
        }
        CruxButton(
            text = if (draft.isEditing) "Save changes" else "Log climb",
            onClick = onSave,
            enabled = !draft.isSaving,
            icon = Icons.Rounded.Check,
            size = CruxButtonSize.Large,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(space.s4)
                .testTag("save_climb"),
        )
    }

    if (pickingDate) {
        PastDayDialog(draft.date, today, onDate) { pickingDate = false }
    }
}

/**
 * A photo and a video of the climb, each from the gallery or the camera. They are copied
 * into the app and attached when the climb is saved.
 */
@Composable
private fun ClimbMedia(
    draft: LogClimbDraft,
    onAttach: (Uri) -> Unit,
    onRemove: () -> Unit,
    captureUri: () -> Uri,
    onAttachVideo: (Uri) -> Unit,
    onRemoveVideo: () -> Unit,
    videoCaptureUri: () -> Uri,
) {
    val space = CruxTheme.space
    var capture by rememberSaveable { mutableStateOf<String?>(null) }
    var viewing by rememberSaveable { mutableStateOf(false) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onAttach) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) capture?.let { onAttach(Uri.parse(it)) }
    }
    val pick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    var videoCapture by rememberSaveable { mutableStateOf<String?>(null) }
    var playing by rememberSaveable { mutableStateOf(false) }
    val videoGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onAttachVideo) }
    val recorder = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { saved ->
        if (saved) videoCapture?.let { onAttachVideo(Uri.parse(it)) }
    }
    val pickVideo = { videoGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }

    Column(verticalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.padding(top = space.s2)) {
        Eyebrow("Photo and video · optional")
        val image = draft.imagePath
        when {
            draft.addingImage -> Text("Adding photo…", style = MaterialTheme.typography.bodyMedium)

            image != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s3)) {
                ImageThumbnail(image, "Climb photo", onClick = { viewing = true }, size = 72.dp, modifier = Modifier.testTag("climb_photo"))
                CruxButton("Replace", pick, variant = CruxButtonVariant.Text, size = CruxButtonSize.Small)
                CruxButton("Remove", onRemove, variant = CruxButtonVariant.Text, size = CruxButtonSize.Small, modifier = Modifier.testTag("remove_climb_photo"))
            }

            else -> Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                CruxButton(
                    "Choose photo",
                    pick,
                    variant = CruxButtonVariant.Outlined,
                    size = CruxButtonSize.Small,
                    icon = Icons.Rounded.Image,
                    modifier = Modifier.testTag("choose_climb_photo"),
                )
                CruxButton(
                    "Take photo",
                    {
                        val uri = captureUri()
                        capture = uri.toString()
                        camera.launch(uri)
                    },
                    variant = CruxButtonVariant.Outlined,
                    size = CruxButtonSize.Small,
                    icon = Icons.Rounded.PhotoCamera,
                    modifier = Modifier.testTag("take_climb_photo"),
                )
            }
        }
        val video = draft.videoPath
        when {
            draft.addingVideo -> Text("Adding video…", style = MaterialTheme.typography.bodyMedium)

            video != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s3)) {
                VideoThumbnail(video, "Climb video", onClick = { playing = true }, size = 72.dp, modifier = Modifier.testTag("climb_video"))
                CruxButton("Replace", pickVideo, variant = CruxButtonVariant.Text, size = CruxButtonSize.Small)
                CruxButton(
                    "Remove",
                    onRemoveVideo,
                    variant = CruxButtonVariant.Text,
                    size = CruxButtonSize.Small,
                    modifier = Modifier.testTag("remove_climb_video"),
                )
            }

            else -> Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                CruxButton(
                    "Choose video",
                    pickVideo,
                    variant = CruxButtonVariant.Outlined,
                    size = CruxButtonSize.Small,
                    icon = Icons.Rounded.VideoLibrary,
                    modifier = Modifier.testTag("choose_climb_video"),
                )
                CruxButton(
                    "Record video",
                    {
                        val uri = videoCaptureUri()
                        videoCapture = uri.toString()
                        recorder.launch(uri)
                    },
                    variant = CruxButtonVariant.Outlined,
                    size = CruxButtonSize.Small,
                    icon = Icons.Rounded.Videocam,
                    modifier = Modifier.testTag("record_climb_video"),
                )
            }
        }
        if (draft.videoFailed) {
            Text("That video could not be added. Try another.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (draft.imageFailed) {
            Text("That photo couldn't be added. Try another.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    if (playing && draft.videoPath != null) {
        VideoPlayer(draft.videoPath, draft.name.ifBlank { "Climb video" }) { playing = false }
    }
    if (viewing && draft.imagePath != null) {
        ImageViewer(draft.imagePath, draft.name.ifBlank { "Climb photo" }) { viewing = false }
    }
}
