package com.hardtekpt.crux.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardtekpt.crux.data.dashboard.DashboardWidget
import com.hardtekpt.crux.data.dashboard.WidgetType
import com.hardtekpt.crux.data.dashboard.defaultDashboard
import com.hardtekpt.crux.data.dashboard.toRows
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxSheet
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.SheetButton
import com.hardtekpt.crux.ui.components.revealWhen
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.you.describe
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onOpenTemplate: (Long) -> Unit,
    onOpenJournal: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenYou: () -> Unit,
    onOpenProblem: (Long) -> Unit = {},
    onStartPlan: (Long) -> Unit = {},
    onOpenDaysOnWall: () -> Unit = {},
    onOpenWeekClimbs: () -> Unit = {},
    onOpenMeasurements: () -> Unit = {},
    onOpenExercise: (Long) -> Unit = {},
    onOpenPlace: (Long) -> Unit = {},
    onLogAtPlace: (Long) -> Unit = {},
    onLogClimb: () -> Unit = {},
    onNewPlan: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val demoMode by viewModel.demoMode.collectAsStateWithLifecycle()
    HomeContent(
        uiState = uiState,
        dashboard = dashboard,
        demoMode = demoMode,
        actions = WidgetActions(
            onOpenTemplate,
            onOpenJournal,
            onOpenProgress,
            onOpenYou,
            onOpenProblem,
            openDaysOnWall = onOpenDaysOnWall,
            openWeekClimbs = onOpenWeekClimbs,
            openMeasurements = onOpenMeasurements,
            startPlan = onStartPlan,
            openExercise = onOpenExercise,
            openPlace = onOpenPlace,
            logAtPlace = onLogAtPlace,
            logClimb = onLogClimb,
            newPlan = onNewPlan,
        ),
        editor = DashboardEditor(
            start = viewModel::startEditing,
            finish = viewModel::finishEditing,
            move = viewModel::move,
            resize = viewModel::resize,
            remove = viewModel::remove,
            openAdd = viewModel::openAddWidget,
            closeAdd = viewModel::closeAddWidget,
            add = viewModel::add,
            reset = viewModel::resetToDefault,
            configure = viewModel::configure,
            applySetup = viewModel::applySetup,
            cancelSetup = viewModel::cancelSetup,
        ),
    )
}

/** Everything edit mode can do to the layout. */
data class DashboardEditor(
    val start: () -> Unit = {},
    val finish: () -> Unit = {},
    val move: (String, String) -> Unit = { _, _ -> },
    val resize: (String) -> Unit = {},
    val remove: (String) -> Unit = {},
    val openAdd: () -> Unit = {},
    val closeAdd: () -> Unit = {},
    val add: (WidgetType) -> Unit = {},
    val reset: () -> Unit = {},
    val configure: (String) -> Unit = {},
    val applySetup: (com.hardtekpt.crux.data.TrainingStat?, Long?) -> Unit = { _, _ -> },
    val cancelSetup: () -> Unit = {},
)

data class DragCallbacks(val onStart: (String) -> Unit, val onDrag: (Offset) -> Unit, val onEnd: () -> Unit)

/**
 * The dashboard: a two-column flow of widgets the climber arranges. Long-press a widget
 * (or tap the edit icon) to rearrange: drag to move, resize, remove, or add from the catalogue.
 */
@Composable
fun HomeContent(
    uiState: HomeUiState,
    dashboard: DashboardState = DashboardState(widgets = defaultDashboard()),
    demoMode: Boolean = false,
    actions: WidgetActions = WidgetActions(),
    editor: DashboardEditor = DashboardEditor(),
    modifier: Modifier = Modifier,
) {
    val editing = dashboard.editing
    val scrollState = rememberScrollState()
    val haptics = LocalHapticFeedback.current
    val edgePx = with(LocalDensity.current) { 96.dp.toPx() }
    val space = CruxTheme.space

    // Drag state, all in root coordinates so it survives the widget changing rows.
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    // A removed widget's last position must not linger: a drag could aim at it, move nothing,
    // and stop looking for the widget that is really there.
    LaunchedEffect(dashboard.widgets) {
        val ids = dashboard.widgets.map { it.id }.toSet()
        bounds.keys.retainAll(ids)
    }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var dragTotal by remember { mutableStateOf(Offset.Zero) }
    var lastTarget by remember { mutableStateOf<String?>(null) }
    var viewport by remember { mutableStateOf(Rect.Zero) }

    // Where the dragged widget's top-left should be, in root coordinates. Scrolling moves the
    // layout but not the finger, so this ignores scroll and the translation absorbs it.
    fun dragOrigin(): Offset = dragStart + dragTotal

    fun dragCenter(): Offset? {
        val id = draggingId ?: return null
        val size = bounds[id]?.size ?: return null
        return dragOrigin() + Offset(size.width / 2, size.height / 2)
    }

    fun retarget() {
        val id = draggingId ?: return
        val center = dragCenter() ?: return
        val live = dashboard.widgets.map { it.id }.toSet()
        val target = bounds.entries.firstOrNull { (other, rect) -> other != id && other in live && rect.contains(center) }?.key
        if (target != null && target != lastTarget) {
            editor.move(id, target)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
        lastTarget = target
    }

    val dragCallbacks = DragCallbacks(
        onStart = { id ->
            if (!editing) editor.start()
            draggingId = id
            dragStart = bounds[id]?.topLeft ?: Offset.Zero
            dragTotal = Offset.Zero
            lastTarget = null
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        },
        onDrag = { delta ->
            dragTotal += delta
            retarget()
        },
        onEnd = { draggingId = null },
    )

    // Scroll when a dragged widget nears the top or bottom of the visible area.
    LaunchedEffect(draggingId) {
        while (draggingId != null) {
            val center = dragCenter()
            if (center != null && viewport != Rect.Zero) {
                val step = when {
                    center.y > viewport.bottom - edgePx * 1.5f -> 18f
                    center.y < viewport.top + edgePx -> -18f
                    else -> 0f
                }
                if (step != 0f) {
                    if (scrollState.scrollBy(step) != 0f) retarget()
                }
            }
            delay(16)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(if (editing) Modifier else Modifier.statusBarsPadding())
            .testTag("screen_Home"),
    ) {
        // No header: the dashboard starts at the top. Editing brings a bar with Add and Done.
        if (editing) {
            CruxTopAppBar(
                title = "Edit dashboard",
                actions = {
                    IconButton(onClick = editor.openAdd, modifier = Modifier.testTag("add_widget")) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add widget")
                    }
                    TextButton(onClick = editor.finish, modifier = Modifier.testTag("done_editing")) {
                        Text("Done", style = MaterialTheme.typography.labelLarge)
                    }
                },
            )
        }
        val header: @Composable () -> Unit = {
            // The date, with the button that edits the dashboard at its end.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Eyebrow(
                    when {
                        editing -> "Drag to move · resize · remove"
                        demoMode -> "${uiState.today.dayLabel()} · Demo data"
                        else -> uiState.today.dayLabel()
                    },
                    Modifier.weight(1f).testTag("home_eyebrow"),
                )
                if (!editing) {
                    IconButton(onClick = editor.start, modifier = Modifier.testTag("edit_dashboard")) {
                        Icon(
                            Icons.Rounded.DashboardCustomize,
                            contentDescription = "Edit dashboard",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        val footer: @Composable () -> Unit = {
            if (dashboard.widgets.isEmpty()) {
                Text(
                    "Your dashboard is empty. Tap Add widget to put something here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (editing || dashboard.widgets.isEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalAlignment = Alignment.CenterVertically) {
                    CruxButton(
                        text = "Add widget",
                        onClick = {
                            if (!editing) editor.start()
                            editor.openAdd()
                        },
                        variant = CruxButtonVariant.Tonal,
                        icon = Icons.Rounded.Add,
                        modifier = Modifier.testTag("add_widget_button"),
                    )
                    CruxButton(text = "Reset layout", onClick = editor.reset, variant = CruxButtonVariant.Text)
                }
            }
        }
        if (editing) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { viewport = it.boundsInRoot() }
                    .verticalScroll(scrollState)
                    .revealWhen(!uiState.isLoading)
                    .padding(horizontal = space.s4)
                    .padding(top = space.s1, bottom = space.s4 + LocalNavBarClearance.current)
                    .testTag("home_list"),
                verticalArrangement = Arrangement.spacedBy(space.s3),
            ) {
                header()
                // One flat, keyed list laid out as a two-column flow: moving a widget only reorders
                // children, so the widget being dragged keeps its gesture while it changes rows.
                DashboardGrid(widgets = dashboard.widgets, spacing = space.s3) {
                    dashboard.widgets.forEach { widget ->
                        key(widget.id) {
                            val dragging = widget.id == draggingId
                            EditableWidget(
                                widget = widget,
                                editing = editing,
                                dragging = dragging,
                                callbacks = dragCallbacks,
                                onResize = { editor.resize(widget.id) },
                                onRemove = { editor.remove(widget.id) },
                                onConfigure = { editor.configure(widget.id) },
                                modifier = Modifier
                                    .onGloballyPositioned { bounds[widget.id] = it.boundsInRoot() }
                                    .zIndex(if (dragging) 1f else 0f)
                                    .graphicsLayer {
                                        val current = bounds[widget.id]
                                        if (dragging && current != null) {
                                            val offset = dragOrigin() - current.topLeft
                                            translationX = offset.x
                                            translationY = offset.y
                                            scaleX = 1.03f
                                            scaleY = 1.03f
                                            shadowElevation = 24f
                                        }
                                    },
                            ) {
                                DashboardWidgetContent(
                                    widget = widget,
                                    state = uiState,
                                    actions = if (editing) WidgetActions() else actions,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
                footer()
            }
        } else {
            // Not editing, the dashboard is a lazy list: only the rows on screen are built.
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .revealWhen(!uiState.isLoading)
                    .testTag("home_list"),
                contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s1, bottom = space.s4 + LocalNavBarClearance.current),
                verticalArrangement = Arrangement.spacedBy(space.s3),
            ) {
                item(key = "header") { header() }
                items(dashboard.widgets.toRows(), key = { row -> row.joinToString { it.id } }) { row ->
                    val widget = row.first()
                    if (widget.size.fullWidth) {
                        // A full-width widget sizes itself; charts and grids inside can't be asked for intrinsic sizes.
                        Box(Modifier.fillMaxWidth().testTag("widget_${widget.type.name}")) {
                            DashboardWidgetContent(widget = widget, state = uiState, actions = actions, modifier = Modifier.fillMaxWidth())
                        }
                    } else {
                        // Two small tiles share the taller one's height, as in the editing grid.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(space.s3),
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        ) {
                            row.forEach { small ->
                                Box(Modifier.weight(1f).fillMaxHeight().testTag("widget_${small.type.name}")) {
                                    DashboardWidgetContent(widget = small, state = uiState, actions = actions, modifier = Modifier.fillMaxSize())
                                }
                            }
                            // A lone small widget keeps its half.
                            if (row.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
                item(key = "footer") { footer() }
            }
        }
    }

    if (dashboard.addingWidget) {
        AddWidgetSheet(onAdd = editor.add, onDismiss = editor.closeAdd)
    }
    dashboard.setup?.let { setup ->
        WidgetSetupSheet(
            setup = setup,
            current = dashboard.widgets.firstOrNull { it.id == setup.widgetId },
            exercises = uiState.training.exercises,
            onPick = editor.applySetup,
            onDismiss = editor.cancelSetup,
        )
    }
}

/**
 * A widget with its edit chrome. In edit mode a layer over the widget takes every tap
 * (so widgets do not navigate); a long press anywhere, or a drag on the handle, moves it.
 */
@Composable
private fun EditableWidget(
    widget: DashboardWidget,
    editing: Boolean,
    dragging: Boolean,
    callbacks: DragCallbacks,
    onResize: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    onConfigure: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val outline by animateFloatAsState(if (editing) 1f else 0f, com.hardtekpt.crux.ui.components.CruxMotion.fast(), label = "outline")
    // Long-press drag sits on the container, so it sees touches after the widget's own taps.
    Box(
        modifier = modifier
            .testTag("widget_${widget.type.name}")
            .pointerInput(widget.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { callbacks.onStart(widget.id) },
                    onDrag = { change, delta ->
                        change.consume()
                        callbacks.onDrag(delta)
                    },
                    onDragEnd = callbacks.onEnd,
                    onDragCancel = callbacks.onEnd,
                )
            },
    ) {
        content()
        if (editing) {
            // Swallows taps so widgets don't navigate while editing.
            Box(
                Modifier
                    .matchParentSize()
                    .clip(MaterialTheme.shapes.large)
                    .border(
                        width = if (dragging) 2.dp else 1.dp,
                        color = colors.primary.copy(alpha = outline * if (dragging) 1f else 0.6f),
                        shape = MaterialTheme.shapes.large,
                    )
                    .pointerInput(Unit) { detectTapGestures { } },
            )
            // Along the bottom edge, so the widget's name at the top stays readable while arranging.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(colors.surfaceContainerHighest, CircleShape)
                    .border(CruxTheme.size.borderHairline, colors.outlineVariant, CircleShape),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.DragIndicator,
                    contentDescription = "Drag to move ${widget.type.title}",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(8.dp)
                        .testTag("drag_${widget.type.name}")
                        .pointerInput(widget.id) {
                            detectDragGestures(
                                onDragStart = { callbacks.onStart(widget.id) },
                                onDrag = { change, delta ->
                                    change.consume()
                                    callbacks.onDrag(delta)
                                },
                                onDragEnd = callbacks.onEnd,
                                onDragCancel = callbacks.onEnd,
                            )
                        },
                )
                if (widget.type.configurable) {
                    IconButton(onClick = onConfigure, modifier = Modifier.size(40.dp).testTag("configure_${widget.type.name}")) {
                        Icon(
                            Icons.Rounded.Tune,
                            contentDescription = "Choose what ${widget.type.title} shows",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (widget.type.sizes.size > 1) {
                    IconButton(onClick = onResize, modifier = Modifier.size(40.dp).testTag("resize_${widget.type.name}")) {
                        Icon(
                            Icons.Rounded.OpenInFull,
                            contentDescription = "Resize ${widget.type.title}, now ${widget.size.label}",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp).testTag("remove_${widget.type.name}")) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove ${widget.type.title}", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * Two-column flow for dashboard widgets, in list order: half-width widgets pair up, a lone
 * half-width widget keeps its half, full-width widgets take a row.
 */
@Composable
private fun DashboardGrid(widgets: List<DashboardWidget>, spacing: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val width = constraints.maxWidth
        val half = (width - gap) / 2
        fun isFull(i: Int) = widgets.getOrNull(i)?.size?.fullWidth ?: true
        // Rows of child indices, following the same pairing as toRows().
        val rows = mutableListOf<List<Int>>()
        var pending: Int? = null
        measurables.indices.forEach { i ->
            when {
                isFull(i) -> {
                    pending?.let { rows += listOf(it) }
                    pending = null
                    rows += listOf(i)
                }

                pending == null -> pending = i

                else -> {
                    rows += listOf(pending!!, i)
                    pending = null
                }
            }
        }
        pending?.let { rows += listOf(it) }
        // Half-width widgets in a row share the taller one's height so tiles line up.
        val placeables = arrayOfNulls<androidx.compose.ui.layout.Placeable>(measurables.size)
        rows.forEach { row ->
            if (row.size == 1 && isFull(row[0])) {
                placeables[row[0]] = measurables[row[0]].measure(Constraints(minWidth = width, maxWidth = width))
            } else {
                val h = row.maxOf { measurables[it].maxIntrinsicHeight(half) }
                row.forEach { placeables[it] = measurables[it].measure(Constraints.fixed(half, h)) }
            }
        }
        val rowHeights = rows.map { row -> row.maxOf { placeables[it]!!.height } }
        val height = rowHeights.sum() + gap * (rows.size - 1).coerceAtLeast(0)
        layout(width, height) {
            var y = 0
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { column, index ->
                    placeables[index]!!.place(x = if (column == 0) 0 else half + gap, y = y)
                }
                y += rowHeights[r] + gap
            }
        }
    }
}

@Composable
private fun AddWidgetSheet(onAdd: (WidgetType) -> Unit, onDismiss: () -> Unit) {
    CruxSheet(
        onDismiss = onDismiss,
        title = "Add widget",
    ) {
        Eyebrow("Widgets", Modifier.padding(bottom = CruxTheme.space.s1))
        WidgetType.entries.forEach { type ->
            CruxListRow(
                title = type.title,
                supporting = "${type.description} · ${type.sizes.joinToString(", ") { it.label.lowercase() }}",
                onClick = { onAdd(type) },
                trailing = { Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.testTag("catalog_${type.name}"),
            )
        }
    }
}

/**
 * What a Training or Exercise widget shows: one of the training figures, or one exercise you've
 * trained, as the app's other sheets look.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun WidgetSetupSheet(
    setup: WidgetSetup,
    current: DashboardWidget?,
    exercises: List<com.hardtekpt.crux.ui.progress.ExerciseProgress>,
    onPick: (com.hardtekpt.crux.data.TrainingStat?, Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    CruxSheet(
        onDismiss = onDismiss,
        modifier = Modifier.testTag("widget_setup"),
        title = setup.type.title,
    ) {
        if (setup.type == WidgetType.TRAINING) {
            Eyebrow("Show", Modifier.padding(bottom = CruxTheme.space.s1))
            com.hardtekpt.crux.data.TrainingStat.entries.forEach { stat ->
                CruxListRow(
                    title = stat.label,
                    supporting = stat.description,
                    selected = current?.stat == stat,
                    onClick = { onPick(stat, null) },
                    modifier = Modifier.testTag("setup_${stat.name}"),
                )
            }
        } else {
            Eyebrow("Exercise", Modifier.padding(bottom = CruxTheme.space.s1))
            if (exercises.isEmpty()) {
                Text(
                    "Finish a session or log a result on an exercise, then pick it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val imperial = com.hardtekpt.crux.ui.LocalUnits.current == com.hardtekpt.crux.data.prefs.UnitSystem.IMPERIAL
            exercises.sortedBy { it.exercise.name.lowercase() }.forEach { progress ->
                CruxListRow(
                    title = progress.exercise.name,
                    supporting = "Best " + progress.best.describe(progress.exercise.metric, imperial),
                    selected = current?.exerciseId == progress.exercise.id,
                    onClick = { onPick(null, progress.exercise.id) },
                    modifier = Modifier.testTag("setup_exercise_${progress.exercise.name}"),
                )
            }
        }
    }
}
