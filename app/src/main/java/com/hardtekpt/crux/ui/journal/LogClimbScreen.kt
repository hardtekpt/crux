package com.hardtekpt.crux.ui.journal

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditNote
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.Venue
import com.hardtekpt.crux.ui.components.AnimatedCount
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxMotion
import com.hardtekpt.crux.ui.components.CruxSegmentedButtons
import com.hardtekpt.crux.ui.components.CruxSheet
import com.hardtekpt.crux.ui.components.CruxStepper
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.ImageThumbnail
import com.hardtekpt.crux.ui.components.ImageViewer
import com.hardtekpt.crux.ui.components.SheetButton
import com.hardtekpt.crux.ui.components.VideoPlayer
import com.hardtekpt.crux.ui.components.VideoThumbnail
import com.hardtekpt.crux.ui.components.input.DayStrip
import com.hardtekpt.crux.ui.components.input.EffortScale
import com.hardtekpt.crux.ui.components.input.GradeStrip
import com.hardtekpt.crux.ui.components.input.PastDayDialog
import com.hardtekpt.crux.ui.components.input.bleed
import com.hardtekpt.crux.ui.components.pressScale
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.gradeState
import com.hardtekpt.crux.ui.outcomeLine
import com.hardtekpt.crux.ui.theme.CruxTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun LogClimbScreen(
    onDone: () -> Unit,
    onOpenLog: (Long) -> Unit = {},
    /** Picking a climb logged today: its log opens in place of this form. */
    onSwitchToLog: (Long) -> Unit = onOpenLog,
    viewModel: LogClimbViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val detail by viewModel.placeDetail.collectAsStateWithLifecycle()
    val earlierLogs by viewModel.earlierLogs.collectAsStateWithLifecycle()
    val datasets by viewModel.datasets.collectAsStateWithLifecycle()
    val links by viewModel.datasetLinks.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val openLog by viewModel.openLog.collectAsStateWithLifecycle()
    LaunchedEffect(draft.saved) { if (draft.saved) onDone() }
    LaunchedEffect(openLog) {
        openLog?.let {
            viewModel.logOpened()
            onSwitchToLog(it)
        }
    }
    val whereActions = WhereActions(
        selectPlace = viewModel::selectPlace,
        createPlace = viewModel::createPlace,
        selectArea = viewModel::selectArea,
        setVenue = viewModel::setVenue,
        setPlaceText = viewModel::setPlace,
        selectSection = viewModel::selectSection,
        pickProblem = viewModel::pickProblem,
        pickDatasetClimb = viewModel::pickDatasetClimb,
        newClimb = viewModel::newClimb,
        usesDatasets = { section -> com.hardtekpt.crux.data.datasetsFor(section, datasets, links[section.id].orEmpty()).isNotEmpty() },
        whereOpened = viewModel::whereOpened,
        searchCatalogue = { dataset, query -> viewModel.searchCatalogue(dataset, query) },
    )
    val choices = remember(draft.placeId, draft.sectionId, detail, datasets, links, playlists) { viewModel.climbChoices(draft) }
    LogClimbContent(
        draft = draft,
        onBack = onDone,
        where = { dayButton -> WhereSection(draft, places, detail, whereActions, trailing = dayButton, choices = choices) },
        suggestions = remember(draft.placeId, draft.name, draft.problemId, detail) { viewModel.candidates() },
        datasetSuggestions = catalogueAndDatasetMatches(draft.name, choices, viewModel),
        onPickDatasetClimb = viewModel::pickDatasetClimb,
        onUnlink = viewModel::unlinkSource,
        onPickClimb = viewModel::pickProblem,
        onGo = viewModel::addGo,
        onUndoGo = viewModel::undoGo,
        onNoBeta = viewModel::setNoBeta,
        earlierLogs = earlierLogs,
        onOpenLog = onOpenLog,
        // Deletes at once; the app offers Undo.
        onDelete = viewModel::confirmDelete,
        onDiscipline = viewModel::setDiscipline,
        onGrade = viewModel::setGrade,
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
                // On a board, the angle sits beside the photo and video.
                angle = if (viewModel.onBoard(draft)) {
                    AngleChoice(draft.angle, viewModel.boardOf(draft), viewModel::setAngle)
                } else {
                    null
                },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogClimbContent(
    draft: LogClimbDraft,
    onBack: () -> Unit,
    onDiscipline: (Discipline) -> Unit,
    onGrade: (Int) -> Unit,
    onDate: (LocalDate) -> Unit,
    onVenue: (Venue) -> Unit,
    onName: (String) -> Unit,
    onPlace: (String) -> Unit,
    onNotes: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    where: @Composable (dayButton: @Composable () -> Unit) -> Unit = { it() },
    suggestions: List<com.hardtekpt.crux.data.model.Problem> = emptyList(),
    onPickClimb: (com.hardtekpt.crux.data.model.Problem) -> Unit = {},
    onGo: (Boolean) -> Unit = {},
    onUndoGo: () -> Unit = {},
    onNoBeta: (Boolean) -> Unit = {},
    earlierLogs: List<com.hardtekpt.crux.data.model.Climb> = emptyList(),
    onOpenLog: (Long) -> Unit = {},
    onDelete: () -> Unit = {},
    onEffort: (Int?) -> Unit = {},
    media: @Composable () -> Unit = {},
    today: LocalDate = LocalDate.now(com.hardtekpt.crux.ui.LocalClock.current),
    datasetSuggestions: List<com.hardtekpt.crux.data.ClimbSource> = emptyList(),
    onPickDatasetClimb: (com.hardtekpt.crux.data.ClimbSource) -> Unit = {},
    onUnlink: () -> Unit = {},
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
            title = if (draft.isEditing) "Edit log" else "Log climb",
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
            where { DayButton(draft.date, today, onClick = { pickingDate = true }) }
            draft.dateError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Eyebrow("Climb", Modifier.padding(top = space.s2))
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
            // Picked from a dataset: its own grade, which the strip started at when it converts.
            draft.gradeHint?.let { hint ->
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("grade_hint"),
                )
            }

            // Today's goes: one tap each, in the order climbed. The style follows from them.
            Eyebrow("Goes", Modifier.padding(top = space.s3))
            GoPads(draft, onGo)
            GoTrail(draft, onUndoGo)
            if (draft.asksAboutBeta) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("No beta", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Sent first go without watching anyone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = draft.noBeta,
                        onCheckedChange = onNoBeta,
                        modifier = Modifier.testTag("no_beta"),
                    )
                }
            }

            com.hardtekpt.crux.ui.components.input.EffortWords(draft.effort, onEffort, Modifier.padding(top = space.s3))

            // Name: suggests climbs here to continue; any other name starts a new climb.
            val source = draft.source
            // A board climb whose catalogue is gone still has its holds.
            val kept = draft.problemHolds
            if (source == null && kept != null) {
                Eyebrow("Holds", Modifier.padding(top = space.s3))
                com.hardtekpt.crux.ui.components.BoardDiagramCard(kept.second, kept.first, draft.name, Modifier.fillMaxWidth(), maxHeight = 280)
            }
            if (source != null) {
                LinkedNameRow(source, onUnlink, Modifier.padding(top = space.s3))
                val board = source.dataset.holdsBoard
                val holds = source.climb.holds
                if (board != null &&
                    holds != null
                ) {
                    com.hardtekpt.crux.ui.components.LightButton(board, holds, source.climb.name, preferredSection = draft.sectionId)
                }
            } else {
                NameField(draft, suggestions, onName, onPickClimb, Modifier.padding(top = space.s3), datasetSuggestions, onPickDatasetClimb)
            }
            Extras(draft, onNotes, media)
            if (earlierLogs.isNotEmpty()) {
                // The name, grade and where are the climb's: changing them changes every log.
                if (draft.isEditing) {
                    Text(
                        "Name, grade and where change the climb, for all ${earlierLogs.size + 1} logs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = space.s3).testTag("climb_wide_note"),
                    )
                }
                EarlierLogs(earlierLogs, onOpenLog, Modifier.padding(top = space.s4))
            }
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
    angle: AngleChoice? = null,
) {
    val space = CruxTheme.space
    var pickingAngle by rememberSaveable { mutableStateOf(false) }
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

    // Each is a chip that offers the gallery or the camera; once added, a thumbnail with Remove.
    Row(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalAlignment = Alignment.CenterVertically) {
        val image = draft.imagePath
        when {
            draft.addingImage -> Text("Adding photo…", style = MaterialTheme.typography.bodySmall)

            image != null -> MediaThumb(onRemove = onRemove, removeTag = "remove_climb_photo") {
                ImageThumbnail(image, "Climb photo", onClick = { viewing = true }, size = 44.dp, modifier = Modifier.testTag("climb_photo"))
            }

            else -> MediaChip(
                "Photo",
                Icons.Rounded.PhotoCamera,
                "add_climb_photo",
                "Choose photo",
                "Take photo",
                "choose_climb_photo",
                "take_climb_photo",
                pick,
            ) {
                val uri = captureUri()
                capture = uri.toString()
                camera.launch(uri)
            }
        }
        val video = draft.videoPath
        when {
            draft.addingVideo -> Text("Adding video…", style = MaterialTheme.typography.bodySmall)

            video != null -> MediaThumb(onRemove = onRemoveVideo, removeTag = "remove_climb_video") {
                VideoThumbnail(video, "Climb video", onClick = { playing = true }, size = 44.dp, modifier = Modifier.testTag("climb_video"))
            }

            else -> MediaChip(
                "Video",
                Icons.Rounded.Videocam,
                "add_climb_video",
                "Choose video",
                "Record video",
                "choose_climb_video",
                "record_climb_video",
                pickVideo,
            ) {
                val uri = videoCaptureUri()
                videoCapture = uri.toString()
                recorder.launch(uri)
            }
        }
        angle?.let { choice ->
            com.hardtekpt.crux.ui.components.CruxFilterChip(
                label = choice.angle?.let { "∠ $it°" } ?: "Angle",
                selected = choice.angle != null,
                onClick = { pickingAngle = true },
                modifier = Modifier.testTag("climb_angle"),
            )
        }
    }
    if (pickingAngle && angle != null) {
        AngleSheet(angle, onDismiss = { pickingAngle = false })
    }
    if (draft.videoFailed) {
        Text("That video could not be added. Try another.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    if (draft.imageFailed) {
        Text("That photo couldn't be added. Try another.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    if (playing && draft.videoPath != null) {
        VideoPlayer(draft.videoPath, draft.name.ifBlank { "Climb video" }) { playing = false }
    }
    if (viewing && draft.imagePath != null) {
        ImageViewer(draft.imagePath, draft.name.ifBlank { "Climb photo" }) { viewing = false }
    }
}

/** The day button beside the place: "Today", or the picked day edged in the accent so it isn't missed. */
@Composable
private fun DayButton(date: LocalDate, today: LocalDate, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val past = date != today
    val tint = colors.primary
    val shape = MaterialTheme.shapes.medium
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(min = 64.dp)
            .clip(shape)
            .background(colors.surfaceContainerLow, shape)
            .border(if (past) CruxTheme.size.borderEmphasis else CruxTheme.size.borderHairline, if (past) colors.primary else colors.outlineVariant, shape)
            .clickable(onClickLabel = "Change the day", onClick = onClick)
            .padding(vertical = CruxTheme.space.s2, horizontal = CruxTheme.space.s1)
            .testTag("climb_day"),
    ) {
        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            if (past) date.format(java.time.format.DateTimeFormatter.ofPattern("EEE d", java.util.Locale.UK)) else "Today",
            style = MaterialTheme.typography.labelSmall,
            color = if (past) tint else colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Two big pads: a fall or a send, one tap per go, each with the climb's count in all. */
@Composable
private fun GoPads(draft: LogClimbDraft, onGo: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
        // The counts are the climb's in all; the dots below say which goes are today's.
        GoPad("Fell", "not yet", draft.fallsInAll, colors.surfaceContainerHighest, colors.onSurface, Modifier.weight(1f).testTag("go_fell")) { onGo(false) }
        GoPad("Sent", "topped", draft.sendsInAll, CruxTheme.colors.success, CruxTheme.colors.onSuccess, Modifier.weight(1f).testTag("go_sent")) { onGo(true) }
    }
}

@Composable
private fun GoPad(label: String, hint: String, count: Int, fill: Color, ink: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier
            .height(84.dp)
            .pressScale(interaction)
            .clip(shape)
            .background(fill, shape)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClickLabel = "Add a go: $label",
                onClick = onClick,
            ),
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("+ $label", style = MaterialTheme.typography.titleMedium, color = ink)
            Text(hint.uppercase(), style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.8f))
        }
        AnimatedCount(
            count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = ink,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(ink.copy(alpha = 0.16f))
                .padding(horizontal = 7.dp, vertical = 1.dp),
        )
    }
}

/**
 * The goes as ✕ and ✓ in order: earlier logs' faded, then this log's, the style they make, and
 * Undo for this log's last one.
 */
@Composable
private fun GoTrail(draft: LogClimbDraft, onUndo: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val success = CruxTheme.colors.success
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("go_trail")) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f, fill = false)) {
            val shown = (draft.earlier.map { it to true } + draft.goes.map { it to false }).takeLast(12)
            shown.forEach { (sent, before) ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .alpha(if (before) 0.4f else 1f)
                        .clip(CircleShape)
                        .background(if (sent) success else colors.surfaceContainerHighest),
                ) {
                    Icon(
                        if (sent) Icons.Rounded.Check else Icons.Rounded.Close,
                        contentDescription = if (sent) "Sent" else "Fell",
                        tint = if (sent) CruxTheme.colors.onSuccess else colors.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        val sent = draft.style.isSend
        Text(
            draft.style.label,
            style = MaterialTheme.typography.labelLarge,
            color = if (sent) success else colors.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .background(if (sent) success.copy(alpha = 0.16f) else colors.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 3.dp)
                .testTag("go_style"),
        )
        if (draft.goesBefore > 0) {
            Text(
                "${draft.attempts} today · ${draft.goesBefore + draft.attempts} in all",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onUndo, enabled = draft.goesTouched, modifier = Modifier.testTag("go_undo")) { Text("Undo") }
    }
}

/** The name, with the climbs here that match it to continue one. */
@Composable
private fun NameField(
    draft: LogClimbDraft,
    suggestions: List<com.hardtekpt.crux.data.model.Problem>,
    onName: (String) -> Unit,
    onPick: (com.hardtekpt.crux.data.model.Problem) -> Unit,
    modifier: Modifier = Modifier,
    fromDatasets: List<com.hardtekpt.crux.data.ClimbSource> = emptyList(),
    onPickFromDataset: (com.hardtekpt.crux.data.ClimbSource) -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
        CruxTextField(
            label = "Name",
            value = draft.name,
            onValueChange = onName,
            placeholder = "Named for you if left blank",
            helper = if (draft.problemId != null && !draft.isEditing) "Adds to this climb" else null,
            error = draft.nameError,
            modifier = Modifier.testTag("field_name"),
        )
        val query = draft.name.trim()
        val matches = suggestions
            .filter { it.id != draft.problemId && (query.isEmpty() || it.name.contains(query, ignoreCase = true)) }
            .take(if (query.isEmpty()) 4 else 6)
        // The section's datasets, for a name typed: picking one is picking it in the Climb step.
        val named = matches.map { it.name.trim().lowercase() }.toSet()
        val fromData = fromDatasets.filter { it.climb.name.trim().lowercase() !in named }
        if (!draft.isEditing && (matches.isNotEmpty() || fromData.isNotEmpty())) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
                verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
                modifier = Modifier.testTag("climb_suggestions"),
            ) {
                matches.forEach { climb ->
                    CruxFilterChip(
                        label = "${climb.name} · ${climb.grade}",
                        selected = false,
                        onClick = { onPick(climb) },
                        modifier = Modifier.testTag("suggest_${climb.name}"),
                    )
                }
                fromData.forEach { source ->
                    CruxFilterChip(
                        label = listOfNotNull(source.climb.name, source.climb.gradeLabel, source.dataset.name).joinToString(" · "),
                        selected = false,
                        onClick = { onPickFromDataset(source) },
                        modifier = Modifier.testTag("suggest_dataset_${source.climb.name}"),
                    )
                }
            }
        }
    }
}

/** Name suggestions from the section's datasets, then its board catalogues (searched as you type), three at most. */
@Composable
private fun catalogueAndDatasetMatches(typed: String, choices: ClimbChoices?, viewModel: LogClimbViewModel): List<com.hardtekpt.crux.data.ClimbSource> {
    val own = remember(typed, choices) { datasetMatches(typed, choices) }
    val catalogues = choices?.datasets.orEmpty().filter { it.isCatalogue }
    val fromCatalogues by androidx.compose.runtime.produceState(emptyList<com.hardtekpt.crux.data.ClimbSource>(), typed, catalogues) {
        val query = typed.trim()
        value = if (query.length < 2 || own.size >= 3) {
            emptyList()
        } else {
            catalogues.flatMap { viewModel.searchCatalogue(it, query, limit = 3) }
                .filter { !it.climb.name.trim().equals(query, ignoreCase = true) }
        }
    }
    return (own + fromCatalogues).take(3)
}

/** Up to three climbs in the section's datasets whose name has what's typed. */
internal fun datasetMatches(typed: String, choices: ClimbChoices?): List<com.hardtekpt.crux.data.ClimbSource> {
    val query = typed.trim()
    if (query.isEmpty() || choices == null) return emptyList()
    return choices.datasets.flatMap { d -> d.climbs.map { com.hardtekpt.crux.data.ClimbSource(it, d) } }
        .filter { it.climb.name.contains(query, ignoreCase = true) && !it.climb.name.trim().equals(query, ignoreCase = true) }
        .take(3)
}

/**
 * The name as a climb picked from a dataset: the dataset's badge, the name, where it's from and
 * who set it. ✕ makes it a plain name again, keeping the text.
 */
@Composable
private fun LinkedNameRow(source: com.hardtekpt.crux.data.ClimbSource, onUnlink: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s1)) {
        Eyebrow("Name")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colors.surfaceContainerLow, shape)
                .border(CruxTheme.size.borderEmphasis, colors.primary, shape)
                .padding(start = CruxTheme.space.s3, top = CruxTheme.space.s1, bottom = CruxTheme.space.s1)
                .testTag("linked_name"),
        ) {
            // A board climb shows its holds; tap them for the whole board.
            val holds = source.climb.holds
            val board = source.dataset.holdsBoard
            if (holds != null && board != null) {
                com.hardtekpt.crux.ui.components.BoardDiagramCard(board, holds, source.climb.name, Modifier.size(width = 40.dp, height = 52.dp), small = true)
            } else {
                com.hardtekpt.crux.ui.datasets.DatasetBadge(source.dataset, size = 32.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(source.climb.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    listOfNotNull("from ${source.dataset.name}", source.climb.setter?.let { "set by $it" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onUnlink, modifier = Modifier.testTag("unlink_source")) {
                Icon(Icons.Rounded.Close, contentDescription = "Type a name instead")
            }
        }
    }
}

/** A note, a photo and a video, each a chip until it's used. */
@Composable
private fun Extras(draft: LogClimbDraft, onNotes: (String) -> Unit, media: @Composable () -> Unit) {
    var writing by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), modifier = Modifier.animateContentSize(CruxMotion.medium())) {
        if (writing || draft.notes.isNotBlank()) {
            CruxTextField(
                label = "Note",
                value = draft.notes,
                onValueChange = onNotes,
                placeholder = "Beta, conditions",
                singleLine = false,
                minLines = 2,
                modifier = Modifier.testTag("field_notes"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), verticalAlignment = Alignment.CenterVertically) {
            if (!writing && draft.notes.isBlank()) {
                CruxButton(
                    "Note",
                    { writing = true },
                    variant = CruxButtonVariant.Outlined,
                    size = CruxButtonSize.Small,
                    icon = Icons.Rounded.EditNote,
                    modifier = Modifier.testTag("add_note"),
                )
            }
            media()
        }
    }
}

/** A media chip that opens a small menu: from the gallery, or with the camera. */
@Composable
private fun MediaChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    chooseLabel: String,
    captureLabel: String,
    chooseTag: String,
    captureTag: String,
    onChoose: () -> Unit,
    onCapture: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        CruxButton(label, { open = true }, variant = CruxButtonVariant.Outlined, size = CruxButtonSize.Small, icon = icon, modifier = Modifier.testTag(tag))
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(chooseLabel) },
                onClick = {
                    open = false
                    onChoose()
                },
                modifier = Modifier.testTag(chooseTag),
            )
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(captureLabel) },
                onClick = {
                    open = false
                    onCapture()
                },
                modifier = Modifier.testTag(captureTag),
            )
        }
    }
}

/** An added photo or video as a small thumbnail with a remove button on its corner. */
@Composable
private fun MediaThumb(onRemove: () -> Unit, removeTag: String, thumbnail: @Composable () -> Unit) {
    Box {
        Box(Modifier.padding(top = 6.dp, end = 6.dp)) { thumbnail() }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClickLabel = "Remove", onClick = onRemove)
                .testTag(removeTag),
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Remove", modifier = Modifier.size(12.dp))
        }
    }
}

/**
 * The climb's other logs, newest first: the day, the grade and how it went. Tapping one opens
 * it to change or delete.
 */
@Composable
private fun EarlierLogs(logs: List<com.hardtekpt.crux.data.model.Climb>, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val goes = logs.sumOf { it.attempts }
    Column(modifier.testTag("earlier_logs"), verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s1)) {
        Eyebrow("Earlier logs · $goes ${if (goes == 1) "go" else "goes"}")
        logs.forEach { log ->
            CruxListRow(
                title = log.date.dayLabel(),
                supporting = listOfNotNull(log.outcomeLine(), log.notes).joinToString(" · "),
                leading = { com.hardtekpt.crux.ui.components.GradeBadge(log.grade, log.gradeState) },
                onClick = { onOpen(log.id) },
                modifier = Modifier.testTag("earlier_log"),
            )
        }
    }
}

/** The board angle as the form has it, the board it's on (for its angles), and how to change it. */
data class AngleChoice(val angle: Int?, val board: com.hardtekpt.crux.data.model.BoardModel?, val onChange: (Int?) -> Unit)

/**
 * The board's angle: a MoonBoard's two angles, or the stepper (0° to 70° in 5° steps, tap the
 * number to type) with quick picks. Clear takes it off. It is saved as the note's first line.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AngleSheet(choice: AngleChoice, onDismiss: () -> Unit) {
    val space = CruxTheme.space
    val board = choice.board
    CruxSheet(
        onDismiss = onDismiss,
        modifier = Modifier.testTag("angle_sheet"),
        title = "Board angle",
        action = if (choice.angle != null) SheetButton("Clear", { choice.onChange(null) }, tag = "angle_clear") else null,
        confirm = SheetButton("Done", {
            // Opening the stepper and leaving it as it shows sets that angle.
            if (choice.angle == null && board?.angles == null) choice.onChange(40)
            onDismiss()
        }, tag = "angle_done"),
        spacing = space.s3,
        scroll = false,
    ) {
        val angles = board?.angles
        Text(
            if (board != null &&
                angles != null
            ) {
                "${board.label} is set at ${angles.joinToString(" or ") { "$it°" }}."
            } else {
                "Saved as the first line of the note."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (angles != null) {
            com.hardtekpt.crux.ui.components.CruxSegmentedButtons(
                options = angles,
                selected = choice.angle ?: -1,
                label = { "$it°" },
                onSelect = { choice.onChange(it) },
                modifier = Modifier.fillMaxWidth().testTag("angle_options"),
            )
        } else {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                com.hardtekpt.crux.ui.components.CruxStepper(
                    choice.angle ?: 40,
                    { choice.onChange(it) },
                    com.hardtekpt.crux.data.model.BoardAngleNote.RANGE,
                    "°",
                    step = com.hardtekpt.crux.data.model.BoardAngleNote.STEP,
                    testTagPrefix = "angle",
                )
            }
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(space.s2),
                verticalArrangement = Arrangement.spacedBy(space.s2),
            ) {
                listOf(20, 25, 30, 35, 40, 45, 50).forEach { quick ->
                    com.hardtekpt.crux.ui.components.CruxFilterChip(
                        label = "$quick°",
                        selected = choice.angle == quick,
                        onClick = { choice.onChange(quick) },
                        modifier = Modifier.testTag("angle_$quick"),
                    )
                }
            }
        }
    }
}
