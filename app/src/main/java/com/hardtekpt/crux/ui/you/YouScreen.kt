package com.hardtekpt.crux.ui.you

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
import androidx.compose.runtime.mutableStateOf
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
import com.hardtekpt.crux.ui.components.CruxTextField
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
) {
    val apeIndex: ApeIndex? get() = apeIndex(latest[MeasurementType.WINGSPAN]?.value, latest[MeasurementType.HEIGHT]?.value)
}

/** Wingspan minus height, and their ratio; climbers quote both. */
data class ApeIndex(val differenceCm: Double, val ratio: Double)

fun apeIndex(wingspanCm: Double?, heightCm: Double?): ApeIndex? =
    if (wingspanCm == null || heightCm == null || heightCm <= 0) null else ApeIndex(wingspanCm - heightCm, wingspanCm / heightCm)

@HiltViewModel
class YouViewModel @Inject constructor(
    private val bodyRepository: BodyRepository,
) : ViewModel() {
    val uiState: StateFlow<YouUiState> = combine(
        bodyRepository.observeWeights(),
        bodyRepository.observeLatest(),
    ) { weights, latest ->
        YouUiState(isLoading = false, weights = weights, summary = weights.weightSummary(), latest = latest)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YouUiState())

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

@Composable
fun YouScreen(
    onLogWeight: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: YouViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    YouContent(
        uiState = uiState,
        onLogWeight = onLogWeight,
        onSetMeasurement = viewModel::setMeasurement,
        onOpenSettings = onOpenSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouContent(
    uiState: YouUiState,
    onLogWeight: () -> Unit,
    onSetMeasurement: (MeasurementType, Double) -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val space = CruxTheme.space
    var editing by rememberSaveable { mutableStateOf<MeasurementType?>(null) }

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
                IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("open_settings")) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                }
            },
        )
        LazyColumn(
            modifier = Modifier.testTag("you_list"),
            contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s1, bottom = space.s4 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s2),
        ) {
            item { Eyebrow("Weight") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(space.s3), verticalAlignment = Alignment.CenterVertically) {
                    val summary = uiState.summary
                    StatTile(
                        label = "Weight",
                        value = summary?.latest?.value?.oneDecimal() ?: "–",
                        unit = summary?.let { "kg" },
                        delta = when {
                            summary == null -> "no weigh-ins yet"
                            summary.change != null -> "${summary.change.signedOneDecimal()} kg · ${summary.window}"
                            else -> "weighed ${summary.latest.date.shortLabel()}"
                        },
                        modifier = Modifier.weight(1f),
                        valueModifier = Modifier.testTag("you_weight"),
                    )
                    // Outlined on purpose: logging a measurement is not this screen's main job.
                    CruxButton(
                        text = "Log weight",
                        onClick = onLogWeight,
                        variant = CruxButtonVariant.Outlined,
                        icon = Icons.Rounded.Add,
                        modifier = Modifier.testTag("log_weight"),
                    )
                }
            }
            if (uiState.weights.isNotEmpty()) {
                item { WeightTrendCard(uiState.weights) }
            }
            item { Eyebrow("Climbing body · tap to update", Modifier.padding(top = space.s3)) }
            item { BodyStatGrid(uiState, onEdit = { editing = it }) }
            item { Eyebrow("Weight history", Modifier.padding(top = space.s3)) }
            if (!uiState.isLoading && uiState.weights.isEmpty()) {
                item {
                    InlineEmptyState(
                        icon = Icons.Rounded.MonitorWeight,
                        text = "No weigh-ins yet. Tap Log weight to start tracking.",
                    )
                }
            }
            itemsIndexed(uiState.weights, key = { _, it -> it.id }) { index, entry ->
                val previous = uiState.weights.getOrNull(index + 1)
                CruxListRow(
                    title = entry.date.dayLabel(),
                    supporting = previous?.let { "${(entry.value - it.value).signedOneDecimal()} kg from ${it.date.shortLabel()}" },
                    trailing = {
                        Text(
                            "${entry.value.oneDecimal()} kg",
                            style = CruxTheme.type.grade,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    modifier = Modifier.testTag("weight_row"),
                )
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
    var range by rememberSaveable { mutableStateOf(ChartRange.Quarter) }
    val today = LocalDate.now()
    val start = range.start(today)
    val inRange = weights.filter { start == null || !it.date.isBefore(start) }.sortedBy { it.date }
    val change = if (inRange.size > 1) inRange.last().value - inRange.first().value else null

    ChartCard(
        title = "Bodyweight",
        trailing = change?.let { "${it.signedOneDecimal()} kg over ${range.label}" },
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
                points = inRange.map { SeriesPoint(it.date.toEpochDay().toDouble(), it.value) },
                formatX = { LocalDate.ofEpochDay(it.toLong()).shortLabel() },
                formatY = { it.oneDecimal() },
                unit = " kg",
                description = "Bodyweight from ${inRange.first().value.oneDecimal()} to ${inRange.last().value.oneDecimal()} kg " +
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
    Column(verticalArrangement = Arrangement.spacedBy(space.s2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.height(IntrinsicSize.Min)) {
            BodyStatTile(MeasurementType.HEIGHT, uiState, onEdit, Modifier.weight(1f))
            BodyStatTile(MeasurementType.WINGSPAN, uiState, onEdit, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.height(IntrinsicSize.Min)) {
            StatTile(
                label = "Ape index",
                value = ape?.differenceCm?.let { signedWhole(it) } ?: "–",
                unit = ape?.let { "cm" },
                delta = ape?.let { "ratio ${String.format(java.util.Locale.UK, "%.2f", it.ratio)}" } ?: "add height and wingspan",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                valueModifier = Modifier.testTag("you_ape_index"),
            )
            BodyStatTile(MeasurementType.STANDING_REACH, uiState, onEdit, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space.s2), modifier = Modifier.height(IntrinsicSize.Min)) {
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
    StatTile(
        label = type.label,
        value = latest?.value?.wholeOrOneDecimal() ?: "–",
        unit = latest?.let { type.unit },
        delta = latest?.let { "set ${it.date.shortLabel()}" } ?: "tap to add",
        modifier = modifier
            .fillMaxHeight()
            .clip(MaterialTheme.shapes.large)
            .clickable { onEdit(type) }
            .testTag("stat_${type.name}"),
        valueModifier = Modifier.testTag("you_${type.name.lowercase()}"),
    )
}

/** `+6`, `−3`, `±0` for a difference in whole centimetres. */
private fun signedWhole(value: Double): String {
    val rounded = Math.round(value)
    return when {
        rounded > 0 -> "+$rounded"
        rounded < 0 -> "−${-rounded}"
        else -> "±0"
    }
}

@Composable
private fun MeasurementDialog(type: MeasurementType, initial: Double?, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by rememberSaveable(type) { mutableStateOf(initial?.wholeOrOneDecimal().orEmpty()) }
    var error by rememberSaveable(type) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(type.label, style = MaterialTheme.typography.headlineSmall) },
        text = {
            CruxTextField(
                label = type.description,
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                helper = type.unit,
                error = error,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("field_measurement"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parseMeasurement(type, text).onSuccess(onSave).onFailure { error = it.message } },
                modifier = Modifier.testTag("save_measurement"),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
