package com.hardtekpt.crux.ui.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hardtekpt.crux.data.SessionItem
import com.hardtekpt.crux.data.SessionSet
import com.hardtekpt.crux.data.model.MetricType
import com.hardtekpt.crux.data.model.formatDuration
import com.hardtekpt.crux.data.model.formatLoad
import com.hardtekpt.crux.data.model.loadUnit
import com.hardtekpt.crux.data.model.loadValue
import com.hardtekpt.crux.data.model.poundsToKg
import com.hardtekpt.crux.data.model.signedLoad
import com.hardtekpt.crux.data.prefs.UnitSystem
import com.hardtekpt.crux.ui.LocalUnits
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxMotion
import com.hardtekpt.crux.ui.components.CruxStepper
import com.hardtekpt.crux.ui.components.CruxValueStepper
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.theme.CruxTheme

/** The value columns a metric's table shows, in order. */
private enum class Field(val label: String) { LOAD("Load"), REPS("Reps"), SECONDS("Time") }

private fun MetricType.fields(): List<Field> = buildList {
    if (usesLoad) add(Field.LOAD)
    if (usesReps || usesIntervals) add(Field.REPS)
    if (usesTime || usesIntervals) add(Field.SECONDS)
}

private fun Field.show(values: SetValues, imperial: Boolean): String = when (this) {
    Field.LOAD -> (
        if (values.loadKg > 0) {
            "+"
        } else if (values.loadKg < 0) {
            "−"
        } else {
            ""
        }
        ) + formatLoad(values.loadKg, imperial)

    Field.REPS -> values.reps.toString()

    Field.SECONDS -> formatDuration(values.seconds)
}

/** Which set of which exercise has a value open for changing. */
internal data class OpenCell(val itemId: Long, val setIndex: Int, val field: Int)

/**
 * One exercise as a card: its name and target, then its sets as a table to tick (set, last time,
 * the values). Tap a value to change it in place; + Add set adds one for this session. Once
 * every set is in, the card folds to one line until it's tapped open again.
 */
@Composable
internal fun ExerciseCard(
    item: SessionItem,
    values: (Int) -> SetValues,
    previous: List<SessionSet>?,
    newBest: Boolean,
    folded: Boolean,
    open: OpenCell?,
    timerRunning: Boolean,
    current: Boolean,
    onToggleFold: () -> Unit,
    onOpen: (OpenCell?) -> Unit,
    onValues: (Int, SetValues) -> Unit,
    onTick: (Int) -> Unit,
    onAddSet: () -> Unit,
    onMenu: () -> Unit,
) {
    val haptics = com.hardtekpt.crux.ui.components.input.rememberCruxHaptics()
    val colors = MaterialTheme.colorScheme
    val space = CruxTheme.space
    val imperial = LocalUnits.current == UnitSystem.IMPERIAL
    val metric = item.exercise.metric
    val fields = metric.fields()
    val doneFolded = folded && item.finished
    CruxCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (doneFolded) 0.8f else 1f)
            .testTag("session_exercise"),
        outline = when {
            timerRunning -> TimerColors.work
            current -> colors.primary
            else -> null
        },
    ) {
        // The header folds and unfolds the card; ⋯ holds what applies to the whole exercise.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleFold)) {
            Column(Modifier.weight(1f)) {
                Text(item.exercise.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("session_exercise_name"))
                if (!doneFolded) {
                    Text(
                        item.target.prescription(metric, imperial) +
                            if (item.target.restSeconds > 0) " · ${formatDuration(item.target.restSeconds)} rest" else "",
                        style = CruxTheme.type.code,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (item.finished) {
                // Done: a green tick and the count, beside the name.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("session_exercise_done")) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = CruxTheme.colors.success, modifier = Modifier.size(16.dp))
                    Text(
                        " ${item.done} ${if (metric.usesIntervals) "cycle" else "set"}${if (item.done == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = CruxTheme.colors.success,
                    )
                }
            }
            if (newBest) {
                Icon(Icons.Rounded.Star, contentDescription = "New best", tint = colors.secondary, modifier = Modifier.size(20.dp).testTag("session_new_best"))
            }
            IconButton(onClick = onMenu, modifier = Modifier.testTag("session_exercise_menu")) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = "${item.exercise.name}: more")
            }
        }
        AnimatedVisibility(visible = !folded, enter = Reveal, exit = Hide) {
            Column(verticalArrangement = Arrangement.spacedBy(space.s1)) {
                // Column heads: set, last time, then each value.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = space.s2)) {
                    HeadCell(if (metric.usesIntervals) "Cycle" else "Set", Modifier.width(44.dp))
                    HeadCell("Last", Modifier.weight(1.2f))
                    fields.forEach { HeadCell(it.label, Modifier.weight(1f)) }
                    Box(Modifier.width(48.dp))
                }
                val next = item.nextSet
                (0 until maxOf(item.target.sets, item.logged)).forEach { setIndex ->
                    val logged = item.sets.firstOrNull { it.setIndex == setIndex }
                    val shown = values(setIndex)
                    val last = previous?.firstOrNull { it.setIndex == setIndex && !it.skipped }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (setIndex == next) colors.surfaceContainerHigh else Color.Transparent)
                            .padding(vertical = 2.dp)
                            .testTag("session_set_$setIndex"),
                    ) {
                        Text(
                            (setIndex + 1).toString(),
                            style = CruxTheme.type.code,
                            color = if (setIndex == next) colors.primary else colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(44.dp),
                        )
                        Text(
                            last?.let { describeSet(metric, it.reps, it.seconds, it.loadKg, imperial) } ?: "–",
                            style = CruxTheme.type.code,
                            color = colors.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1.2f),
                        )
                        if (logged?.skipped == true) {
                            Text(
                                "Skipped",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.outline,
                                modifier = Modifier.weight(fields.size.toFloat().coerceAtLeast(1f)),
                            )
                        } else {
                            fields.forEachIndexed { index, field ->
                                val isOpen = open == OpenCell(item.id, setIndex, index)
                                ValueCell(
                                    text = field.show(shown, imperial),
                                    open = isOpen,
                                    done = logged != null,
                                    onClick = { onOpen(if (isOpen) null else OpenCell(item.id, setIndex, index)) },
                                    modifier = Modifier.weight(1f).testTag("session_value_${setIndex}_${field.name}"),
                                )
                            }
                        }
                        TickBox(
                            done = logged != null && !logged.skipped,
                            running = timerRunning && setIndex == next,
                            onClick = {
                                if (logged == null) haptics.confirm()
                                onTick(setIndex)
                            },
                            modifier = Modifier.testTag("session_tick_$setIndex"),
                        )
                    }
                    // The open value's stepper sits right under its row.
                    val openHere = open?.takeIf { it.itemId == item.id && it.setIndex == setIndex }
                    val lastOpen = remember { mutableStateOf<OpenCell?>(null) }
                    if (openHere != null) lastOpen.value = openHere
                    AnimatedVisibility(visible = openHere != null, enter = Reveal, exit = Hide) {
                        lastOpen.value?.let { cell ->
                            Box(Modifier.fillMaxWidth().padding(vertical = space.s1), contentAlignment = Alignment.Center) {
                                ValueStepper(fields[cell.field], shown, imperial) { onValues(setIndex, it) }
                            }
                        }
                    }
                }
                CruxButton(
                    if (metric.usesIntervals) "Add cycle" else "Add set",
                    onAddSet,
                    variant = CruxButtonVariant.Text,
                    size = CruxButtonSize.Small,
                    icon = Icons.Rounded.Add,
                    modifier = Modifier.testTag("session_add_set"),
                )
            }
        }
    }
}

/** How a part of a card opens and closes: it grows from the top and fades, at the app's pace. */
private val Reveal = expandVertically(CruxMotion.medium(), expandFrom = Alignment.Top) + fadeIn(CruxMotion.medium())
private val Hide = shrinkVertically(CruxMotion.medium(), shrinkTowards = Alignment.Top) + fadeOut(CruxMotion.fast())

@Composable
private fun HeadCell(text: String, modifier: Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = if (text == "Last") TextAlign.Start else TextAlign.Center,
        maxLines = 1,
        // Big text sizes shrink a heading to fit its column instead of running into the next.
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 7.sp, maxFontSize = MaterialTheme.typography.labelSmall.fontSize),
        modifier = modifier.padding(horizontal = 2.dp),
    )
}

/** A value in the table: a soft pill, outlined in the accent while it's open for changing. */
@Composable
private fun RowScope.ValueCell(text: String, open: Boolean, done: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(horizontal = 3.dp)
            .clip(shape)
            .background(colors.surfaceContainerHighest)
            .then(if (open) Modifier.border(CruxTheme.size.borderEmphasis, colors.primary, shape) else Modifier)
            .clickable(onClickLabel = "Change", onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(
            text,
            style = CruxTheme.type.code.copy(fontWeight = FontWeight.SemiBold),
            color = if (done) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 1,
        )
    }
}

/** The tick at the end of a row: empty to do, filled when done, a play mark while its timer runs. */
@Composable
private fun TickBox(done: Boolean, running: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val success = CruxTheme.colors.success
    // The whole 48dp square is the target; TalkBack reads it as the set's tick box.
    Box(
        modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = done, role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = { onClick() })
            .semantics { contentDescription = if (running) "Set, timer running" else "Set done" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (done) success else Color.Transparent)
                .border(
                    1.5.dp,
                    if (done) {
                        success
                    } else if (running) {
                        TimerColors.work
                    } else {
                        colors.outline
                    },
                    RoundedCornerShape(9.dp),
                ),
        ) {
            when {
                done -> Icon(Icons.Rounded.Check, contentDescription = null, tint = CruxTheme.colors.onSuccess, modifier = Modifier.size(18.dp))
                running -> Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = TimerColors.work, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** The stepper for one value, in the app's usual stepper. */
@Composable
private fun ValueStepper(field: Field, values: SetValues, imperial: Boolean, onChange: (SetValues) -> Unit) {
    when (field) {
        Field.REPS -> CruxStepper(values.reps, { onChange(values.copy(reps = it)) }, 0..200, "reps", testTagPrefix = "set_reps")

        Field.SECONDS -> CruxStepper(values.seconds, { onChange(values.copy(seconds = it)) }, 0..3600, "s", testTagPrefix = "set_seconds")

        Field.LOAD -> {
            val step = if (imperial) poundsToKg(2.5) else 1.25
            val shown = loadValue(values.loadKg, imperial)
            CruxValueStepper(
                display = (
                    if (values.loadKg > 0) {
                        "+"
                    } else if (values.loadKg < 0) {
                        "−"
                    } else {
                        ""
                    }
                    ) + formatLoad(values.loadKg, imperial),
                unit = loadUnit(imperial),
                canDecrease = shown > -100,
                canIncrease = shown < 300,
                onDecrease = { onChange(values.copy(loadKg = values.loadKg - step)) },
                onIncrease = { onChange(values.copy(loadKg = values.loadKg + step)) },
                testTagPrefix = "set_load",
            )
        }
    }
}

/**
 * The session's climbing block: "Climbing" and the place, its figures (climbs, sent, hardest) once
 * there's a climb, a row per climb with its goes added up, and Log climb. [current] edges it in the
 * accent while climbing is what's happening.
 */
@Composable
internal fun ClimbsCard(
    climbs: List<com.hardtekpt.crux.data.model.Climb>,
    onLogMore: (Long) -> Unit,
    onLogClimb: () -> Unit,
    place: String? = null,
    current: Boolean = false,
) {
    val sent = climbs.filter { it.style.isSend }
    val hardest = sent.maxByOrNull { it.gradeIndex }?.grade
    CruxCard(modifier = Modifier.fillMaxWidth().testTag("session_climbs"), outline = MaterialTheme.colorScheme.primary.takeIf { current }) {
        Text(
            listOfNotNull("Climbing", place).joinToString(" · "),
            style = MaterialTheme.typography.titleMedium,
        )
        if (climbs.isEmpty()) {
            Text("Climbs you log now land in this session.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            com.hardtekpt.crux.ui.components.StatStrip(
                listOf(
                    com.hardtekpt.crux.ui.components.StatFigure(climbs.size.toString(), if (climbs.size == 1) "climb" else "climbs"),
                    com.hardtekpt.crux.ui.components.StatFigure(sent.size.toString(), "sent"),
                    com.hardtekpt.crux.ui.components.StatFigure(hardest ?: "–", "hardest"),
                ),
                Modifier.padding(vertical = CruxTheme.space.s2).testTag("session_tally"),
            )
        }
        climbs.sortedByDescending { it.id }.forEach { climb -> SessionClimbLine(climb, onClick = climb.problemId?.let { id -> { onLogMore(id) } }) }
        CruxButton(
            "Log climb",
            onLogClimb,
            variant = CruxButtonVariant.Text,
            size = CruxButtonSize.Small,
            icon = Icons.Rounded.Add,
            modifier = Modifier.testTag("session_card_log_climb"),
        )
    }
}

/** A session with nothing in it yet opens on this: what to do first, each one a block. */
@Composable
internal fun FirstBlock(onLogClimb: () -> Unit, onPlaylist: (() -> Unit)?, onAddExercise: () -> Unit, onTimer: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s1), modifier = Modifier.testTag("session_first_block")) {
        com.hardtekpt.crux.ui.components.Eyebrow("What are you doing first?", Modifier.padding(bottom = CruxTheme.space.s1))
        com.hardtekpt.crux.ui.components.CruxListRow(
            title = "Climb",
            supporting = "Log climbs as you go",
            leading = { Icon(Icons.Rounded.Landscape, contentDescription = null) },
            onClick = onLogClimb,
            modifier = Modifier.testTag("first_climb"),
        )
        onPlaylist?.let {
            com.hardtekpt.crux.ui.components.CruxListRow(
                title = "Climb a playlist",
                supporting = "One climb after another, with + Fell and + Sent",
                leading = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null) },
                onClick = it,
                modifier = Modifier.testTag("first_playlist"),
            )
        }
        com.hardtekpt.crux.ui.components.CruxListRow(
            title = "An exercise",
            supporting = "From your library",
            leading = { Icon(Icons.Rounded.FitnessCenter, contentDescription = null) },
            onClick = onAddExercise,
            modifier = Modifier.testTag("first_exercise"),
        )
        com.hardtekpt.crux.ui.components.CruxListRow(
            title = "A timer",
            supporting = "A countdown, Tabata, repeaters or your own",
            leading = { Icon(Icons.Rounded.Timer, contentDescription = null) },
            onClick = onTimer,
            modifier = Modifier.testTag("first_timer"),
        )
        Text(
            "Add more at any time with +. Each becomes a block, in the order you add it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = CruxTheme.space.s2),
        )
    }
}

/** What applies to a whole exercise: change its targets, start its timer, skip it, take it out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExerciseMenuSheet(
    item: SessionItem,
    onChangeTargets: () -> Unit,
    onStartTimer: (() -> Unit)?,
    onSkip: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
    onAbout: (() -> Unit)? = null,
) {
    MenuSheet(item.exercise.name, onDismiss) {
        onAbout?.let {
            CruxListRow(
                title = "About this exercise",
                supporting = "Your record, chart and history",
                leading = { Icon(Icons.Rounded.Insights, contentDescription = null) },
                onClick = it,
                modifier = Modifier.testTag("session_exercise_about"),
            )
        }
        CruxListRow(
            title = "Change sets, reps and rest",
            supporting = "For this session only",
            leading = { Icon(Icons.Rounded.Tune, contentDescription = null) },
            onClick = onChangeTargets,
            modifier = Modifier.testTag("session_edit_target"),
        )
        onStartTimer?.let {
            CruxListRow(
                title = "Start its timer",
                supporting = "Each cycle ticks itself as it finishes",
                leading = { Icon(Icons.Rounded.Timer, contentDescription = null) },
                onClick = it,
                modifier = Modifier.testTag("session_start_timer"),
            )
        }
        onSkip?.let {
            CruxListRow(
                title = "Skip the rest of it",
                supporting = "The sets not done are marked skipped",
                leading = { Icon(Icons.Rounded.SkipNext, contentDescription = null) },
                onClick = it,
                modifier = Modifier.testTag("session_skip_exercise"),
            )
        }
        onRemove?.let {
            CruxListRow(
                title = "Take it out of this session",
                supporting = "Nothing is logged on it yet",
                leading = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = it,
                modifier = Modifier.testTag("session_remove_exercise"),
            )
        }
    }
}

/** The + by Finish: log a climb, add an exercise, or run a timer of its own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddToSessionSheet(
    onLogClimb: () -> Unit,
    onAddExercise: () -> Unit,
    onTimer: (() -> Unit)?,
    onDismiss: () -> Unit,
    onPlaylist: (() -> Unit)? = null,
) {
    MenuSheet("Add to this session", onDismiss) {
        CruxListRow(
            title = "Log a climb",
            leading = { Icon(Icons.Rounded.Terrain, contentDescription = null) },
            onClick = onLogClimb,
            modifier = Modifier.testTag("session_log_climb"),
        )
        onPlaylist?.let {
            CruxListRow(
                title = "Climb a playlist",
                supporting = "One climb after another, with + Fell and + Sent",
                leading = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null) },
                onClick = it,
                modifier = Modifier.testTag("session_playlist_start"),
            )
        }
        CruxListRow(
            title = "Add an exercise",
            leading = { Icon(Icons.Rounded.FitnessCenter, contentDescription = null) },
            onClick = onAddExercise,
            modifier = Modifier.testTag("session_add_exercise"),
        )
        CruxListRow(
            title = "Start a timer",
            supporting = if (onTimer == null) "A timer is already running" else "A countdown, Tabata, Repeaters or your own",
            leading = { Icon(Icons.Rounded.Timer, contentDescription = null) },
            enabled = onTimer != null,
            onClick = onTimer ?: {},
            modifier = Modifier.testTag("session_timer"),
        )
    }
}

/** Which playlist to climb in the session. */
@Composable
internal fun PlaylistPicker(playlists: List<com.hardtekpt.crux.data.Playlist>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    MenuSheet("Climb a playlist", onDismiss) {
        playlists.forEach { list ->
            CruxListRow(
                title = list.name,
                supporting = com.hardtekpt.crux.ui.datasets.playlistLine(list),
                onClick = { onPick(list.id) },
                modifier = Modifier.testTag("session_pick_${list.name}"),
            )
        }
    }
}

/** A bottom sheet of rows, as the app's other sheets look. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    com.hardtekpt.crux.ui.components.ActionSheet(title, onDismiss, Modifier.testTag("session_menu")) { content() }
}

/** A small dot of colour, for counts in the dock. */
@Composable
internal fun Dot(color: Color) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
}

/** "+15 kg" for a load, for short labels. */
internal fun loadLabel(kg: Double, imperial: Boolean) = signedLoad(kg, imperial)
