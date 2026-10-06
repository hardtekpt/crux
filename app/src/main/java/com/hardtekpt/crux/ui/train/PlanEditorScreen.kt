package com.hardtekpt.crux.ui.train

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardtekpt.crux.data.model.Exercise
import com.hardtekpt.crux.data.model.ExerciseTarget
import com.hardtekpt.crux.data.model.MetricType
import com.hardtekpt.crux.data.model.formatKg
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.CruxCardFill
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxStepper
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.CruxValueStepper
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.input.DurationWheel
import com.hardtekpt.crux.ui.components.input.InputRow
import com.hardtekpt.crux.ui.components.input.LoadWheel
import com.hardtekpt.crux.ui.components.input.NumberWheel
import com.hardtekpt.crux.ui.components.input.formatDuration
import com.hardtekpt.crux.ui.theme.CruxTheme

@Composable
fun PlanEditorScreen(
    onDone: () -> Unit,
    viewModel: PlanEditorViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    LaunchedEffect(draft.done) { if (draft.done) onDone() }
    val space = CruxTheme.space

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("screen_PlanEditor"),
    ) {
        CruxTopAppBar(
            title = if (draft.isNew) "New plan" else "Edit plan",
            onBack = onDone,
            actions = {
                if (!draft.isNew) {
                    IconButton(onClick = viewModel::requestDelete, modifier = Modifier.testTag("delete_plan")) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete plan")
                    }
                }
            },
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = space.s4, end = space.s4, bottom = space.s6),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            item {
                CruxTextField(
                    label = "Name",
                    value = draft.name,
                    onValueChange = viewModel::setName,
                    placeholder = "Max hangs + limit",
                    error = draft.nameError,
                    modifier = Modifier.testTag("field_plan_name"),
                )
            }
            item {
                CruxTextField(
                    label = "Description",
                    value = draft.description,
                    onValueChange = viewModel::setDescription,
                    placeholder = "What the session is for",
                    helper = "Optional",
                    singleLine = false,
                    minLines = 2,
                )
            }
            draft.blocks.forEachIndexed { blockIndex, block ->
                item(key = "block_${block.key}") {
                    BlockCard(
                        index = blockIndex,
                        block = block,
                        isFirst = blockIndex == 0,
                        isLast = blockIndex == draft.blocks.lastIndex,
                        onRename = { viewModel.renameBlock(blockIndex, it) },
                        onMove = { viewModel.moveBlock(blockIndex, it) },
                        onRemove = { viewModel.removeBlock(blockIndex) },
                        onAddExercise = { viewModel.openPicker(blockIndex) },
                        onEditItem = { viewModel.editItem(ItemRef(blockIndex, it)) },
                        onMoveItem = { item, delta -> viewModel.moveItem(ItemRef(blockIndex, item), delta) },
                        onRemoveItem = { viewModel.removeItem(ItemRef(blockIndex, it)) },
                    )
                }
            }
            item {
                CruxButton(
                    text = "Add block",
                    onClick = viewModel::addBlock,
                    variant = CruxButtonVariant.Outlined,
                    icon = Icons.Rounded.Add,
                    modifier = Modifier.testTag("add_block"),
                )
            }
            draft.contentError?.let { error ->
                item { Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
        CruxButton(
            text = "Save plan",
            onClick = viewModel::save,
            enabled = !draft.isSaving,
            icon = Icons.Rounded.Check,
            size = CruxButtonSize.Large,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(space.s4)
                .testTag("save_plan"),
        )
    }

    if (draft.pickingFor != null) {
        ExercisePickerSheet(library = library, onPick = viewModel::addExercise, onDismiss = viewModel::closePicker)
    }
    draft.editingItem?.let { item ->
        TargetSheet(
            exercise = item.exercise,
            target = item.target,
            onChange = viewModel::updateTarget,
            onDismiss = viewModel::closeEditor,
        )
    }
    if (draft.confirmDelete) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge,
            title = { Text("Delete ${draft.name}?", style = MaterialTheme.typography.headlineSmall) },
            text = { Text("The plan is removed. Your exercises stay in the library.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete, modifier = Modifier.testTag("confirm_delete")) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelDelete) { Text("Keep") } },
        )
    }
}

@Composable
private fun BlockCard(
    index: Int,
    block: BlockDraft,
    isFirst: Boolean,
    isLast: Boolean,
    onRename: (String) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    onAddExercise: () -> Unit,
    onEditItem: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
) {
    val space = CruxTheme.space
    CruxCard(fill = CruxCardFill.Low, modifier = Modifier.fillMaxWidth().testTag("plan_block")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Block ${index + 1}", Modifier.weight(1f))
            SmallIcon(Icons.Rounded.KeyboardArrowUp, "Move block up", enabled = !isFirst) { onMove(-1) }
            SmallIcon(Icons.Rounded.KeyboardArrowDown, "Move block down", enabled = !isLast) { onMove(1) }
            SmallIcon(Icons.Rounded.Close, "Remove block") { onRemove() }
        }
        CruxTextField(label = "Block name", value = block.name, onValueChange = onRename)
        Column(verticalArrangement = Arrangement.spacedBy(space.s2)) {
            block.items.forEachIndexed { itemIndex, item ->
                CruxListRow(
                    title = item.exercise.name,
                    supporting = listOfNotNull(item.target.prescription(item.exercise.metric, com.hardtekpt.crux.ui.LocalUnits.current == com.hardtekpt.crux.data.prefs.UnitSystem.IMPERIAL), item.target.restLabel()?.let { "rest $it" })
                        .joinToString(" · "),
                    onClick = { onEditItem(itemIndex) },
                    trailing = {
                        Row {
                            SmallIcon(Icons.Rounded.KeyboardArrowUp, "Move up", enabled = itemIndex > 0) { onMoveItem(itemIndex, -1) }
                            SmallIcon(Icons.Rounded.KeyboardArrowDown, "Move down", enabled = itemIndex < block.items.lastIndex) {
                                onMoveItem(itemIndex, 1)
                            }
                            SmallIcon(Icons.Rounded.Close, "Remove ${item.exercise.name}") { onRemoveItem(itemIndex) }
                        }
                    },
                    modifier = Modifier.testTag("plan_item"),
                )
            }
        }
        CruxButton(
            text = "Add exercise",
            onClick = onAddExercise,
            variant = CruxButtonVariant.Text,
            icon = Icons.Rounded.Add,
            modifier = Modifier.testTag("add_exercise_to_block"),
        )
    }
}

@Composable
private fun SmallIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(CruxTheme.size.touchTarget)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ExercisePickerSheet(
    library: List<Exercise>,
    onPick: (Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = library.filter { it.name.contains(query.trim(), ignoreCase = true) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(horizontal = CruxTheme.space.s4)) {
            Text("Add exercise", style = MaterialTheme.typography.headlineSmall)
            CruxTextField(
                label = "Search the library",
                value = query,
                onValueChange = { query = it },
                placeholder = "Hang, pull-up, core",
                modifier = Modifier
                    .padding(top = CruxTheme.space.s3)
                    .testTag("exercise_search"),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
                contentPadding = PaddingValues(bottom = CruxTheme.space.s8),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                if (matches.isEmpty()) {
                    item {
                        Text(
                            if (library.isEmpty()) "Your library is empty. Add exercises from Train › Exercises." else "Nothing matches \"$query\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(matches, key = { it.id }) { exercise ->
                    CruxListRow(
                        title = exercise.name,
                        supporting = "${exercise.category.label} · ${exercise.metric.label}",
                        onClick = { onPick(exercise) },
                        modifier = Modifier.testTag("pick_${exercise.name}"),
                    )
                }
            }
        }
    }
}

/** Targets for one exercise; only the fields its metric uses are shown. */
@Composable
private fun TargetSheet(
    exercise: Exercise,
    target: ExerciseTarget,
    onChange: (ExerciseTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    val metric = exercise.metric
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.testTag("target_sheet"),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CruxTheme.space.s4)
                .padding(bottom = CruxTheme.space.s6)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
        ) {
            Text(exercise.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                target.prescription(metric, com.hardtekpt.crux.ui.LocalUnits.current == com.hardtekpt.crux.data.prefs.UnitSystem.IMPERIAL),
                style = CruxTheme.type.code,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (metric.usesIntervals) {
                IntervalTargets(target, metric.usesLoad, onChange)
            } else {
                StandardTargets(target, metric, onChange)
            }
            CruxButton(
                text = "Done",
                onClick = onDismiss,
                variant = CruxButtonVariant.Tonal,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("target_done"),
            )
        }
    }
}

/**
 * Sets with reps or time per set, optional load, and rest between sets. Each value is a row
 * that opens its wheel in place; one row is open at a time.
 */
@Composable
private fun StandardTargets(target: ExerciseTarget, metric: MetricType, onChange: (ExerciseTarget) -> Unit) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val toggle = { key: String -> open = if (open == key) null else key }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        InputRow("Sets", target.sets.toString(), open == "sets", { toggle("sets") }, unit = "sets", testTag = "sets") {
            NumberWheel(target.sets, { onChange(target.copy(sets = it)) }, 1..20, "sets", "Sets")
        }
        if (metric.usesReps) {
            InputRow("Reps", target.reps.toString(), open == "reps", { toggle("reps") }, unit = "reps", testTag = "reps") {
                NumberWheel(target.reps, { onChange(target.copy(reps = it)) }, 1..100, "reps", "Reps")
            }
        }
        if (metric.usesTime) {
            InputRow("Time per set", formatDuration(target.seconds), open == "seconds", { toggle("seconds") }, mono = true, testTag = "seconds") {
                DurationWheel(
                    target.seconds, { onChange(target.copy(seconds = it)) }, "Time per set",
                    maxMinutes = 10, minSeconds = 1, presets = listOf(5, 7, 10, 20, 30, 60),
                )
            }
        }
        if (metric.usesLoad) {
            LoadRow(target, open == "load", { toggle("load") }, onChange)
        }
        InputRow("Rest between sets", formatDuration(target.restSeconds), open == "rest", { toggle("rest") }, mono = true, testTag = "rest") {
            DurationWheel(
                target.restSeconds, { onChange(target.copy(restSeconds = it)) }, "Rest between sets",
                maxMinutes = 15, secondStep = 5, presets = listOf(60, 90, 120, 180, 240, 300),
            )
        }
    }
}

/** Interval work: work and rest per repeat, repeats per cycle, cycles, rest between cycles. */
@Composable
private fun IntervalTargets(target: ExerciseTarget, usesLoad: Boolean, onChange: (ExerciseTarget) -> Unit) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val toggle = { key: String -> open = if (open == key) null else key }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        InputRow("Work", formatDuration(target.seconds), open == "work", { toggle("work") }, mono = true, testTag = "work") {
            DurationWheel(
                target.seconds, { onChange(target.copy(seconds = it)) }, "Work",
                maxMinutes = 10, minSeconds = 1, presets = listOf(5, 7, 10, 15, 20, 30),
            )
        }
        InputRow("Rest", formatDuration(target.repRestSeconds), open == "rep_rest", { toggle("rep_rest") }, mono = true, testTag = "rep_rest") {
            DurationWheel(
                target.repRestSeconds, { onChange(target.copy(repRestSeconds = it)) }, "Rest",
                maxMinutes = 10, presets = listOf(3, 5, 10, 30, 60, 90),
            )
        }
        InputRow("Repeats", target.reps.toString(), open == "repeats", { toggle("repeats") }, unit = "reps", testTag = "repeats") {
            NumberWheel(target.reps, { onChange(target.copy(reps = it)) }, 1..50, "reps", "Repeats")
        }
        InputRow("Cycles", target.sets.toString(), open == "cycles", { toggle("cycles") }, unit = "cycles", testTag = "cycles") {
            NumberWheel(target.sets, { onChange(target.copy(sets = it)) }, 1..20, "cycles", "Cycles")
        }
        if (usesLoad) {
            LoadRow(target, open == "load", { toggle("load") }, onChange)
        }
        InputRow("Rest between cycles", formatDuration(target.restSeconds), open == "cycle_rest", { toggle("cycle_rest") }, mono = true, testTag = "cycle_rest") {
            DurationWheel(
                target.restSeconds, { onChange(target.copy(restSeconds = it)) }, "Rest between cycles",
                maxMinutes = 15, secondStep = 5, presets = listOf(60, 90, 120, 180, 240, 300),
            )
        }
    }
}

@Composable
private fun LoadRow(target: ExerciseTarget, open: Boolean, onToggle: () -> Unit, onChange: (ExerciseTarget) -> Unit) {
    val load = target.loadKg
    val imperial = com.hardtekpt.crux.ui.LocalUnits.current == com.hardtekpt.crux.data.prefs.UnitSystem.IMPERIAL
    InputRow(
        "Added load",
        (if (load > 0) "+" else if (load < 0) "−" else "") + com.hardtekpt.crux.data.model.formatLoad(load, imperial),
        open,
        onToggle,
        unit = com.hardtekpt.crux.data.model.loadUnit(imperial),
        testTag = "load",
    ) {
        LoadWheel(load, { onChange(target.copy(loadKg = it)) }, minKg = MIN_LOAD, maxKg = MAX_LOAD)
    }
}

private const val MIN_LOAD = -50.0
private const val MAX_LOAD = 150.0
