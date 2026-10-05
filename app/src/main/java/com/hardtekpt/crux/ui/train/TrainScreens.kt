package com.hardtekpt.crux.ui.train

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.hardtekpt.crux.data.TemplateRepository
import com.hardtekpt.crux.data.model.WorkoutTemplate
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.EmptyState
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.navigation.TemplateDetailRoute
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TrainUiState(
    val isLoading: Boolean = true,
    val templates: List<WorkoutTemplate> = emptyList(),
)

@HiltViewModel
class TrainViewModel @Inject constructor(repository: TemplateRepository) : ViewModel() {
    val uiState: StateFlow<TrainUiState> = repository.observeTemplates()
        .map { TrainUiState(isLoading = false, templates = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainUiState())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainScreen(
    onOpenTemplate: (Long) -> Unit,
    viewModel: TrainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val space = CruxTheme.space

    Column(
        Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("screen_Train"),
    ) {
        CruxTopAppBar(title = "Train", scrollBehavior = scrollBehavior)
        if (!uiState.isLoading && uiState.templates.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.FitnessCenter,
                headline = "No workouts yet",
                sentence = "The workout builder arrives next. Starter templates will show up here.",
            )
            return
        }
        LazyColumn(
            contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s1, bottom = space.s4 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            item { Eyebrow("Templates · ${uiState.templates.size}") }
            items(uiState.templates, key = { it.id }) { template ->
                CruxCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .clickable { onOpenTemplate(template.id) }
                        .testTag("template_card"),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(template.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        template.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${template.blocks.size} blocks · ${template.exerciseCount} exercises · ~${template.estimatedMinutes} min",
                        style = CruxTheme.type.code,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = space.s1),
                    )
                }
            }
        }
    }
}

data class TemplateDetailUiState(
    val isLoading: Boolean = true,
    val template: WorkoutTemplate? = null,
)

@HiltViewModel
class TemplateDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: TemplateRepository,
) : ViewModel() {
    private val templateId = savedStateHandle.toRoute<TemplateDetailRoute>().templateId

    val uiState: StateFlow<TemplateDetailUiState> = repository.observeTemplate(templateId)
        .map { TemplateDetailUiState(isLoading = false, template = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemplateDetailUiState())
}

/** Read-only for the MVP: blocks and their targets. Editing comes with the workout builder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateDetailScreen(
    onBack: () -> Unit,
    viewModel: TemplateDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val template = uiState.template
    val space = CruxTheme.space

    Column(Modifier.fillMaxSize().testTag("screen_TemplateDetail")) {
        CruxTopAppBar(title = template?.name ?: "", onBack = onBack)
        if (template == null) return@Column
        LazyColumn(
            contentPadding = PaddingValues(start = space.s4, end = space.s4, bottom = space.s4 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s2),
        ) {
            item {
                Text(
                    template.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Text(
                    "~${template.estimatedMinutes} min · ${template.exerciseCount} exercises",
                    style = CruxTheme.type.code,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = space.s2),
                )
            }
            template.blocks.forEachIndexed { index, block ->
                item(key = "block_$index") {
                    Eyebrow("${index + 1} · ${block.name}", Modifier.padding(top = space.s4, bottom = space.s1))
                }
                items(block.exercises.size, key = { "ex_${index}_$it" }) { exerciseIndex ->
                    val exercise = block.exercises[exerciseIndex]
                    CruxListRow(
                        title = exercise.name,
                        supporting = exercise.target,
                        trailing = exercise.rest?.let { rest ->
                            {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.Timer,
                                        contentDescription = "Rest",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = space.s1),
                                    )
                                    Text(rest, style = CruxTheme.type.code, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        modifier = Modifier.testTag("template_exercise"),
                    )
                }
            }
            item {
                CruxButton(
                    text = "Start session",
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = space.s6),
                )
            }
            item {
                Text(
                    "Sessions arrive with the session logger.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
