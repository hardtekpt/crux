package com.hardtekpt.crux.ui.you

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Straighten
import com.hardtekpt.crux.data.ClimbRepository
import com.hardtekpt.crux.data.ExerciseBest
import com.hardtekpt.crux.data.Note
import com.hardtekpt.crux.data.NoteRepository
import com.hardtekpt.crux.data.RecordRepository
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.PersonalBest
import com.hardtekpt.crux.data.prefs.GradeScales
import com.hardtekpt.crux.data.prefs.ThemeMode
import com.hardtekpt.crux.data.prefs.UnitSystem
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.CruxCardFill
import java.time.Clock
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.BodyRepository
import com.hardtekpt.crux.data.model.Measurement
import com.hardtekpt.crux.data.model.MeasurementType
import com.hardtekpt.crux.ui.WeightSummary
import com.hardtekpt.crux.ui.charts.ChartCard
import com.hardtekpt.crux.ui.charts.ChartRange
import com.hardtekpt.crux.ui.charts.SeriesPoint
import com.hardtekpt.crux.ui.charts.TimeSeriesChart
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxSegmentedButtons
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.LocalUnits
import com.hardtekpt.crux.ui.components.input.RulerInput
import com.hardtekpt.crux.ui.lengthDifference
import com.hardtekpt.crux.ui.measureInput
import com.hardtekpt.crux.ui.measurement
import com.hardtekpt.crux.ui.typicalValue
import com.hardtekpt.crux.ui.weight
import com.hardtekpt.crux.ui.weightChange
import com.hardtekpt.crux.ui.weightUnit
import com.hardtekpt.crux.ui.weightValue
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.InlineEmptyState
import com.hardtekpt.crux.ui.components.StatTile
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.oneDecimal
import com.hardtekpt.crux.ui.shortLabel
import com.hardtekpt.crux.ui.signedOneDecimal
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.weightSummary
import com.hardtekpt.crux.ui.wholeOrOneDecimal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class YouUiState(
    val isLoading: Boolean = true,
    val weights: List<Measurement> = emptyList(),
    val summary: WeightSummary? = null,
    /** Newest value of each body stat. */
    val latest: Map<MeasurementType, Measurement> = emptyMap(),
    /** Climbs and recorded results per day, for the consistency grid. */
    val activity: Map<LocalDate, Int> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
    val climbingSince: LocalDate? = null,
    val daysThisYear: Int = 0,
    val climbsThisYear: Int = 0,
    /** Weeks in a row, up to this one, with at least one day on the wall. */
    val weekStreak: Int = 0,
    val bestWeekStreak: Int = 0,
    val daysLast30: Int = 0,
    val hardestBoulder: PersonalBest? = null,
    val hardestRoute: PersonalBest? = null,
    val records: List<ExerciseBest> = emptyList(),
    val notes: List<Note> = emptyList(),
    val units: UnitSystem = UnitSystem.METRIC,
    val scales: GradeScales = GradeScales(),
    val theme: ThemeMode = ThemeMode.DARK,
) {
    val apeIndex: ApeIndex? get() = apeIndex(latest[MeasurementType.WINGSPAN]?.value, latest[MeasurementType.HEIGHT]?.value)
}

/** Wingspan minus height, and their ratio; climbers quote both. */
data class ApeIndex(val differenceCm: Double, val ratio: Double)

fun apeIndex(wingspanCm: Double?, heightCm: Double?): ApeIndex? =
    if (wingspanCm == null || heightCm == null || heightCm <= 0) null else ApeIndex(wingspanCm - heightCm, wingspanCm / heightCm)

/** Weeks in a row ending at [lastWeek] with any activity, and the longest such run. */
fun weekStreaks(activeDays: Set<LocalDate>, lastWeek: LocalDate): Pair<Int, Int> {
    if (activeDays.isEmpty()) return 0 to 0
    val monday = { d: LocalDate -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val weeks = activeDays.map(monday).toSortedSet()
    var best = 0
    var run = 0
    var previous: LocalDate? = null
    for (week in weeks) {
        run = if (previous != null && previous.plusWeeks(1) == week) run + 1 else 1
        best = maxOf(best, run)
        previous = week
    }
    // The current run counts this week if active, else up to last week (this week isn't over).
    var current = 0
    var week = monday(lastWeek)
    if (week !in weeks) week = week.minusWeeks(1)
    while (week in weeks) {
        current++
        week = week.minusWeeks(1)
    }
    return current to best
}

@HiltViewModel
class YouViewModel @Inject constructor(
    private val bodyRepository: BodyRepository,
    climbRepository: ClimbRepository,
    recordRepository: RecordRepository,
    noteRepository: NoteRepository,
    preferences: UserPreferencesRepository,
    clock: Clock,
) : ViewModel() {
    private val today = LocalDate.now(clock)

    private data class Body(val weights: List<Measurement>, val latest: Map<MeasurementType, Measurement>)
    private data class Prefs(val units: UnitSystem, val scales: GradeScales, val theme: ThemeMode)

    private val body = combine(bodyRepository.observeWeights(), bodyRepository.observeLatest(), ::Body)
    private val prefs = combine(preferences.units, preferences.gradeScales, preferences.themeMode, ::Prefs)

    val uiState: StateFlow<YouUiState> = combine(
        body,
        climbRepository.observeClimbs(),
        climbRepository.observePersonalBests(),
        combine(recordRepository.observeBests(), noteRepository.observeNotes(), ::Pair),
        prefs,
    ) { body, climbs, bests, (records, notes), prefs ->
        val climbDays = climbs.groupingBy { it.date }.eachCount()
        val recordDays = records.flatMap { listOf(it.best.date) }.groupingBy { it }.eachCount()
        val activity = (climbDays.keys + recordDays.keys).associateWith { (climbDays[it] ?: 0) + (recordDays[it] ?: 0) }
        val (streak, bestStreak) = weekStreaks(climbDays.keys, today)
        fun hardest(discipline: Discipline): PersonalBest? {
            val standard = bests.filter { it.discipline == discipline && !it.gradeScale.isLocal }
            return standard.filter { it.gradeScale == prefs.scales.forDiscipline(discipline) }.maxByOrNull { it.gradeIndex }
                ?: standard.maxByOrNull { it.gradeIndex }
        }
        YouUiState(
            isLoading = false,
            weights = body.weights,
            summary = body.weights.weightSummary(),
            latest = body.latest,
            activity = activity,
            today = today,
            climbingSince = climbs.minOfOrNull { it.date },
            daysThisYear = climbDays.keys.count { it.year == today.year },
            climbsThisYear = climbs.count { it.date.year == today.year },
            weekStreak = streak,
            bestWeekStreak = bestStreak,
            daysLast30 = climbDays.keys.count { !it.isBefore(today.minusDays(29)) && !it.isAfter(today) },
            hardestBoulder = hardest(Discipline.BOULDER),
            hardestRoute = hardest(Discipline.ROUTE),
            records = records,
            notes = notes,
            units = prefs.units,
            scales = prefs.scales,
            theme = prefs.theme,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YouUiState(today = today))

    fun setMeasurement(type: MeasurementType, value: Double) {
        viewModelScope.launch { bodyRepository.setMeasurement(type, value) }
    }
}

/** Parses a body stat as typed (comma or point decimals) and checks it against the type's range. */
fun parseMeasurement(type: MeasurementType, text: String): Result<Double> {
    val value = text.replace(',', '.').trim().toDoubleOrNull()
    if (value != null && value in type.range) return Result.success(value)
    val unit = if (type.unit == "%") "%" else " ${type.unit}"
    val low = type.range.start.wholeOrOneDecimal()
    val high = type.range.endInclusive.wholeOrOneDecimal()
    return Result.failure(IllegalArgumentException("Enter a ${type.label.lowercase()} between $low and $high$unit"))
}

/** Where the profile's taps lead. */
data class ProfileActions(
    val logWeight: () -> Unit = {},
    val openSettings: () -> Unit = {},
    val newNote: () -> Unit = {},
    val openNote: (Long) -> Unit = {},
    val openNotes: () -> Unit = {},
    val logRecord: () -> Unit = {},
    val openRecords: (Long) -> Unit = {},
)

@Composable
fun YouScreen(
    actions: ProfileActions,
    viewModel: YouViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    YouContent(
        uiState = uiState,
        actions = actions,
        onSetMeasurement = viewModel::setMeasurement,
    )
}

/**
 * The climber's profile, top to bottom: a climber card with the headline numbers; how
 * consistent you've been; personal records; measurements (weight, body, circumferences);
 * notes; and preferences.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouContent(
    uiState: YouUiState,
    actions: ProfileActions,
    onSetMeasurement: (MeasurementType, Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val space = CruxTheme.space
    val units = LocalUnits.current
    var editing by rememberSaveable { mutableStateOf<MeasurementType?>(null) }
    var allWeights by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("screen_You"),
    ) {
        CruxTopAppBar(
            title = "You",
            scrollBehavior = scrollBehavior,
            actions = {
                IconButton(onClick = actions.openSettings, modifier = Modifier.testTag("open_settings")) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            },
        )
        LazyColumn(
            modifier = Modifier.testTag("you_list"),
            contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s1, bottom = space.s6 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            item(key = "card") {
                ClimberCard(
                    daysThisYear = uiState.daysThisYear,
                    climbsThisYear = uiState.climbsThisYear,
                    climbingSince = uiState.climbingSince,
                    hardestBoulder = uiState.hardestBoulder,
                    hardestRoute = uiState.hardestRoute,
                )
            }

            // ---- Consistency
            item(key = "consistency_header") { ProfileSectionHeader("Consistency") }
            item(key = "consistency") {
                CruxCard(fill = CruxCardFill.Low) {
                    ConsistencyGrid(uiState.activity, uiState.today)
                    Row(horizontalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.padding(top = space.s3)) {
                        ProfileStat("${uiState.weekStreak} wk", "Streak", Modifier.weight(1f), highlight = uiState.weekStreak > 0)
                        ProfileStat("${uiState.bestWeekStreak} wk", "Best streak", Modifier.weight(1f))
                        ProfileStat(uiState.daysLast30.toString(), "Days, 30d", Modifier.weight(1f))
                    }
                }
            }

            // ---- Personal records
            item(key = "records_header") { ProfileSectionHeader("Personal records", action = "Log a record", onAction = actions.logRecord) }
            if (uiState.records.isEmpty()) {
                item(key = "records_empty") {
                    InlineEmptyState(icon = Icons.Rounded.EmojiEvents, text = "Log a result on an exercise, like a max hang or a 1-arm pull, and your best shows here.")
                }
            }
            items(uiState.records, key = { "record_${it.exercise.id}" }) { best ->
                RecordRow(best, onClick = { actions.openRecords(best.exercise.id) })
            }

            // ---- Measurements
            item(key = "measure_header") { ProfileSectionHeader("Measurements", action = "Log weight", onAction = actions.logWeight) }
            item(key = "weight") {
                val summary = uiState.summary
                StatTile(
                    label = "Weight",
                    value = summary?.latest?.value?.let { units.weight(it).value } ?: "–",
                    unit = summary?.let { units.weightUnit() },
                    delta = when {
                        summary == null -> "no weigh-ins yet"
                        summary.change != null -> "${units.weightChange(summary.change)} · ${summary.window}"
                        else -> "weighed ${summary.latest.date.shortLabel()}"
                    },
                    valueModifier = Modifier.testTag("you_weight"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .clickable(onClick = actions.logWeight)
                        .testTag("log_weight"),
                )
            }
            if (uiState.weights.isNotEmpty()) {
                item(key = "weight_chart") { WeightTrendCard(uiState.weights) }
            }
            item(key = "body_label") { Eyebrow("Body · tap to update", Modifier.padding(top = space.s2)) }
            item(key = "body") { BodyStatGrid(uiState, onEdit = { editing = it }) }
            item(key = "girth_label") { Eyebrow("Circumferences", Modifier.padding(top = space.s2)) }
            item(key = "girth") {
                val types = listOf(MeasurementType.FOREARM, MeasurementType.BICEP, MeasurementType.CHEST, MeasurementType.WAIST, MeasurementType.THIGH)
                Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
                    types.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(space.s3), modifier = Modifier.height(IntrinsicSize.Min)) {
                            pair.forEach { BodyStatTile(it, uiState, { editing = it }, Modifier.weight(1f)) }
                            if (pair.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
            }
            if (uiState.weights.isNotEmpty()) {
                item(key = "weight_history_label") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = space.s2)) {
                        Eyebrow("Weigh-ins", Modifier.weight(1f))
                        if (uiState.weights.size > 3) {
                            CruxButton(if (allWeights) "Show fewer" else "Show all ${uiState.weights.size}", { allWeights = !allWeights }, variant = CruxButtonVariant.Text, size = CruxButtonSize.Small)
                        }
                    }
                }
            }
            val shownWeights = if (allWeights) uiState.weights else uiState.weights.take(3)
            itemsIndexed(shownWeights, key = { _, it -> "w_${it.id}" }) { index, entry ->
                val previous = uiState.weights.getOrNull(uiState.weights.indexOf(entry) + 1)
                CruxListRow(
                    title = entry.date.dayLabel(),
                    supporting = previous?.let { "${units.weightChange(entry.value - it.value)} from ${it.date.shortLabel()}" },
                    trailing = {
                        Text(units.weight(entry.value).toString(), style = CruxTheme.type.grade, color = MaterialTheme.colorScheme.onSurface)
                    },
                    modifier = Modifier.testTag("weight_row"),
                )
            }

            // ---- Notes
            item(key = "notes_header") {
                ProfileSectionHeader("Notes", action = if (uiState.notes.size > 3) "All ${uiState.notes.size}" else "New note", onAction = if (uiState.notes.size > 3) actions.openNotes else actions.newNote)
            }
            if (uiState.notes.isEmpty()) {
                item(key = "notes_empty") {
                    InlineEmptyState(icon = Icons.AutoMirrored.Rounded.Notes, text = "Jot down how training feels, a niggle to watch or beta to remember. Add one from the Log button too.")
                }
            }
            items(uiState.notes.take(3), key = { "note_${it.id}" }) { note ->
                NoteCard(note, onClick = { actions.openNote(note.id) })
            }

            // ---- Preferences
            item(key = "prefs_header") { ProfileSectionHeader("Preferences") }
            item(key = "prefs") {
                CruxCard(fill = CruxCardFill.Low, modifier = Modifier.testTag("preferences")) {
                    Column(Modifier.padding(vertical = space.s1).fillMaxWidth()) {
                        PreferenceRow(Icons.Rounded.Straighten, "Units", uiState.units.label, actions.openSettings)
                        PreferenceRow(Icons.Rounded.Leaderboard, "Grades", "${uiState.scales.boulder.label} · ${uiState.scales.route.label}", actions.openSettings)
                        PreferenceRow(Icons.Rounded.Palette, "Theme", uiState.theme.label, actions.openSettings)
                        PreferenceRow(Icons.Rounded.Backup, "Data and backup", "Demo, export, import", actions.openSettings)
                    }
                }
            }
        }
    }

    editing?.let { type ->
        MeasurementDialog(
            type = type,
            initial = uiState.latest[type]?.value,
            onDismiss = { editing = null },
            onSave = {
                onSetMeasurement(type, it)
                editing = null
            },
        )
    }
}

/** Bodyweight over a chosen window, with the change across it in the header. */
@Composable
private fun WeightTrendCard(weights: List<Measurement>, modifier: Modifier = Modifier) {
    val units = LocalUnits.current
    var range by rememberSaveable { mutableStateOf(ChartRange.Quarter) }
    val today = LocalDate.now()
    val start = range.start(today)
    val inRange = weights.filter { start == null || !it.date.isBefore(start) }.sortedBy { it.date }
    val change = if (inRange.size > 1) inRange.last().value - inRange.first().value else null

    ChartCard(
        title = "Bodyweight",
        trailing = change?.let { "${units.weightChange(it)} over ${range.label}" },
        modifier = modifier.testTag("weight_chart_card"),
    ) {
        CruxSegmentedButtons(
            options = ChartRange.entries,
            selected = range,
            label = { it.label },
            onSelect = { range = it },
            modifier = Modifier.padding(bottom = CruxTheme.space.s3),
        )
        if (inRange.size < 2) {
            Text(
                "Log another weigh-in in this window to see the trend.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = CruxTheme.space.s6),
            )
        } else {
            TimeSeriesChart(
                points = inRange.map { SeriesPoint(it.date.toEpochDay().toDouble(), units.weightValue(it.value)) },
                formatX = { LocalDate.ofEpochDay(it.toLong()).shortLabel() },
                formatY = { it.oneDecimal() },
                unit = " ${units.weightUnit()}",
                description = "Bodyweight from ${units.weight(inRange.first().value)} to ${units.weight(inRange.last().value)} " +
                    "between ${inRange.first().date.shortLabel()} and ${inRange.last().date.shortLabel()}",
            )
        }
    }
}

/** Height, wingspan, ape index, reach and body fat as tiles, two per row. */
@Composable
private fun BodyStatGrid(uiState: YouUiState, onEdit: (MeasurementType) -> Unit) {
    val space = CruxTheme.space
    val ape = uiState.apeIndex
    val units = LocalUnits.current
    Column(verticalArrangement = Arrangement.spacedBy(space.s3)) {
        Row(horizontalArrangement = Arrangement.spacedBy(space.s3), modifier = Modifier.height(IntrinsicSize.Min)) {
            BodyStatTile(MeasurementType.HEIGHT, uiState, onEdit, Modifier.weight(1f))
            BodyStatTile(MeasurementType.WINGSPAN, uiState, onEdit, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space.s3), modifier = Modifier.height(IntrinsicSize.Min)) {
            StatTile(
                label = "Ape index",
                value = ape?.differenceCm?.let { units.lengthDifference(it).value } ?: "–",
                unit = ape?.differenceCm?.let { units.lengthDifference(it).unit },
                delta = ape?.let { "ratio ${String.format(java.util.Locale.UK, "%.2f", it.ratio)}" } ?: "add height and wingspan",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                valueModifier = Modifier.testTag("you_ape_index"),
            )
            BodyStatTile(MeasurementType.STANDING_REACH, uiState, onEdit, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space.s3), modifier = Modifier.height(IntrinsicSize.Min)) {
            BodyStatTile(MeasurementType.BODY_FAT, uiState, onEdit, Modifier.weight(1f))
            Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun BodyStatTile(
    type: MeasurementType,
    uiState: YouUiState,
    onEdit: (MeasurementType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latest = uiState.latest[type]
    val shown = latest?.let { LocalUnits.current.measurement(type, it.value) }
    StatTile(
        label = type.label,
        value = shown?.value ?: "–",
        unit = shown?.unit,
        delta = latest?.let { "set ${it.date.shortLabel()}" } ?: "tap to add",
        modifier = modifier
            .fillMaxHeight()
            .clip(MaterialTheme.shapes.large)
            .clickable { onEdit(type) }
            .testTag("stat_${type.name}"),
        valueModifier = Modifier.testTag("you_${type.name.lowercase()}"),
    )
}


/** Sets a body stat on a ruler, opened on the last value (or a typical one), in the climber's units. */
@Composable
private fun MeasurementDialog(type: MeasurementType, initial: Double?, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    val units = LocalUnits.current
    val input = remember(type, units) { units.measureInput(type) }
    val start = input.toDisplay(initial ?: type.typicalValue())
    var shown by rememberSaveable(type, units) { mutableDoubleStateOf(input.scale.valueAt(input.scale.indexOf(start))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(type.label, style = MaterialTheme.typography.headlineSmall) },
        text = {
            RulerInput(
                title = type.description,
                value = shown,
                onValueChange = { shown = it },
                scale = input.scale,
                display = input.display,
                unit = input.unit,
                delta = initial?.let { "Last set ${units.measurement(type, it)}" } ?: "Drag the scale, or tap the number to type",
                parseTyped = input.parse,
                typeUnit = input.typeUnit,
                testTag = "ruler_measurement",
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(input.toStored(shown).coerceIn(type.range)) },
                modifier = Modifier.testTag("save_measurement"),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
