package com.hardtekpt.crux.data

import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.NewClimb
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One more go on a saved problem, without the Log climb form: an attempt today, at the
 * problem's place, facility and wall, in its grade. For the "+1 go" on project rows.
 */
class QuickGo(private val climbs: ClimbRepository, private val places: PlaceRepository, private val clock: Clock) {

    /** Logs the go and returns the new climb's id, or null if the problem is gone. */
    suspend fun log(problemId: Long): Long? {
        val problem = places.getProblem(problemId) ?: return null
        val detail = problem.placeId?.let { places.observePlaceDetail(it).first() }
        val place = detail?.place
        val area = detail?.areas?.firstOrNull { it.id == problem.areaId }
        val section = place?.sections?.firstOrNull { it.id == problem.sectionId } ?: place?.sectionOf(area)
        return climbs.logClimb(
            NewClimb(
                discipline = problem.discipline,
                gradeScale = problem.gradeScale,
                gradeIndex = problem.gradeIndex,
                style = AscentStyle.ATTEMPT,
                attempts = 1,
                venue = (section?.type ?: place?.typeOf(area) ?: com.hardtekpt.crux.data.model.PlaceType.GYM).venue,
                date = LocalDate.now(clock),
                name = problem.name,
                place = place?.name,
                notes = null,
                placeId = problem.placeId,
                areaId = problem.areaId,
                problemId = problem.id,
                angle = area?.angle,
                gradeLabel = problem.gradeLabel,
                gradeColour = problem.gradeColour,
                sectionId = section?.id,
            ),
        )
    }

    /** Takes a go logged by mistake back out. */
    suspend fun undo(climbId: Long) = climbs.deleteClimb(climbId)
}

/** A go just logged with "+1 go", which can still be undone. */
data class LoggedGo(val problemId: Long, val climbId: Long)

/**
 * "+1 go" for a screen: logs the go, keeps it undoable for a few seconds, and takes it back
 * on Undo. [scope] is the view model's.
 */
class QuickGoState(private val quickGo: QuickGo, private val scope: kotlinx.coroutines.CoroutineScope) {
    private val _last = kotlinx.coroutines.flow.MutableStateFlow<LoggedGo?>(null)
    val last: kotlinx.coroutines.flow.StateFlow<LoggedGo?> = _last

    fun log(problemId: Long) {
        scope.launch {
            val climbId = quickGo.log(problemId) ?: return@launch
            val go = LoggedGo(problemId, climbId)
            _last.value = go
            kotlinx.coroutines.delay(UNDO_MILLIS)
            _last.compareAndSet(go, null)
        }
    }

    fun undo() {
        val go = _last.value ?: return
        _last.value = null
        scope.launch { quickGo.undo(go.climbId) }
    }

    companion object {
        const val UNDO_MILLIS = 6_000L
    }
}
