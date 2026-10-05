package com.hardtekpt.crux.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.ClimbRepository
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.PersonalBest
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.EmptyState
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.GradeBadge
import com.hardtekpt.crux.ui.components.GradeState
import com.hardtekpt.crux.ui.components.StatTile
import com.hardtekpt.crux.ui.shortLabel
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DisciplineBests(
    val discipline: Discipline,
    /** Hardest send of any style. */
    val hardest: PersonalBest?,
    /** Hardest send per style, in the order styles are listed for the discipline. */
    val byStyle: List<PersonalBest>,
)

data class ProgressUiState(
    val isLoading: Boolean = true,
    val disciplines: List<DisciplineBests> = emptyList(),
) {
    val isEmpty: Boolean get() = disciplines.all { it.hardest == null }
}

fun List<PersonalBest>.toDisciplineBests(): List<DisciplineBests> = Discipline.entries.map { discipline ->
    val rows = filter { it.discipline == discipline }.sortedBy { it.style.ordinal }
    DisciplineBests(
        discipline = discipline,
        hardest = rows.maxWithOrNull(compareBy<PersonalBest> { it.gradeIndex }.thenBy { -it.date.toEpochDay() }),
        byStyle = rows,
    )
}

@HiltViewModel
class ProgressViewModel @Inject constructor(climbRepository: ClimbRepository) : ViewModel() {
    val uiState: StateFlow<ProgressUiState> = climbRepository.observePersonalBests()
        .map { ProgressUiState(isLoading = false, disciplines = it.toDisciplineBests()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())
}

@Composable
fun ProgressScreen(viewModel: ProgressViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProgressContent(uiState)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressContent(uiState: ProgressUiState, modifier: Modifier = Modifier) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val space = CruxTheme.space
    Column(
        modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("screen_Progress"),
    ) {
        CruxTopAppBar(title = "Progress", scrollBehavior = scrollBehavior)
        if (!uiState.isLoading && uiState.isEmpty) {
            EmptyState(
                icon = Icons.Rounded.Insights,
                headline = "No sends yet",
                sentence = "Log a send and your hardest grades show up here.",
            )
            return
        }
        // The overall hardest send gets the personal-best tile; one per screen.
        val topBest = uiState.disciplines.mapNotNull { it.hardest }.maxByOrNull { it.date }
        LazyColumn(
            contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s1, bottom = space.s12),
            verticalArrangement = Arrangement.spacedBy(space.s2),
        ) {
            item { Eyebrow("Hardest sends", Modifier.padding(bottom = space.s1)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(space.s3)) {
                    uiState.disciplines.forEach { bests ->
                        val hardest = bests.hardest
                        StatTile(
                            label = bests.discipline.label,
                            value = hardest?.grade ?: "–",
                            delta = hardest?.let { "${it.style.label.lowercase()} · ${it.date.shortLabel()}" } ?: "no sends yet",
                            isPersonalBest = hardest != null && hardest == topBest,
                            modifier = Modifier.weight(1f),
                            valueModifier = Modifier.testTag("hardest_${bests.discipline.name}"),
                        )
                    }
                }
            }
            uiState.disciplines.filter { it.byStyle.isNotEmpty() }.forEach { bests ->
                item(key = "header_${bests.discipline}") {
                    Eyebrow("${bests.discipline.label} · by style", Modifier.padding(top = space.s4, bottom = space.s1))
                }
                items(bests.byStyle, key = { "${it.discipline}_${it.style}" }) { best ->
                    CruxListRow(
                        title = best.style.label,
                        supporting = listOfNotNull(best.name, best.place, best.date.shortLabel()).joinToString(" · "),
                        leading = {
                            GradeBadge(
                                best.grade,
                                if (best == bests.hardest) GradeState.PersonalBest else GradeState.Sent,
                            )
                        },
                        modifier = Modifier.testTag("best_row"),
                    )
                }
            }
        }
    }
}
