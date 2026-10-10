package com.hardtekpt.crux.ui.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.ui.session.IntervalRun
import com.hardtekpt.crux.ui.session.IntervalSpec
import com.hardtekpt.crux.ui.session.toSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The standalone interval timer: a setup that opens as it was last left, and a run worked out
 * from the clock. Nothing is logged.
 */
@HiltViewModel
class IntervalTimerViewModel @Inject constructor(private val preferences: UserPreferencesRepository, private val clock: Clock) : ViewModel() {
    private val _spec = MutableStateFlow(IntervalSpec.TABATA)
    val spec: StateFlow<IntervalSpec> = _spec.asStateFlow()

    private val _run = MutableStateFlow<IntervalRun?>(null)
    val run: StateFlow<IntervalRun?> = _run.asStateFlow()

    val sounds: StateFlow<Boolean> = preferences.timerSounds.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        viewModelScope.launch { preferences.intervalTimer.first()?.let { _spec.value = it.toSpec() } }
    }

    fun now(): Long = clock.millis()

    fun setSpec(spec: IntervalSpec) {
        _spec.value = spec
        viewModelScope.launch { preferences.setIntervalTimer(spec.toSettings()) }
    }

    fun start() {
        _run.value = IntervalRun(_spec.value, clock.millis())
    }

    fun pause() = _run.update { it?.pause(clock.millis()) }

    fun resume() = _run.update { it?.resume(clock.millis()) }

    /** Back to the setup. */
    fun stop() {
        _run.value = null
    }

    /** The same timer again from the top. */
    fun restart() = start()

    /** Forward to the next phase, or back to the start of this one (or the one before, early on). */
    fun skip(forward: Boolean) = _run.update { run ->
        run ?: return@update null
        val now = clock.millis()
        val position = run.position(now)
        val index = when {
            forward -> position.index + 1

            // Within the first two seconds of a phase, back goes to the previous one.
            run.elapsed(now) - run.startOf(position.index) < 2_000 -> position.index - 1

            else -> position.index
        }
        run.skipTo(index, now)
    }
}
