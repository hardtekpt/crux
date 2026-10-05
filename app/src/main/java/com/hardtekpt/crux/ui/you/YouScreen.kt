package com.hardtekpt.crux.ui.you

import androidx.compose.foundation.layout.Arrangement
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
import com.hardtekpt.crux.ui.WeightSummary
import com.hardtekpt.crux.ui.components.CruxButton
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
import javax.inject.Inject

data class YouUiState(
    val isLoading: Boolean = true,
    val weights: List<Measurement> = emptyList(),
    val summary: WeightSummary? = null,
    val height: Measurement? = null,
) {
    val heightCm: Double? get() = height?.value
}

@HiltViewModel
class YouViewModel @Inject constructor(
    private val bodyRepository: BodyRepository,
) : ViewModel() {
    val uiState: StateFlow<YouUiState> = combine(
        bodyRepository.observeWeights(),
        bodyRepository.observeHeight(),
    ) { weights, height ->
        YouUiState(isLoading = false, weights = weights, summary = weights.weightSummary(), height = height)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YouUiState())

    fun setHeight(cm: Double) {
        viewModelScope.launch { bodyRepository.setHeight(cm) }
    }
}

/** Height entry: 100–250 cm, one decimal at most. Returns the value or an error message. */
fun parseHeight(text: String): Result<Double> {
    val value = text.replace(',', '.').trim().toDoubleOrNull()
    return if (value == null || value < 100 || value > 250) {
        Result.failure(IllegalArgumentException("Enter a height between 100 and 250 cm"))
    } else {
        Result.success(value)
    }
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
        onSetHeight = viewModel::setHeight,
        onOpenSettings = onOpenSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouContent(
    uiState: YouUiState,
    onLogWeight: () -> Unit,
    onSetHeight: (Double) -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val space = CruxTheme.space
    var editingHeight by rememberSaveable { mutableStateOf(false) }

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
            contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s1, bottom = space.s4 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            item { Eyebrow("Body") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(space.s3)) {
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
                    StatTile(
                        label = "Height",
                        value = uiState.heightCm?.wholeOrOneDecimal() ?: "–",
                        unit = uiState.heightCm?.let { "cm" },
                        delta = uiState.height?.let { "set ${it.date.shortLabel()}" } ?: "not set",
                        modifier = Modifier.weight(1f),
                        valueModifier = Modifier.testTag("you_height"),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(space.s2)) {
                    // Outlined on purpose: logging a measurement is not this screen's main job.
                    CruxButton(
                        text = "Log weight",
                        onClick = onLogWeight,
                        variant = CruxButtonVariant.Outlined,
                        icon = Icons.Rounded.Add,
                        modifier = Modifier.testTag("log_weight"),
                    )
                    CruxButton(
                        text = "Set height",
                        onClick = { editingHeight = true },
                        variant = CruxButtonVariant.Text,
                        modifier = Modifier.testTag("set_height"),
                    )
                }
            }
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

    if (editingHeight) {
        HeightDialog(
            initial = uiState.heightCm,
            onDismiss = { editingHeight = false },
            onSave = {
                onSetHeight(it)
                editingHeight = false
            },
        )
    }
}

@Composable
private fun HeightDialog(initial: Double?, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial?.wholeOrOneDecimal().orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("Set height", style = MaterialTheme.typography.headlineSmall) },
        text = {
            CruxTextField(
                label = "Height",
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                placeholder = "178",
                helper = "cm",
                error = error,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("field_height"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parseHeight(text).onSuccess(onSave).onFailure { error = it.message } },
                modifier = Modifier.testTag("save_height"),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
