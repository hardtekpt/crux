package com.hardtekpt.crux.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardtekpt.crux.MainDispatcherRule
import com.hardtekpt.crux.data.FakeExerciseRepository
import com.hardtekpt.crux.data.FakePlaceRepository
import com.hardtekpt.crux.data.FakeSessionRepository
import com.hardtekpt.crux.data.Session
import com.hardtekpt.crux.data.SessionItem
import com.hardtekpt.crux.data.SessionSet
import com.hardtekpt.crux.data.model.Exercise
import com.hardtekpt.crux.data.model.ExerciseCategory
import com.hardtekpt.crux.data.model.ExerciseTarget
import com.hardtekpt.crux.data.model.MetricType
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The interval timer logs each whole cycle as a set of its exercise. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SessionTimerViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @get:Rule val tmp = TemporaryFolder()

    private class MutableClock(var millis: Long = 0) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(millis)
        override fun millis(): Long = millis
    }

    private val clock = MutableClock()
    private val sessions = FakeSessionRepository()
    private val repeaters = Exercise(1, "Repeaters", ExerciseCategory.FINGERS, MetricType.WEIGHTED_INTERVALS, null)
    private val item = SessionItem(
        id = 7,
        exercise = repeaters,
        blockName = "Fingers",
        target = ExerciseTarget(sets = 3, reps = 6, seconds = 7, loadKg = 5.0, restSeconds = 180, repRestSeconds = 3),
        sets = listOf(SessionSet(0, 6, 7, 5.0, skipped = false)),
    )

    private val preferences by lazy { UserPreferencesRepository(mainDispatcherRule.preferencesDataStore(File(tmp.root, "prefs.preferences_pb"))) }

    private fun viewModel(): SessionViewModel {
        sessions.session.value = Session(1, "Plan", 1, null, null, 0, null, true, null, null, listOf(item), emptyList())
        return SessionViewModel(
            SavedStateHandle(mapOf("sessionId" to 1L)),
            sessions,
            FakeExerciseRepository(),
            FakePlaceRepository(),
            preferences,
            clock,
            com.hardtekpt.crux.live.LiveTimers(),
        ).also { it.session.launchIn(TestScope(mainDispatcherRule.testDispatcher)) }
    }

    @Test
    fun `logging a climb in the session starts the rest set in settings`() = kotlinx.coroutines.runBlocking {
        preferences.setClimbRestSeconds(90)
        val vm = viewModel()
        assertNull(vm.rest.value)
        val climb = com.hardtekpt.crux.data.model.Climb(
            1, com.hardtekpt.crux.data.model.Discipline.BOULDER, com.hardtekpt.crux.data.model.GradeScale.FONT, 10,
            com.hardtekpt.crux.data.model.AscentStyle.ATTEMPT, 1, com.hardtekpt.crux.data.model.Venue.GYM,
            java.time.LocalDate.of(2026, 10, 13), "Pink", null, null, sessionId = 1,
        )
        sessions.session.value = sessions.session.value!!.copy(climbs = listOf(climb))
        val rest = kotlinx.coroutines.withTimeout(5_000) { vm.rest.first { it != null } }!!
        assertEquals(90, rest.totalSeconds)
    }

    @Test
    fun `an interval exercise's timer runs the cycles still to do`() {
        val spec = viewModel().timerFor(item)
        assertEquals(IntervalSpec(prepSeconds = 10, workSeconds = 7, restSeconds = 3, repeats = 6, cycles = 2, cycleRestSeconds = 180), spec)
    }

    @Test
    fun `each whole cycle is logged as the next free set, and the timer closes at the end`() {
        val vm = viewModel()
        val spec = IntervalSpec(prepSeconds = 0, workSeconds = 2, restSeconds = 1, repeats = 2, cycles = 2, cycleRestSeconds = 5)
        vm.startTimer(spec, item.id)

        clock.millis = 6_000 // into the rest between cycles
        vm.tickTimer()
        assertEquals(listOf(item.id to SessionSet(1, 2, 2, 5.0, skipped = false)), sessions.loggedSets)

        clock.millis = 60_000
        vm.tickTimer()
        assertEquals(2, sessions.loggedSets.size)
        assertEquals(2, sessions.loggedSets.last().second.setIndex)
        assertNull(vm.timer.value)
        assertEquals(1, vm.timerEnds.value)
    }

    @Test
    fun `stopping keeps the cycles already done, and a free timer logs nothing`() {
        val vm = viewModel()
        vm.startTimer(IntervalSpec.TABATA, null)
        clock.millis = 1_000_000
        vm.stopTimer()
        assertEquals(emptyList<Pair<Long, SessionSet>>(), sessions.loggedSets)
        assertNull(vm.timer.value)
    }
}
