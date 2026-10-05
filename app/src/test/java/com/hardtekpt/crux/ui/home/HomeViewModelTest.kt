package com.hardtekpt.crux.ui.home

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.hardtekpt.crux.MainDispatcherRule
import com.hardtekpt.crux.data.ClimbRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeClimbRepository()

    @Test
    fun `starts loading then shows climb count`() = runTest {
        val viewModel = HomeViewModel(repository)

        viewModel.uiState.test {
            assertEquals(HomeUiState(isLoading = false, totalClimbs = 0), awaitLoaded())
        }
    }

    @Test
    fun `logging a climb updates the total`() = runTest {
        val viewModel = HomeViewModel(repository)

        viewModel.uiState.test {
            assertEquals(0, awaitLoaded().totalClimbs)
            viewModel.logSampleClimb()
            assertEquals(1, awaitItem().totalClimbs)
        }
    }
}

/** Skips the initial loading placeholder emitted before the repository answers. */
private suspend fun ReceiveTurbine<HomeUiState>.awaitLoaded(): HomeUiState {
    var item = awaitItem()
    while (item.isLoading) item = awaitItem()
    return item
}

private class FakeClimbRepository : ClimbRepository {
    private val count = MutableStateFlow(0)
    override fun observeClimbCount(): Flow<Int> = count
    override suspend fun logClimb(name: String, grade: String, notes: String?) {
        count.value += 1
    }
}
