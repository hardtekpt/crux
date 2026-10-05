package com.hardtekpt.crux.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.BodyRepository
import com.hardtekpt.crux.data.ClimbRepository
import com.hardtekpt.crux.data.TemplateRepository
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.PersonalBest
import com.hardtekpt.crux.data.model.WorkoutTemplate
import com.hardtekpt.crux.ui.WeightSummary
import com.hardtekpt.crux.ui.weightSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class WeekSummary(
    val climbs: Int = 0,
    val sends: Int = 0,
    val daysClimbed: Int = 0,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val todaysPlan: WorkoutTemplate? = null,
    val week: WeekSummary = WeekSummary(),
    val latestBest: PersonalBest? = null,
    val weight: WeightSummary? = null,
    val recentClimbs: List<Climb> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    climbRepository: ClimbRepository,
    bodyRepository: BodyRepository,
    templateRepository: TemplateRepository,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)
    private val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    val uiState: StateFlow<HomeUiState> = combine(
        templateRepository.observeTemplates(),
        climbRepository.observeClimbsSince(weekStart),
        climbRepository.observePersonalBests(),
        bodyRepository.observeWeights(),
        climbRepository.observeRecentClimbs(RECENT_LIMIT),
    ) { templates, weekClimbs, bests, weights, recent ->
        HomeUiState(
            isLoading = false,
            today = today,
            // MVP: a fixed rotation through the templates by day of the week.
            todaysPlan = templates.takeIf { it.isNotEmpty() }?.let { it[today.dayOfWeek.ordinal % it.size] },
            week = WeekSummary(
                climbs = weekClimbs.size,
                sends = weekClimbs.count { it.style.isSend },
                daysClimbed = weekClimbs.map { it.date }.distinct().size,
            ),
            latestBest = bests.maxWithOrNull(compareBy<PersonalBest> { it.date }.thenBy { it.gradeIndex }),
            weight = weights.weightSummary(),
            recentClimbs = recent,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(today = today),
    )

    private companion object {
        const val RECENT_LIMIT = 3
    }
}
