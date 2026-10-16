package com.hardtekpt.crux.ui.you

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.ExerciseRecord
import com.hardtekpt.crux.data.ExerciseRepository
import com.hardtekpt.crux.data.RecordRepository
import com.hardtekpt.crux.data.ScheduleRepository
import com.hardtekpt.crux.data.TemplateRepository
import com.hardtekpt.crux.data.best
import com.hardtekpt.crux.data.model.Exercise
import com.hardtekpt.crux.data.model.ExerciseTarget
import com.hardtekpt.crux.data.prefs.UnitSystem
import com.hardtekpt.crux.ui.LocalClock
import com.hardtekpt.crux.ui.LocalUnits
import com.hardtekpt.crux.ui.charts.formatTick
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.StatTile
import com.hardtekpt.crux.ui.components.listItemMotion
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.progress.ExerciseChartCard
import com.hardtekpt.crux.ui.progress.headlineShown
import com.hardtekpt.crux.ui.progress.headlineUnit
import com.hardtekpt.crux.ui.progress.trainingProgress
import com.hardtekpt.crux.ui.theme.Archivo
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A plan that uses the exercise: its target there and the days of the week it's on. */
data class PlanUse(val planId: Long, val planName: String, val target: ExerciseTarget, val days: List<DayOfWeek>)

data class ExercisePageState(val exercise: Exercise? = null, val results: List<ExerciseRecord> = emptyList(), val plans: List<PlanUse> = emptyList())

@HiltViewModel
class ExerciseRecordsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    exercises: ExerciseRepository,
    private val records: RecordRepository,
    templates: TemplateRepository? = null,
    schedule: ScheduleRepository? = null,
    private val undo: com.hardtekpt.crux.ui.UndoCenter? = null,
) : ViewModel() {
    val exerciseId: Long = savedStateHandle.get<Long>("exerciseId") ?: 0L

    val state: StateFlow<ExercisePageState> = combine(
        exercises.observeExercises(),
        records.observeRecords(exerciseId),
        templates?.observeTemplates() ?: flowOf(emptyList()),
        schedule?.observeWeek() ?: flowOf(emptyList()),
    ) { list, mine, plans, week ->
        ExercisePageState(
            exercise = list.firstOrNull { it.id == exerciseId },
            results = mine,
            plans = plans.mapNotNull { plan ->
                val item = plan.blocks.flatMap { it.items }.firstOrNull { it.exercise.id == exerciseId } ?: return@mapNotNull null
                PlanUse(plan.id, plan.name, item.target, week.filter { it.planId == plan.id }.map { it.day }.distinct().sorted())
            },
        )
    }
        .flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExercisePageState())

    /** Deletes a result at once, with Undo. */
    fun delete(id: Long) {
        viewModelScope.launch {
            val restore = records.deleteRecordUndoable(id)
            if (restore != null) undo?.offer("Deleted the result", undo = restore)
        }
    }
}

/**
 * Everything about one exercise on one page: its record, Log a result (and its timer for interval
 * work), its chart and three figures, how it's set up, the plans that use it, and every day it was
 * trained. The pencil opens the editor. Every list of exercises opens this page.
 */
@Composable
fun ExerciseRecordsScreen(
    onBack: () -> Unit,
    onAdd: (Long) -> Unit,
    viewModel: ExerciseRecordsViewModel = hiltViewModel(),
    onEdit: (Long) -> Unit = {},
    onOpenPlan: (Long) -> Unit = {},
    onTimer: ((Long) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exercise = state.exercise
    val results = state.results
    val space = CruxTheme.space
    val colors = MaterialTheme.colorScheme
    var seen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (exercise != null) seen = true
    if (seen && exercise == null) androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
    Column(Modifier.fillMaxSize().testTag("screen_ExerciseRecords")) {
        CruxTopAppBar(title = exercise?.name.orEmpty(), onBack = onBack) {
            if (exercise != null) {
                IconButton(onClick = { onEdit(exercise.id) }, modifier = Modifier.testTag("exercise_edit")) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit ${exercise.name}")
                }
            }
        }
        val ex = exercise ?: return@Column
        val metric = ex.metric
        val pr = metric.best(results)
        val today = LocalDate.now(LocalClock.current)
        val imperial = LocalUnits.current == UnitSystem.IMPERIAL
        val progress = remember(ex, results, today) {
            trainingProgress(emptyList(), results, listOf(ex), emptyList(), emptySet(), today, java.time.ZoneId.systemDefault()).exercises.firstOrNull()
        }
        LazyColumn(
            contentPadding = PaddingValues(start = space.s4, end = space.s4, bottom = space.s4 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            item(key = "kind") {
                Text(
                    listOfNotNull(ex.category.label, metric.label).joinToString(" · "),
                    style = CruxTheme.type.code,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.testTag("exercise_kind"),
                )
            }
            item(key = "record") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.s3)) {
                    Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = colors.secondary)
                    Column(Modifier.weight(1f)) {
                        Text("PERSONAL RECORD", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        Text(
                            pr?.describe(metric, imperial) ?: "–",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                            modifier = Modifier.testTag("exercise_pr"),
                        )
                        Text(
                            pr?.let { "Set ${it.date.dayLabel()}" } ?: "Log a result or do it in a session to see your progress.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                    CruxButton("Log a result", {
                        onAdd(ex.id)
                    }, icon = Icons.Rounded.Add, variant = CruxButtonVariant.Tonal, modifier = Modifier.testTag("add_record"))
                    if (onTimer != null && metric.usesIntervals) {
                        CruxButton("Timer", {
                            onTimer(ex.id)
                        }, icon = Icons.Rounded.Timer, variant = CruxButtonVariant.Outlined, modifier = Modifier.testTag("exercise_timer"))
                    }
                }
            }
            // Its best set per day, or how much was done, over time, with three figures under it.
            progress?.let { p ->
                item(key = "chart") { ExerciseChartCard(p, today, Modifier.padding(top = space.s2)) }
                item(key = "figures") {
                    val since = today.minusDays(91)
                    val before = metric.best(results.filter { it.date.isBefore(since) })
                    val moved = before?.let { metric.headlineShown(p.best, imperial) - metric.headlineShown(it, imperial) }
                    Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                        StatTile(label = "Days", value = p.days.size.toString(), delta = "trained", modifier = Modifier.weight(1f))
                        StatTile(label = "Results", value = results.size.toString(), delta = "sets and logs", modifier = Modifier.weight(1f))
                        StatTile(
                            label = "In 3 months",
                            value = moved?.let { (if (it > 0) "+" else "") + formatTick(it) } ?: "–",
                            unit = moved?.let { metric.headlineUnit(imperial) },
                            delta = if (moved == null) "not long enough" else "on your best",
                            isPersonalBest = (moved ?: 0.0) > 0,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            // How plans and sessions start it, read-only; the pencil changes it.
            item(key = "setup") {
                Eyebrow("How it's set up", Modifier.padding(top = space.s3))
            }
            item(key = "setup_card") {
                CruxCard(modifier = Modifier.testTag("exercise_setup")) {
                    SetupLine("Starts at", ExerciseTarget.defaultFor(ex).prescription(metric, imperial))
                    SetupLine("Measures", metric.label)
                    ex.notes?.takeIf { it.isNotBlank() }?.let { SetupLine("Notes", it) }
                }
            }
            if (state.plans.isNotEmpty()) {
                item(key = "plans") { Eyebrow("In your plans · ${state.plans.size}", Modifier.padding(top = space.s3)) }
                items(state.plans, key = { "plan_${it.planId}" }) { use ->
                    CruxListRow(
                        title = use.planName,
                        supporting = listOf(
                            if (use.days.isEmpty()) "Not on the week" else use.days.joinToString(", ") { it.getDisplayName(DateTextStyle.SHORT, Locale.UK) },
                            use.target.prescription(metric, imperial),
                        ).joinToString(" · "),
                        onClick = { onOpenPlan(use.planId) },
                        modifier = Modifier.testTag("exercise_plan"),
                    )
                }
            }
            // Each day it was trained: how many results, and the best of them.
            val days = progress?.days.orEmpty()
            item(key = "days") { Eyebrow("By day · ${days.size}", Modifier.padding(top = space.s3)) }
            items(days, key = { it.date.toEpochDay() }) { day ->
                val single = day.best.takeIf { day.results == 1 && !it.fromSession }
                CruxListRow(
                    title = day.date.dayLabel(),
                    supporting = listOfNotNull(
                        when {
                            day.results == 1 && day.fromSession -> "1 set in a session"
                            day.fromSession -> "${day.results} sets in a session"
                            day.results == 1 -> "Logged by hand"
                            else -> "${day.results} results logged by hand"
                        },
                        "best ${day.best.describe(metric, imperial)}",
                        single?.notes,
                    ).joinToString(" · "),
                    leading = if (day.isBest) {
                        { Icon(Icons.Rounded.EmojiEvents, contentDescription = "Personal record", tint = colors.secondary) }
                    } else {
                        null
                    },
                    // A set from a session is changed in its session, so only a day with one hand-logged result can go here.
                    trailing = single?.let { record ->
                        { IconButton(onClick = { viewModel.delete(record.id) }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete result") } }
                    },
                    selected = day.isBest,
                    modifier = listItemMotion().heightIn(min = 56.dp).testTag("record_result"),
                )
            }
        }
    }
}

@Composable
private fun SetupLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp).weight(0.35f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.65f))
    }
}
