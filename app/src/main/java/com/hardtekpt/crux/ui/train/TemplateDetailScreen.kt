package com.hardtekpt.crux.ui.train

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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.navigation.TemplateDetailRoute
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TemplateDetailUiState(
    val isLoading: Boolean = true,
    val template: WorkoutTemplate? = null,
)

@HiltViewModel
class TemplateDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: TemplateRepository,
) : ViewModel() {
    val templateId = savedStateHandle.toRoute<TemplateDetailRoute>().templateId

    val uiState: StateFlow<TemplateDetailUiState> = repository.observeTemplate(templateId)
        .map { TemplateDetailUiState(isLoading = false, template = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TemplateDetailUiState())
}

/** A plan's blocks and targets, with Edit in the app bar. Sessions arrive with the session logger. */
@Composable
fun TemplateDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: TemplateDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val template = uiState.template
    val space = CruxTheme.space

    // A plan deleted from its editor leaves nothing to show; step back out.
    if (!uiState.isLoading && template == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
    }

    Column(Modifier.fillMaxSize().testTag("screen_TemplateDetail")) {
        CruxTopAppBar(
            title = template?.name ?: "",
            onBack = onBack,
            actions = {
                if (template != null) {
                    IconButton(onClick = { onEdit(template.id) }, modifier = Modifier.testTag("edit_plan")) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit plan")
                    }
                }
            },
        )
        if (template == null) return@Column
        LazyColumn(
            contentPadding = PaddingValues(start = space.s4, end = space.s4, bottom = space.s4 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s2),
        ) {
            if (template.description.isNotBlank()) {
                item {
                    Text(
                        template.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                items(block.items.size, key = { "ex_${index}_$it" }) { itemIndex ->
                    val item = block.items[itemIndex]
                    CruxListRow(
                        title = item.exercise.name,
                        supporting = item.prescription,
                        trailing = item.target.restLabel()?.let { rest ->
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
