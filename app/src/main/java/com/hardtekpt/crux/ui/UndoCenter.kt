package com.hardtekpt.crux.ui

import androidx.lifecycle.ViewModel
import com.hardtekpt.crux.data.local.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch

/**
 * Deletes that can be taken back. A screen deletes something simple (a log, a note, a result),
 * closes, and offers it here; the app shows "Deleted … · Undo" above the nav bar, wherever the
 * climber lands. Undo puts it back; when the snackbar goes, [Pending.commit] finishes the job (for
 * a log, removing its photo and video files). A new offer settles the one before it.
 *
 * Deletes that take other things with them (a place, a plan on the schedule, a dataset) still ask
 * first in a dialog.
 */
@Singleton
class UndoCenter @Inject constructor(@ApplicationScope private val scope: CoroutineScope) {
    class Pending internal constructor(val id: Long, val message: String, internal val undo: suspend () -> Unit, internal val commit: suspend () -> Unit)

    private val ids = AtomicLong()
    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    fun offer(message: String, undo: suspend () -> Unit, commit: suspend () -> Unit = {}) {
        val next = Pending(ids.incrementAndGet(), message, undo, commit)
        _pending.getAndUpdate { next }?.let { previous -> scope.launch { previous.commit() } }
    }

    fun undo(pending: Pending) {
        if (_pending.compareAndSet(pending, null)) scope.launch { pending.undo() }
    }

    fun settle(pending: Pending) {
        if (_pending.compareAndSet(pending, null)) scope.launch { pending.commit() }
    }
}

/** Hands the app shell the [UndoCenter], for its snackbar. */
@HiltViewModel
class UndoViewModel @Inject constructor(val center: UndoCenter) : ViewModel()
