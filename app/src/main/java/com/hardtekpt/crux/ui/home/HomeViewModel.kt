package com.hardtekpt.crux.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.ClimbRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val totalClimbs: Int = 0,
    val climbsThisWeek: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val climbRepository: ClimbRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        climbRepository.observeClimbCount(),
        climbRepository.observeClimbCountSince(startOfWeekMillis()),
    ) { total, thisWeek ->
        HomeUiState(isLoading = false, totalClimbs = total, climbsThisWeek = thisWeek)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    /** Temporary quick-log action so the Room pipeline is visible end to end. */
    fun logSampleClimb() {
        viewModelScope.launch {
            climbRepository.logClimb(name = "Warm-up problem", grade = "V2")
        }
    }

    private fun startOfWeekMillis(): Long {
        val zone = ZoneId.systemDefault()
        return LocalDate.now(zone)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }
}
