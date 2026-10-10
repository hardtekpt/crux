package com.hardtekpt.crux.ui.timer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardtekpt.crux.MainDispatcherRule
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.ui.session.IntervalPhase
import com.hardtekpt.crux.ui.session.IntervalSpec
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class IntervalTimerViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @get:Rule val tmp = TemporaryFolder()

    private class MutableClock(var millis: Long = 0) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(millis)
        override fun millis(): Long = millis
    }

    private val clock = MutableClock()
    private val preferences by lazy { UserPreferencesRepository(mainDispatcherRule.preferencesDataStore(File(tmp.root, "prefs.preferences_pb"))) }

    @Test
    fun `the setup is kept for next time`() = runTest(mainDispatcherRule.testDispatcher) {
        IntervalTimerViewModel(preferences, clock).setSpec(IntervalSpec.MAX_HANGS)

        assertEquals(IntervalSpec.MAX_HANGS.toSettings(), preferences.intervalTimer.first())
        assertEquals(IntervalSpec.MAX_HANGS, IntervalTimerViewModel(preferences, clock).spec.value)
    }

    @Test
    fun `skip moves on a phase, and back early in a phase goes to the one before`() {
        val vm = IntervalTimerViewModel(preferences, clock)
        vm.setSpec(IntervalSpec.TABATA)
        vm.start()
        clock.millis = 3_000
        vm.skip(forward = true)
        assertEquals(IntervalPhase.Kind.WORK, vm.run.value!!.position(clock.millis).phase.kind)
        // A second into the work, back goes to the preparation.
        clock.millis = 4_000
        vm.skip(forward = false)
        assertEquals(IntervalPhase.Kind.PREP, vm.run.value!!.position(clock.millis).phase.kind)
        // Five seconds into the preparation, back goes to its start.
        clock.millis = 9_000
        vm.skip(forward = false)
        assertEquals(10_000, vm.run.value!!.position(clock.millis).leftMillis)
    }

    @Test
    fun `pausing holds the time, and stop goes back to the setup`() {
        val vm = IntervalTimerViewModel(preferences, clock)
        vm.start()
        clock.millis = 2_000
        vm.pause()
        clock.millis = 60_000
        assertTrue(vm.run.value!!.paused)
        assertEquals(2_000, vm.run.value!!.elapsed(clock.millis))
        vm.resume()
        clock.millis = 61_000
        assertEquals(3_000, vm.run.value!!.elapsed(clock.millis))
        vm.stop()
        assertNull(vm.run.value)
    }
}
