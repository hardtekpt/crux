package com.hardtekpt.crux.ui.you

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.lengthDifference
import com.hardtekpt.crux.ui.measurement
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.places.PlacesViewModel
import com.hardtekpt.crux.ui.places.placesList
import com.hardtekpt.crux.ui.shortLabel
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.theme.JetBrainsMono
import com.hardtekpt.crux.ui.weight
import com.hardtekpt.crux.ui.weightChange

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
            item(key = "body_label") { Eyebrow("Body · tap to update") }
            item(key = "body") { BodyStatGrid(uiState, onEdit = { editing = it }) }
            if (uiState.weights.isEmpty()) {
                item(key = "weight_empty") {
                    CruxButton("Log your first weigh-in", onLogWeight, variant = CruxButtonVariant.Tonal, icon = Icons.Rounded.Add)
                }
            } else {
                item(key = "weight_chart") { WeightTrendCard(uiState.weights) }
            }
            if (uiState.weights.size > 3) {
                item(key = "weight_history_label") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow("Weigh-ins", Modifier.weight(1f))
                        CruxButton(if (allWeights) "Show fewer" else "Show all ${uiState.weights.size}", { allWeights = !allWeights }, variant = CruxButtonVariant.Text, size = CruxButtonSize.Small)
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
                    "Measured relaxed with a soft tape. Tap a side to update it; history is kept.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            items(MeasurementType.circumferenceParts, key = { it.first }) { (part, types) ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(colors.surfaceContainer)
                        .border(CruxTheme.size.borderHairline, colors.outlineVariant, MaterialTheme.shapes.large)
                        .padding(space.s4),
                    verticalArrangement = Arrangement.spacedBy(space.s2),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(part, style = MaterialTheme.typography.titleMedium)
                            Text(types.first().description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        if (types.size == 2) {
                            val left = uiState.latest[types[0]]?.value
                            val right = uiState.latest[types[1]]?.value
                            if (left != null && right != null && left != right) {
                                val bigger = if (right > left) "R" else "L"
                                Text(
                                    "$bigger +${units.measurement(types[0], kotlin.math.abs(right - left))}",
                                    style = TapeLabel,
                                    color = colors.tertiary,
                                    modifier = Modifier.testTag("diff_$part"),
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                        types.forEach { type ->
                            val latest = uiState.latest[type]
                            val side = when {
                                types.size == 1 -> null
                                type == types[0] -> "Left"
                                else -> "Right"
                            }
                            TapeCell(
                                side = side,
                                value = latest?.let { units.measurement(type, it.value).toString() },
                                date = latest?.date?.shortLabel(),
                                onClick = { editing = type },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stat_${type.name}"),
                                valueModifier = Modifier.testTag("you_${type.name.lowercase()}"),
                            )
                        }
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
                viewModel.setMeasurement(type, it)
                editing = null
            },
        )
    }
}

/** One side's value on a strip that reads like measuring tape, ticks along the top edge. */
@Composable
private fun TapeCell(
    side: String?,
    value: String?,
    date: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueModifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val tick = colors.outline
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .androidxDrawTicks(tick)
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
    ) {
        side?.let { Text(it.uppercase(), style = TapeLabel, color = colors.onSurfaceVariant) }
        if (value != null) {
            Text(value, style = CruxTheme.type.grade.copy(fontSize = 20.sp), modifier = valueModifier)
            date?.let { Text("set $it", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
        } else {
            Text("Add", style = MaterialTheme.typography.titleSmall, color = colors.primary, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

/** Short tick marks every 6 dp along the top, a longer one every fifth: the tape. */
private fun Modifier.androidxDrawTicks(color: androidx.compose.ui.graphics.Color): Modifier = this.then(
    Modifier.drawBehind {
        val step = 6.dp.toPx()
        var x = step
        var i = 1
        while (x < size.width) {
            val h = if (i % 5 == 0) 8.dp.toPx() else 4.dp.toPx()
            drawLine(color.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, h), strokeWidth = 1.dp.toPx())
            x += step
            i++
        }
    },
)

/** Every saved gym, crag and board, with the Crag/Gym/Board filters. */
@Composable
fun PlacesScreen(
    onBack: () -> Unit,
    onOpenPlace: (Long) -> Unit,
    onNewPlace: () -> Unit,
    viewModel: PlacesViewModel = hiltViewModel(),
) {
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
