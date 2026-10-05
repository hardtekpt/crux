package com.hardtekpt.crux.ui.home

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.hardtekpt.crux.MainDispatcherRule
import com.hardtekpt.crux.data.FIXED_CLOCK
import com.hardtekpt.crux.data.FakeBodyRepository
import com.hardtekpt.crux.data.FakeClimbRepository
import com.hardtekpt.crux.data.FakeTemplateRepository
import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.NewClimb
import com.hardtekpt.crux.data.model.WorkoutTemplate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class HomeViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val climbs = FakeClimbRepository()
    private val body = FakeBodyRepository()
    private val templates = FakeTemplateRepository()
    private val today = LocalDate.now(FIXED_CLOCK)

    private fun viewModel() = HomeViewModel(climbs, body, templates, FIXED_CLOCK)

    @Test
    fun `empty database gives empty widgets`() = runTest {
        viewModel().uiState.test {
            val state = awaitLoaded()
            assertEquals(WeekSummary(), state.week)
            assertNull(state.latestBest)
            assertNull(state.weight)
            assertNull(state.todaysPlan)
            assertEquals(0, state.recentClimbs.size)
        }
    }

    @Test
    fun `this week counts only climbs since Monday`() = runTest {
        // FIXED_CLOCK is a Monday, so yesterday falls in the previous week.
        climbs.logClimb(newClimb(today, AscentStyle.FLASH))
        climbs.logClimb(newClimb(today, AscentStyle.ATTEMPT))
        climbs.logClimb(newClimb(today.minusDays(1), AscentStyle.REDPOINT))

        viewModel().uiState.test {
            assertEquals(WeekSummary(climbs = 2, sends = 1, daysClimbed = 1), awaitLoaded().week)
        }
    }

    @Test
    fun `logging a climb or weight updates home without a restart`() = runTest {
        templates.templates.value = listOf(WorkoutTemplate(1, "Strength day", "", 50, emptyList()))
        viewModel().uiState.test {
            awaitLoaded()
            climbs.logClimb(newClimb(today, AscentStyle.FLASH, gradeIndex = 11))
            val withClimb = awaitUntil { it.recentClimbs.size == 1 }
            assertEquals("7A", withClimb.latestBest?.grade)
            assertEquals("Strength day", withClimb.todaysPlan?.name)

            body.logWeight(72.4, today)
            assertEquals(72.4, awaitUntil { it.weight != null }.weight?.latest?.value)
        }
    }

    private fun newClimb(date: LocalDate, style: AscentStyle, gradeIndex: Int = 5) = NewClimb(
        discipline = Discipline.BOULDER,
        gradeIndex = gradeIndex,
        style = style,
        attempts = 1,
        date = date,
        name = null,
        place = null,
        notes = null,
    )
}

private suspend fun ReceiveTurbine<HomeUiState>.awaitUntil(predicate: (HomeUiState) -> Boolean): HomeUiState {
    var item = awaitItem()
    while (!predicate(item)) item = awaitItem()
    return item
}

/** Skips the initial loading placeholder emitted before the repositories answer. */
private suspend fun ReceiveTurbine<HomeUiState>.awaitLoaded(): HomeUiState = awaitUntil { !it.isLoading }
