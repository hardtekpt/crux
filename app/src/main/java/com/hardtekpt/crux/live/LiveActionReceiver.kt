package com.hardtekpt.crux.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hardtekpt.crux.data.SessionRepository
import com.hardtekpt.crux.ui.session.Rest
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The live notification's buttons: they act on the session and timers without opening Crux. */
@AndroidEntryPoint
class LiveActionReceiver : BroadcastReceiver() {
    @Inject lateinit var sessions: SessionRepository

    @Inject lateinit var live: LiveTimers

    @Inject lateinit var clock: Clock

    @Inject lateinit var playlists: LivePlaylists

    override fun onReceive(context: Context, intent: Intent) {
        val action = runCatching { LiveAction.valueOf(intent.action.orEmpty()) }.getOrNull() ?: return
        val pending = goAsync()
        scope.launch {
            try {
                act(action)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun act(action: LiveAction) {
        val now = clock.millis()
        when (action) {
            LiveAction.SET_DONE -> setDone(now)

            LiveAction.ADD_REST -> live.addRest(30)

            LiveAction.SUB_REST -> live.addRest(-30)

            LiveAction.SKIP_REST -> live.endRest()

            LiveAction.PAUSE -> {
                live.timer.value = live.timer.value?.let { it.copy(run = it.run.pause(now)) }
                live.standalone.value = live.standalone.value?.pause(now)
            }

            LiveAction.RESUME -> {
                live.timer.value = live.timer.value?.let { it.copy(run = it.run.resume(now)) }
                live.standalone.value = live.standalone.value?.resume(now)
            }

            // The session screen logs the cycles already done when it next ticks.
            LiveAction.STOP -> {
                if (live.timer.value != null) live.timer.value = null else live.standalone.value = null
            }

            LiveAction.LOG_CLIMB -> Unit

            LiveAction.PREV_CLIMB -> playlists.move(-1)

            LiveAction.NEXT_CLIMB -> playlists.move(1)

            LiveAction.LIGHT -> playlists.light()
        }
    }

    /** Logs the next set of the first exercise with sets left, as last time or as planned, then rests. */
    private suspend fun setDone(now: Long) {
        val session = sessions.observeRunning().first() ?: return
        val item = session.items.firstOrNull { !it.finished } ?: return
        val setIndex = item.nextSet ?: return
        val metric = item.exercise.metric
        val last = item.sets.lastOrNull { !it.skipped }
        sessions.logSet(
            itemId = item.id,
            setIndex = setIndex,
            reps = (last?.reps ?: item.target.reps).takeIf { metric.usesReps || metric.usesIntervals },
            seconds = (last?.seconds ?: item.target.seconds).takeIf { metric.usesTime || metric.usesIntervals },
            loadKg = (last?.loadKg ?: item.target.loadKg).takeIf { metric.usesLoad },
        )
        live.attach(session.id)
        if (item.target.restSeconds > 0) live.rest.value = Rest(now + item.target.restSeconds * 1000L, item.target.restSeconds)
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
