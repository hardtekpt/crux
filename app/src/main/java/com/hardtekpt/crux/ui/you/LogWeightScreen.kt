package com.hardtekpt.crux.ui.you

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.BodyRepository
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.data.model.Measurement
import com.hardtekpt.crux.data.model.MeasurementType
import com.hardtekpt.crux.ui.LocalUnits
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.input.DayStrip
import com.hardtekpt.crux.ui.components.input.PastDayDialog
import com.hardtekpt.crux.ui.components.input.RulerInput
import com.hardtekpt.crux.ui.measureInput
import com.hardtekpt.crux.ui.shortLabel
import com.hardtekpt.crux.ui.typicalValue
import com.hardtekpt.crux.ui.weightChange
import com.hardtekpt.crux.ui.weightValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class LogWeightDraft(
    /** Kilograms; starts at the last weigh-in so a new one is a small nudge. */
    val kg: Double = MeasurementType.WEIGHT.typicalValue(),
    /** The last weigh-in, to show the change against. */
    val previous: Measurement? = null,
    val date: LocalDate,
    val today: LocalDate,
    val error: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class LogWeightViewModel @Inject constructor(
    private val bodyRepository: BodyRepository,
    clock: Clock,
) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val _draft = MutableStateFlow(LogWeightDraft(date = today, today = today))
    val draft: StateFlow<LogWeightDraft> = _draft.asStateFlow()
    private var touched = false

    init {
        viewModelScope.launch {
            val last = bodyRepository.observeWeights().first().maxByOrNull { it.date }
            if (last != null) _draft.update { if (touched) it.copy(previous = last) else it.copy(kg = last.value, previous = last) }
        }
    }

    fun setKg(kg: Double) {
        touched = true
        _draft.update { it.copy(kg = kg, error = null) }
    }

    fun setDate(date: LocalDate) = _draft.update { it.copy(date = date, error = null) }

    fun save() {
        val draft = _draft.value
        if (draft.saved) return
        if (draft.kg !in MeasurementType.WEIGHT.range) {
            _draft.update { it.copy(error = "Pick a weight between 20 and 300 kg") }
            return
        }
        if (draft.date.isAfter(today)) {
            _draft.update { it.copy(error = "Pick today or an earlier day") }
            return
        }
        viewModelScope.launch {
            bodyRepository.logWeight(draft.kg, draft.date)
            _draft.update { it.copy(saved = true) }
        }
    }
}

@Composable
fun LogWeightScreen(
    onDone: () -> Unit,
    viewModel: LogWeightViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    LaunchedEffect(draft.saved) { if (draft.saved) onDone() }
    val space = CruxTheme.space
    val units = LocalUnits.current
    val input = remember(units) { units.measureInput(MeasurementType.WEIGHT) }
    var pickingDay by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_LogWeight"),
    ) {
        CruxTopAppBar(title = "Log weight", onBack = onDone)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            CruxCard(Modifier.padding(horizontal = space.s4)) {
                RulerInput(
                    title = "Bodyweight",
                    value = input.scale.valueAt(input.scale.indexOf(input.toDisplay(draft.kg))),
                    onValueChange = { viewModel.setKg(input.toStored(it)) },
                    scale = input.scale,
                    display = input.display,
                    unit = input.unit,
                    delta = draft.previous?.let { last ->
                        val change = draft.kg - last.value
                        val text = if (kotlin.math.abs(units.weightValue(change)) < 0.05) "No change" else units.weightChange(change)
                        "$text since ${last.date.shortLabel()}"
                    } ?: "Drag the scale, or tap the number to type",
                    parseTyped = input.parse,
                    typeUnit = input.typeUnit,
                    testTag = "ruler_weight",
                )
            }
            Eyebrow("Day · ${draft.date.dayLabel()}", Modifier.padding(start = space.s4, top = space.s2))
            DayStrip(
                selected = draft.date,
                today = draft.today,
                onSelect = viewModel::setDate,
                onOpenCalendar = { pickingDay = true },
            )
            draft.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = space.s4))
            }
        }
        CruxButton(
            text = "Log weight",
            onClick = viewModel::save,
            icon = Icons.Rounded.Check,
            size = CruxButtonSize.Large,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(space.s4)
                .testTag("save_weight"),
        )
    }
    if (pickingDay) {
        PastDayDialog(draft.date, draft.today, viewModel::setDate) { pickingDay = false }
    }
}
