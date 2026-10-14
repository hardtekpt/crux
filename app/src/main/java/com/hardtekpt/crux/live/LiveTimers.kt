package com.hardtekpt.crux.live

import com.hardtekpt.crux.ui.session.IntervalRun
import com.hardtekpt.crux.ui.session.Rest
import com.hardtekpt.crux.ui.session.SessionTimer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The timers that run while you train, held for the whole app rather than one screen: the
 * session's rest and interval timer, and the Interval timer page's own run. The session screen
 * and the live notification both read and change them, so a rest keeps going when you leave the
 * screen and its buttons in the notification act on the same timer.
 */
@Singleton
class LiveTimers @Inject constructor() {
    /** The session the rest and timer belong to. */
    @Volatile var sessionId: Long? = null
        private set

    val rest = MutableStateFlow<Rest?>(null)
    val timer = MutableStateFlow<SessionTimer?>(null)

    /** The Interval timer page's run (You → Interval timer), outside any session. */
    val standalone = MutableStateFlow<IntervalRun?>(null)

    /** Another session starts with no rest or timer of the last one. */
    fun attach(sessionId: Long) {
        if (this.sessionId == sessionId) return
        this.sessionId = sessionId
        rest.value = null
        timer.value = null
    }

    /** The rest ran out, or was skipped. */
    fun endRest() {
        rest.value = null
    }

    fun addRest(seconds: Int) {
        rest.value = rest.value?.let { it.copy(endsAtMillis = it.endsAtMillis + seconds * 1000L, totalSeconds = (it.totalSeconds + seconds).coerceAtLeast(1)) }
    }
}
