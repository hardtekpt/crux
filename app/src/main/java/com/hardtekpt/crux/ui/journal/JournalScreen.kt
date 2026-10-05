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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.ClimbRepository
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.Venue
import com.hardtekpt.crux.data.model.PlaceSummary
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxSegmentedButtons
import com.hardtekpt.crux.ui.places.PlacesViewModel
import com.hardtekpt.crux.ui.places.placesList
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.EmptyState
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.GradeBadge
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.displayName
import com.hardtekpt.crux.ui.gradeState
import com.hardtekpt.crux.ui.outcomeLine
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
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
    val key: String get() = "$date|${place.orEmpty()}|$venue"
    val venue: Venue get() = climbs.first().venue
    val title: String get() = listOfNotNull(date.dayLabel(), place, venue.label).joinToString(" · ")
}

data class JournalUiState(
    val isLoading: Boolean = true,
    val days: List<JournalDay> = emptyList(),
)

/** Climbs arrive newest first; grouping keeps that order. */
fun List<Climb>.groupByDayAndPlace(): List<JournalDay> =
    groupBy { Triple(it.date, it.place, it.venue) }.map { (key, climbs) -> JournalDay(key.first, key.second, climbs) }

@HiltViewModel
class JournalViewModel @Inject constructor(
    climbRepository: ClimbRepository,
) : ViewModel() {
    val uiState: StateFlow<JournalUiState> = climbRepository.observeClimbs()
        .map { JournalUiState(isLoading = false, days = it.groupByDayAndPlace()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState())
}

enum class JournalView(val label: String) { Climbs("Climbs"), Places("Places") }

@Composable
fun JournalScreen(
    onOpenClimb: (Long) -> Unit = {},
    onOpenPlace: (Long) -> Unit = {},
    onNewPlace: () -> Unit = {},
    viewModel: JournalViewModel = hiltViewModel(),
    placesViewModel: PlacesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val places by placesViewModel.places.collectAsStateWithLifecycle()
    var view by rememberSaveable { mutableStateOf(JournalView.Climbs) }
    JournalContent(
        uiState = uiState,
        view = view,
        onView = { view = it },
        places = places,
        onOpenClimb = onOpenClimb,
        onOpenPlace = onOpenPlace,
        onNewPlace = onNewPlace,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalContent(
    uiState: JournalUiState,
    modifier: Modifier = Modifier,
    view: JournalView = JournalView.Climbs,
    onView: (JournalView) -> Unit = {},
    places: List<PlaceSummary>? = emptyList(),
    onOpenClimb: (Long) -> Unit = {},
    onOpenPlace: (Long) -> Unit = {},
    onNewPlace: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("screen_Journal"),
    ) {
        CruxTopAppBar(title = "Journal", scrollBehavior = scrollBehavior)
        val space = CruxTheme.space
        CruxSegmentedButtons(
            options = JournalView.entries,
            selected = view,
            label = { it.label },
            onSelect = onView,
            modifier = Modifier.padding(horizontal = space.s4, vertical = space.s2).testTag("journal_view"),
        )
        if (view == JournalView.Places) {
            LazyColumn(
                contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s1, bottom = space.s4 + LocalNavBarClearance.current),
                verticalArrangement = Arrangement.spacedBy(space.s2),
                modifier = Modifier.testTag("places_list"),
            ) {
                placesList(places, onOpenPlace, onNewPlace)
            }
            return
        }
        if (!uiState.isLoading && uiState.days.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Rounded.MenuBook,
                headline = "No climbs logged yet",
                sentence = "Tap Log, then Log climb, to start the journal.",
            )
            return
        }
        LazyColumn(
            contentPadding = PaddingValues(start = space.s4, end = space.s4, top = space.s1, bottom = space.s4 + LocalNavBarClearance.current),
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
                        supporting = listOfNotNull(climb.outcomeLine(), climb.effort?.let { "felt $it/10" }, climb.notes).joinToString(" · "),
                        leading = { GradeBadge(climb.grade, climb.gradeState) },
                        onClick = { onOpenClimb(climb.id) },
                        modifier = Modifier.testTag("journal_climb"),
                    )
                }
            }
        }
    }
}
