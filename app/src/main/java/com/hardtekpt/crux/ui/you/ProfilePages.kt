package com.hardtekpt.crux.ui.you

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hardtekpt.crux.data.model.MeasurementType
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.ui.LocalUnits
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.CruxCardFill
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.lengthDifference
import com.hardtekpt.crux.ui.measurement
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.oneDecimal
import com.hardtekpt.crux.ui.places.PlacesViewModel
import com.hardtekpt.crux.ui.places.placesList
import com.hardtekpt.crux.ui.shortLabel
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.theme.JetBrainsMono
import com.hardtekpt.crux.ui.weight
import com.hardtekpt.crux.ui.weightChange
import com.hardtekpt.crux.ui.weightUnit
import com.hardtekpt.crux.ui.weightValue

private val TapeLabel = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.2.sp)

/** Bodyweight (trend and every weigh-in) and the climbing body stats. */
@Composable
fun MeasurementsScreen(onBack: () -> Unit, onLogWeight: () -> Unit, viewModel: YouViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val units = LocalUnits.current
    val space = CruxTheme.space
    var editing by rememberSaveable { mutableStateOf<MeasurementType?>(null) }
    var allWeights by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().testTag("screen_Measurements")) {
        CruxTopAppBar(
            title = "Measurements",
            onBack = onBack,
            actions = {
                IconButton(onClick = onLogWeight, modifier = Modifier.testTag("measurements_log_weight")) {
                    Icon(Icons.Rounded.Add, contentDescription = "Log weight")
                }
            },
        )
        LazyColumn(
            modifier = Modifier.testTag("measurements_list"),
            contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s1, bottom = space.s6 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            // Weight first: it changes most.
            item(key = "weight_card") { WeightCard(uiState, onLogWeight) }
            // Proportions: height and wingspan drawn on the figure, the ape index in the header.
            item(key = "proportions") {
                val ape = uiState.apeIndex
                CruxCard(fill = CruxCardFill.Default, modifier = Modifier.testTag("proportions_card")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow("Your body · tap to update", Modifier.weight(1f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .border(CruxTheme.size.borderHairline, MaterialTheme.colorScheme.outline, CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("APE INDEX", style = TapeLabel.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                ape?.differenceCm?.let { units.lengthDifference(it).toString() } ?: "–",
                                style = TapeLabel.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.testTag("you_ape_index"),
                            )
                        }
                    }
                    ProportionsFigure(
                        height = uiState.latest[MeasurementType.HEIGHT]?.value,
                        wingspan = uiState.latest[MeasurementType.WINGSPAN]?.value,
                        reach = uiState.latest[MeasurementType.STANDING_REACH]?.value,
                        weightKg = uiState.summary?.latest?.value,
                        bodyFat = uiState.latest[MeasurementType.BODY_FAT]?.value,
                        onEdit = { editing = it },
                        onLogWeight = onLogWeight,
                        modifier = Modifier.padding(top = space.s2),
                    )
                }
            }
            if (uiState.weights.size > 3) {
                item(key = "weight_history_label") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow("Weigh-ins", Modifier.weight(1f))
                        CruxButton(if (allWeights) "Show fewer" else "Show all ${uiState.weights.size}", {
                            allWeights = !allWeights
                        }, variant = CruxButtonVariant.Text, size = CruxButtonSize.Small)
                    }
                }
            }
            val shown = if (allWeights) uiState.weights else uiState.weights.take(3)
            items(shown, key = { "w_${it.id}" }) { entry ->
                val previous = uiState.weights.getOrNull(uiState.weights.indexOf(entry) + 1)
                CruxListRow(
                    title = entry.date.dayLabel(),
                    supporting = previous?.let { "${units.weightChange(entry.value - it.value)} from ${it.date.shortLabel()}" },
                    trailing = { Text(units.weight(entry.value).toString(), style = CruxTheme.type.grade, color = MaterialTheme.colorScheme.onSurface) },
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
                viewModel.setMeasurement(type, it)
                editing = null
            },
        )
    }
}

/** Weight on top of the Measurements page: the latest value, how it moved, and the chart. */
@Composable
private fun WeightCard(uiState: YouUiState, onLogWeight: () -> Unit) {
    val units = LocalUnits.current
    val colors = MaterialTheme.colorScheme
    val summary = uiState.summary
    var range by rememberSaveable { mutableStateOf(com.hardtekpt.crux.ui.charts.ChartRange.Quarter) }
    val start = range.start(uiState.today)
    val inRange = uiState.weights.filter { start == null || !it.date.isBefore(start) }.sortedBy { it.date }
    CruxCard(fill = CruxCardFill.Default, modifier = Modifier.testTag("weight_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Weight", Modifier.weight(1f))
            CruxButton("Log", onLogWeight, variant = CruxButtonVariant.Tonal, size = CruxButtonSize.Small, icon = Icons.Rounded.Add)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(summary?.latest?.value?.let { units.weight(it).value } ?: "–", style = CruxTheme.type.metricLarge)
            if (summary !=
                null
            ) {
                Text(
                    " ${units.weightUnit()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        Text(
            when {
                summary == null -> "No weigh-ins yet"
                summary.change != null -> "${units.weightChange(summary.change)} · ${summary.window} · last weigh-in ${summary.latest.date.dayLabel()}"
                else -> "Last weigh-in ${summary.latest.date.dayLabel()}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        if (uiState.weights.size >= 2) {
            com.hardtekpt.crux.ui.components.CruxSegmentedButtons(
                options = com.hardtekpt.crux.ui.charts.ChartRange.entries,
                selected = range,
                label = { it.label },
                onSelect = { range = it },
                modifier = Modifier.padding(top = CruxTheme.space.s3, bottom = CruxTheme.space.s2),
            )
            if (inRange.size < 2) {
                Text(
                    "Log another weigh-in in this window to see the trend.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = CruxTheme.space.s4),
                )
            } else {
                com.hardtekpt.crux.ui.charts.TimeSeriesChart(
                    points = inRange.map { com.hardtekpt.crux.ui.charts.SeriesPoint(it.date.toEpochDay().toDouble(), units.weightValue(it.value)) },
                    formatX = { java.time.LocalDate.ofEpochDay(it.toLong()).shortLabel() },
                    formatY = { it.oneDecimal() },
                    unit = " ${units.weightUnit()}",
                    height = 150.dp,
                    description = "Bodyweight between ${inRange.first().date.shortLabel()} and ${inRange.last().date.shortLabel()}",
                )
            }
        }
    }
}

/**
 * Circumferences by body part. Limbs show left and right side by side as two pieces of tape,
 * with the difference between them; chest and waist have one.
 */
@Composable
fun CircumferencesScreen(onBack: () -> Unit, viewModel: YouViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val units = LocalUnits.current
    val space = CruxTheme.space
    val colors = MaterialTheme.colorScheme
    var editing by rememberSaveable { mutableStateOf<MeasurementType?>(null) }
    Column(Modifier.fillMaxSize().testTag("screen_Circumferences")) {
        CruxTopAppBar(title = "Circumferences", onBack = onBack)
        LazyColumn(
            modifier = Modifier.testTag("circumferences_list"),
            contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s1, bottom = space.s6 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            item(key = "intro") {
                Text(
                    "Each band is where the tape goes. Tap a value to update that side.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            item(key = "figure") {
                CruxCard(fill = CruxCardFill.Default) {
                    Eyebrow("Seen from behind · your left is left")
                    CircumferenceFigure(
                        latest = MeasurementType.circumferences.mapNotNull { type -> uiState.latest[type]?.let { type to it.value } }.toMap(),
                        onEdit = { editing = it },
                        modifier = Modifier.padding(top = space.s2),
                    )
                }
            }
            val last = MeasurementType.circumferences.mapNotNull { uiState.latest[it]?.date }.maxOrNull()
            if (last != null) {
                item(key = "last") {
                    Text("Last measured ${last.shortLabel()}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
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
                viewModel.setMeasurement(type, it)
                editing = null
            },
        )
    }
}

/** Every saved gym, crag and board, with the Crag/Gym/Board filters. */
@Composable
fun PlacesScreen(onBack: () -> Unit, onOpenPlace: (Long) -> Unit, onNewPlace: () -> Unit, viewModel: PlacesViewModel = hiltViewModel()) {
    val places by viewModel.places.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<PlaceType?>(null) }
    val space = CruxTheme.space
    Column(Modifier.fillMaxSize().testTag("screen_Places")) {
        CruxTopAppBar(title = "Places", onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s1, bottom = space.s4 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s3),
            modifier = Modifier.testTag("places_list"),
        ) {
            placesList(places, onOpenPlace, onNewPlace, filter, { filter = it })
        }
    }
}
