package com.hardtekpt.crux.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardtekpt.crux.R
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.MeasurementType
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.prefs.UnitSystem
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxSegmentedButtons
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.input.RulerInput
import com.hardtekpt.crux.ui.measureInput
import com.hardtekpt.crux.ui.settings.BackupScreen
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.typicalValue

/** What the setup screens do; the view model's, or nothing in screenshots. */
data class SetupActions(
    val start: () -> Unit = {},
    val restore: () -> Unit = {},
    val skipSetup: () -> Unit = {},
    val back: () -> Unit = {},
    val setUnits: (UnitSystem) -> Unit = {},
    val setScale: (GradeScale) -> Unit = {},
    val unitsDone: (skip: Boolean) -> Unit = {},
    val setPlaceName: (String) -> Unit = {},
    val toggleKind: (PlaceType) -> Unit = {},
    val setBoard: (SetupBoard) -> Unit = {},
    val placeDone: (skip: Boolean) -> Unit = {},
    val bodyDone: (weight: Double?, height: Double?) -> Unit = { _, _ -> },
    val setDemo: (Boolean) -> Unit = {},
    val finish: () -> Unit = {},
)

/** First-run setup: Welcome, then units, a first place, body stats and how to start. [onDone] opens the app. */
@Composable
fun SetupFlow(onDone: () -> Unit, viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.step != SetupStep.WELCOME && state.step != SetupStep.RESTORE) { viewModel.back() }
    if (state.step == SetupStep.RESTORE) {
        // The app's own Backups page; back from it opens the app once something was restored.
        BackupScreen(onBack = { viewModel.leaveRestore(onDone) })
        BackHandler { viewModel.leaveRestore(onDone) }
        return
    }
    SetupContent(
        state = state,
        actions = SetupActions(
            start = viewModel::start,
            restore = viewModel::restore,
            skipSetup = { viewModel.skip(onDone) },
            back = { viewModel.back() },
            setUnits = viewModel::setUnits,
            setScale = viewModel::setScale,
            unitsDone = viewModel::unitsDone,
            setPlaceName = viewModel::setPlaceName,
            toggleKind = viewModel::toggleKind,
            setBoard = viewModel::setBoard,
            placeDone = viewModel::placeDone,
            bodyDone = viewModel::bodyDone,
            setDemo = viewModel::setDemo,
            finish = { viewModel.finish(onDone) },
        ),
    )
}

@Composable
fun SetupContent(state: SetupState, actions: SetupActions, modifier: Modifier = Modifier) {
    // Its own surface, so text takes the theme's colour: setup is drawn outside the app's scaffold.
    androidx.compose.material3.Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { SetupSteps(state, actions) }
}

@Composable
private fun SetupSteps(state: SetupState, actions: SetupActions) {
    AnimatedContent(
        targetState = state.step,
        transitionSpec = {
            // Forward fades through in 180 ms; back is a plain, quicker fade.
            if (state.back) fadeIn(tween(120)) togetherWith fadeOut(tween(120)) else fadeIn(tween(180)) togetherWith fadeOut(tween(180))
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_Setup"),
        label = "setup",
    ) { step ->
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = CruxTheme.space.s4)
                .padding(bottom = CruxTheme.space.s6),
        ) {
            when (step) {
                SetupStep.WELCOME, SetupStep.RESTORE -> Welcome(actions)
                SetupStep.UNITS -> UnitsStep(state, actions)
                SetupStep.PLACE -> PlaceStep(state, actions)
                SetupStep.BODY -> BodyStep(state, actions)
                SetupStep.START -> StartStep(state, actions)
            }
        }
    }
}

@Composable
private fun ColumnScope.Welcome(actions: SetupActions) {
    val space = CruxTheme.space
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = space.s1, vertical = space.s6),
        verticalArrangement = Arrangement.spacedBy(space.s4, Alignment.CenterVertically),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(CruxTheme.size.borderHairline, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.Unspecified,
                modifier = Modifier.size(52.dp),
            )
        }
        Text("Climb Logger", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold)
        Text(
            "Plan training, log every climb and track your numbers. Everything stays on this phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Panel(Modifier.padding(top = space.s3)) {
            listOf(
                "Units and grade systems" to false,
                "The place you climb most" to true,
                "Weight and height" to true,
                "Start empty or with demo data" to false,
            ).forEachIndexed { index, (text, optional) ->
                Row(
                    Modifier.padding(horizontal = space.s4, vertical = space.s3),
                    horizontalArrangement = Arrangement.spacedBy(space.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "%02d".format(index + 1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(20.dp),
                    )
                    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (optional) Eyebrow("Optional")
                }
                if (index < 3) Divider()
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(space.s2)) {
        CruxButton("Set up", actions.start, Modifier.fillMaxWidth().testTag("setup_start"), size = com.hardtekpt.crux.ui.components.CruxButtonSize.Large)
        Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
            CruxButton(
                "Restore a backup",
                actions.restore,
                Modifier.weight(1f).testTag("setup_restore"),
                variant = CruxButtonVariant.Outlined,
                size = com.hardtekpt.crux.ui.components.CruxButtonSize.Large,
            )
            CruxButton(
                "Skip setup",
                actions.skipSetup,
                Modifier.weight(1f).testTag("setup_skip_all"),
                variant = CruxButtonVariant.Text,
                size = com.hardtekpt.crux.ui.components.CruxButtonSize.Large,
            )
        }
    }
}

/** Back, the four steps as a bar, and Skip (not on the last step). */
@Composable
private fun StepHeader(step: Int, onBack: () -> Unit, onSkip: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(top = CruxTheme.space.s2), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.padding(end = CruxTheme.space.s1).testTag("setup_back")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s1)) {
            repeat(4) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (index < step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
        }
        if (onSkip != null) {
            CruxButton("Skip", onSkip, Modifier.padding(start = CruxTheme.space.s1).testTag("setup_skip"), variant = CruxButtonVariant.Text)
        } else {
            Spacer(Modifier.width(56.dp))
        }
    }
}

@Composable
private fun StepTitle(eyebrow: String, title: String, body: String?) {
    Column(Modifier.padding(top = CruxTheme.space.s4, bottom = CruxTheme.space.s6), verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
        Eyebrow(eyebrow)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        body?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** A hairline-framed panel, rows inside it split by hairlines. */
@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(CruxTheme.size.borderHairline, MaterialTheme.colorScheme.outlineVariant, shape),
        content = content,
    )
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(CruxTheme.size.borderHairline).background(MaterialTheme.colorScheme.outlineVariant))

private fun UnitSystem.sample() = if (this == UnitSystem.METRIC) "kg · cm" else "lb · ft in"

private fun GradeScale.sample() = when (this) {
    GradeScale.FONT -> "6A · 7B+"
    GradeScale.V_SCALE -> "V3 · V8"
    GradeScale.FRENCH -> "6a · 7b+"
    GradeScale.YDS -> "5.10b · 5.12c"
    else -> ""
}

@Composable
private fun ColumnScope.UnitsStep(state: SetupState, actions: SetupActions) {
    StepHeader(1, actions.back) { actions.unitsDone(true) }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        StepTitle("Step 1 of 4", "Units and grades", "Logs keep the grade you enter and never convert. Each place section can use its own scale later.")
        Panel {
            @Composable
            fun <T> Choice(label: String, sample: String, options: List<T>, selected: T, name: (T) -> String, onSelect: (T) -> Unit) {
                Column(Modifier.padding(CruxTheme.space.s4), verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s3)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(sample, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    CruxSegmentedButtons(options, selected, name, onSelect)
                }
            }
            Choice("Units", state.units.sample(), UnitSystem.entries, state.units, { it.label }, actions.setUnits)
            Divider()
            Choice("Boulders", state.boulder.sample(), Discipline.BOULDER.scales, state.boulder, { it.label }, actions.setScale)
            Divider()
            Choice("Routes", state.route.sample(), Discipline.ROUTE.scales, state.route, { it.label }, actions.setScale)
        }
        Text(
            "Change these any time in You › Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = CruxTheme.space.s3, start = CruxTheme.space.s1),
        )
    }
    CruxButton("Next", {
        actions.unitsDone(false)
    }, Modifier.fillMaxWidth().testTag("setup_next"), size = com.hardtekpt.crux.ui.components.CruxButtonSize.Large)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.PlaceStep(state: SetupState, actions: SetupActions) {
    val space = CruxTheme.space
    StepHeader(2, actions.back) { actions.placeDone(true) }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        StepTitle("Step 2 of 4 · Optional", "Where do you climb?", "Add the place you go most. Log climb picks it for you. Add more later under You › Places.")
        CruxTextField("Name", state.placeName, actions.setPlaceName, Modifier.testTag("setup_place_name"), placeholder = "Your gym or crag")
        Eyebrow("What's there", Modifier.padding(top = space.s6, bottom = space.s2))
        Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
            PlaceType.entries.forEach { kind ->
                val on = kind in state.kinds
                val shape = MaterialTheme.shapes.large
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 88.dp)
                        .clip(shape)
                        .background(if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                        .border(
                            if (on) {
                                BorderStroke(
                                    CruxTheme.size.borderEmphasis,
                                    MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                BorderStroke(CruxTheme.size.borderHairline, MaterialTheme.colorScheme.outlineVariant)
                            },
                            shape,
                        )
                        .toggleable(on, role = Role.Checkbox) { actions.toggleKind(kind) }
                        .padding(space.s3)
                        .testTag("setup_kind_${kind.name}"),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Icon(
                        if (on) Icons.Rounded.Check else Icons.Rounded.Add,
                        contentDescription = null,
                        tint = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        kind.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = space.s4),
                    )
                }
            }
        }
        if (PlaceType.BOARD in state.kinds) {
            Panel(Modifier.padding(top = space.s4)) {
                Column(Modifier.padding(horizontal = space.s4, vertical = space.s3), verticalArrangement = Arrangement.spacedBy(space.s2)) {
                    Text("Which board?", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalArrangement = Arrangement.spacedBy(space.s2)) {
                        SetupBoard.entries.forEach { board ->
                            CruxFilterChip(board.label, board == state.board, {
                                actions.setBoard(board)
                            }, modifier = Modifier.testTag("setup_board_${board.name}"))
                        }
                    }
                    Text(
                        "Kilter and MoonBoard sections can browse their climbs once you add a dataset.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    CruxButton(
        "Add place",
        { actions.placeDone(false) },
        Modifier.fillMaxWidth().padding(top = space.s3).testTag("setup_next"),
        size = com.hardtekpt.crux.ui.components.CruxButtonSize.Large,
        enabled = state.canAddPlace,
    )
}

@Composable
private fun ColumnScope.BodyStep(state: SetupState, actions: SetupActions) {
    val weightInput = remember(state.units) { state.units.measureInput(MeasurementType.WEIGHT) }
    val heightInput = remember(state.units) { state.units.measureInput(MeasurementType.HEIGHT) }
    fun start(input: com.hardtekpt.crux.ui.MeasureInput, stored: Double?, type: MeasurementType) =
        input.scale.valueAt(input.scale.indexOf(input.toDisplay(stored ?: type.typicalValue())))
    var weight by rememberSaveable(state.units) { mutableDoubleStateOf(start(weightInput, state.weight, MeasurementType.WEIGHT)) }
    var height by rememberSaveable(state.units) { mutableDoubleStateOf(start(heightInput, state.height, MeasurementType.HEIGHT)) }
    StepHeader(3, actions.back) { actions.bodyDone(null, null) }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s3)) {
        StepTitle("Step 3 of 4 · Optional", "Weight and height", "Used for load relative to bodyweight and your You page. Stored only on this phone.")
        RulerInput(
            title = "Weight",
            value = weight,
            onValueChange = { weight = it },
            scale = weightInput.scale,
            display = weightInput.display,
            unit = weightInput.unit,
            parseTyped = weightInput.parse,
            typeUnit = weightInput.typeUnit,
            testTag = "setup_weight",
        )
        RulerInput(
            title = "Height",
            value = height,
            onValueChange = { height = it },
            scale = heightInput.scale,
            display = heightInput.display,
            unit = heightInput.unit,
            parseTyped = heightInput.parse,
            typeUnit = heightInput.typeUnit,
            testTag = "setup_height",
        )
        Text(
            "Drag the ruler or tap the number to type it. Arm span and other measurements live under You › Measurements.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = CruxTheme.space.s1),
        )
    }
    CruxButton(
        "Next",
        {
            actions.bodyDone(
                weightInput.toStored(weight).coerceIn(MeasurementType.WEIGHT.range),
                heightInput.toStored(height).coerceIn(MeasurementType.HEIGHT.range),
            )
        },
        Modifier.fillMaxWidth().padding(top = CruxTheme.space.s3).testTag("setup_next"),
        size = com.hardtekpt.crux.ui.components.CruxButtonSize.Large,
    )
}

@Composable
private fun ColumnScope.StartStep(state: SetupState, actions: SetupActions) {
    val space = CruxTheme.space
    StepHeader(4, actions.back, onSkip = null)
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(space.s3)) {
        StepTitle("Step 4 of 4", "How do you want to start?", null)
        StartOption(
            "Start my own log",
            "A clean journal with your settings and place. Your first climb or session starts it.",
            selected = !state.demo,
            tag = "setup_own",
        ) { actions.setDemo(false) }
        StartOption(
            "Look around with demo data",
            "Sample plans, climbs and records to explore. Turn demo mode off in Settings to start your own log; nothing you add is lost.",
            selected = state.demo,
            tag = "setup_demo",
        ) { actions.setDemo(true) }
        Eyebrow("Your setup", Modifier.padding(top = space.s4))
        val units = state.units
        val place = state.place()
        SummaryRow("Units", units.label)
        SummaryRow("Grades", "${state.boulder.label} · ${state.route.label}")
        SummaryRow(
            "Place",
            place?.let { p -> p.name + " · " + p.sections.joinToString(", ") { if (it.type == PlaceType.BOARD) it.name else it.type.label } } ?: "None yet",
        )
        SummaryRow(
            "Body",
            if (state.weight == null || state.height == null) {
                "Not set"
            } else {
                val w = units.measureInput(MeasurementType.WEIGHT)
                val h = units.measureInput(MeasurementType.HEIGHT)
                listOf(
                    w.display(w.toDisplay(state.weight)) + (w.unit?.let { " $it" } ?: ""),
                    h.display(h.toDisplay(state.height)) + (h.unit?.let { " $it" } ?: ""),
                ).joinToString(" · ")
            },
            last = true,
        )
    }
    CruxButton(
        if (state.demo) "Open demo" else "Start",
        actions.finish,
        Modifier.fillMaxWidth().padding(top = space.s3).testTag("setup_finish"),
        size = com.hardtekpt.crux.ui.components.CruxButtonSize.Large,
        enabled = !state.saving,
    )
}

@Composable
private fun StartOption(title: String, body: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.primaryContainer else colors.surfaceContainerLow)
            .border(
                if (selected) {
                    BorderStroke(
                        CruxTheme.size.borderEmphasis,
                        colors.primary,
                    )
                } else {
                    BorderStroke(CruxTheme.size.borderHairline, colors.outlineVariant)
                },
                shape,
            )
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(CruxTheme.space.s4)
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
    ) {
        Box(
            Modifier
                .padding(top = 1.dp)
                .size(22.dp)
                .border(2.dp, if (selected) colors.primary else colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(colors.primary))
        }
        Column(verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s1)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) colors.onPrimaryContainer else colors.onSurface)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, last: Boolean = false) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = CruxTheme.space.s2), horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s4)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        if (!last) Divider()
    }
}
