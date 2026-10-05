package com.hardtekpt.crux.ui.journal

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.theme.CruxTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun LogClimbScreen(
    onDone: () -> Unit,
    viewModel: LogClimbViewModel = hiltViewModel(),
) {
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
        onVenue = viewModel::setVenue,
        onName = viewModel::setName,
        onPlace = viewModel::setPlace,
        onNotes = viewModel::setNotes,
        onSave = viewModel::save,
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

            Eyebrow("Grade · ${draft.gradeScale.label}", Modifier.padding(top = space.s3))
            GradePicker(draft, onGrade)

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

            Eyebrow("Day", Modifier.padding(top = space.s3))
            CruxButton(
                text = draft.date.dayLabel(),
                onClick = { pickingDate = true },
                variant = CruxButtonVariant.Outlined,
                icon = Icons.Rounded.CalendarMonth,
                modifier = Modifier.testTag("pick_date"),
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
        val state = rememberDatePickerState(
            initialSelectedDateMillis = draft.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = remember {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) =
                        utcTimeMillis <= LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    pickingDate = false
                }) { Text("Set day") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

/** Grades scroll horizontally in scale order; the picked one stays in view. */
@Composable
private fun GradePicker(draft: LogClimbDraft, onGrade: (Int) -> Unit) {
    val grades = draft.gradeScale.grades
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (draft.gradeIndex - 2).coerceAtLeast(0))
    LaunchedEffect(draft.gradeScale) {
        listState.scrollToItem((draft.gradeIndex - 2).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
        contentPadding = PaddingValues(end = CruxTheme.space.s4),
        modifier = Modifier.testTag("grade_picker"),
    ) {
        itemsIndexed(grades) { index, grade ->
            CruxFilterChip(
                label = grade,
                selected = index == draft.gradeIndex,
                onClick = { onGrade(index) },
                labelStyle = CruxTheme.type.gradeSmall,
                modifier = Modifier.testTag("grade_$grade"),
            )
        }
    }
}
