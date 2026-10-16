package com.hardtekpt.crux.ui.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import com.hardtekpt.crux.ui.components.CruxSheet
import com.hardtekpt.crux.ui.components.CruxStepper
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.CruxValueStepper
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.SheetButton
import com.hardtekpt.crux.ui.components.input.DurationWheel
import com.hardtekpt.crux.ui.components.input.InputRow
import com.hardtekpt.crux.ui.components.input.LoadWheel
import com.hardtekpt.crux.ui.components.input.NumberWheel
import com.hardtekpt.crux.ui.components.input.formatDuration
import com.hardtekpt.crux.ui.theme.CruxTheme

@Composable
fun PlanEditorScreen(onDone: () -> Unit, viewModel: PlanEditorViewModel = hiltViewModel()) {
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
        com.hardtekpt.crux.ui.components.ExercisePickerSheet(library = library, onPick = viewModel::addExercise, onDismiss = viewModel::closePicker)
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
    // Laid out like the plan's own page: the block as a numbered heading (its name typed in place),
    // its exercises as rows under it, and no card around them. Moving and removing live in menus.
    val space = CruxTheme.space
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.fillMaxWidth().testTag("plan_block")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = space.s2)) {
            Text("${index + 1} ·", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            androidx.compose.foundation.text.BasicTextField(
                value = block.name,
                onValueChange = onRename,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = colors.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
                decorationBox = { field ->
                    Box(Modifier.padding(horizontal = space.s2, vertical = space.s2)) {
                        if (block.name.isEmpty()) Text("Block name", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                        field()
                    }
                },
                modifier = Modifier.weight(1f).testTag("block_name"),
            )
            RowMenu(
                "Block ${index + 1}",
                listOfNotNull(
                    ("Move up" to { onMove(-1) }).takeIf { !isFirst },
                    ("Move down" to { onMove(1) }).takeIf { !isLast },
                    "Remove block" to onRemove,
                ),
            )
        }
        block.items.forEachIndexed { itemIndex, item ->
            CruxListRow(
                title = item.exercise.name,
                supporting = listOfNotNull(
                    item.target.prescription(
                        item.exercise.metric,
                        com.hardtekpt.crux.ui.LocalUnits.current == com.hardtekpt.crux.data.prefs.UnitSystem.IMPERIAL,
                    ),
                    item.target.restLabel()?.let { "rest $it" },
                ).joinToString(" · "),
                onClick = { onEditItem(itemIndex) },
                trailing = {
                    RowMenu(
                        item.exercise.name,
                        listOfNotNull(
                            ("Move up" to { onMoveItem(itemIndex, -1) }).takeIf { itemIndex > 0 },
                            ("Move down" to { onMoveItem(itemIndex, 1) }).takeIf { itemIndex < block.items.lastIndex },
                            "Remove" to { onRemoveItem(itemIndex) },
                        ),
                    )
                },
                modifier = Modifier.testTag("plan_item"),
            )
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

/** ⋮ with a short menu of what applies to one row: move it, remove it. */
@Composable
private fun RowMenu(what: String, actions: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(CruxTheme.size.touchTarget)) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "$what: more", modifier = Modifier.size(20.dp))
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { (label, action) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        action()
                    },
                )
            }
        }
    }
}

/** Targets for one exercise; only the fields its metric uses are shown. [note] says where the change applies. */
@Composable
internal fun TargetSheet(
    exercise: Exercise,
    target: ExerciseTarget,
    onChange: (ExerciseTarget) -> Unit,
    onDismiss: () -> Unit,
    note: String? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    val metric = exercise.metric
    CruxSheet(
        onDismiss = onDismiss,
        modifier = Modifier.testTag("target_sheet"),
        title = exercise.name,
        confirm = SheetButton("Done", onDismiss, tag = "target_done"),
        spacing = CruxTheme.space.s3,
    ) {
        Text(
            target.prescription(metric, com.hardtekpt.crux.ui.LocalUnits.current == com.hardtekpt.crux.data.prefs.UnitSystem.IMPERIAL),
            style = CruxTheme.type.code,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        // The same steppers as Edit exercise and the interval timer, so sets and reps are set one way.
        if (metric.usesIntervals) {
            com.hardtekpt.crux.ui.session.IntervalFields(target.toIntervals(), showPrep = false) { onChange(it.toTarget(target.loadKg)) }
        } else {
            TargetFields(metric, target, onChange)
        }
        if (metric.usesLoad) LoadRow(target.loadKg) { onChange(target.copy(loadKg = it)) }
        extra?.invoke()
    }
}

private const val MIN_LOAD = -50.0
private const val MAX_LOAD = 150.0
