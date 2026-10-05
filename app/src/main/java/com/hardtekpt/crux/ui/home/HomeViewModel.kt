package com.hardtekpt.crux.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.ClimbRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val totalClimbs: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val climbRepository: ClimbRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = climbRepository.observeClimbCount()
        .map { count -> HomeUiState(isLoading = false, totalClimbs = count) }
        .stateIn(
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
}
