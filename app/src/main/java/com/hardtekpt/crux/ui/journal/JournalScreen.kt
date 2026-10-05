package com.hardtekpt.crux.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
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
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.EmptyState
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.GradeBadge
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.displayName
import com.hardtekpt.crux.ui.gradeState
import com.hardtekpt.crux.ui.outcomeLine
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** One journal group: a day at a place. */
data class JournalDay(
    val date: LocalDate,
    val place: String?,
    val climbs: List<Climb>,
) {
    val key: String get() = "$date|${place.orEmpty()}"
    val title: String get() = listOfNotNull(date.dayLabel(), place).joinToString(" · ")
}

data class JournalUiState(
    val isLoading: Boolean = true,
    val days: List<JournalDay> = emptyList(),
)

/** Climbs arrive newest first; grouping keeps that order. */
fun List<Climb>.groupByDayAndPlace(): List<JournalDay> =
    groupBy { it.date to it.place }.map { (key, climbs) -> JournalDay(key.first, key.second, climbs) }

@HiltViewModel
class JournalViewModel @Inject constructor(
    climbRepository: ClimbRepository,
) : ViewModel() {
    val uiState: StateFlow<JournalUiState> = climbRepository.observeClimbs()
        .map { JournalUiState(isLoading = false, days = it.groupByDayAndPlace()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState())
}

@Composable
fun JournalScreen(viewModel: JournalViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    JournalContent(uiState)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalContent(uiState: JournalUiState, modifier: Modifier = Modifier) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("screen_Journal"),
    ) {
        CruxTopAppBar(title = "Journal", scrollBehavior = scrollBehavior)
        if (!uiState.isLoading && uiState.days.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                headline = "No climbs logged yet",
                sentence = "Tap Log, then Log climb, to start the journal.",
            )
            return
        }
        val space = CruxTheme.space
        LazyColumn(
            contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s1, bottom = space.s16 + space.s12),
            verticalArrangement = Arrangement.spacedBy(space.s2),
        ) {
            uiState.days.forEachIndexed { index, day ->
                item(key = day.key) {
                    Eyebrow(
                        day.title,
                        Modifier.padding(top = if (index == 0) space.s0 else space.s4, bottom = space.s1),
                    )
                }
                items(day.climbs, key = { it.id }) { climb ->
                    CruxListRow(
                        title = climb.displayName(),
                        supporting = listOfNotNull(climb.outcomeLine(), climb.notes).joinToString(" · "),
                        leading = { GradeBadge(climb.grade, climb.gradeState) },
                        modifier = Modifier.testTag("journal_climb"),
                    )
                }
            }
        }
    }
}
