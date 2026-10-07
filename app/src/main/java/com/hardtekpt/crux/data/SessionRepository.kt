package com.hardtekpt.crux.data

import androidx.room.withTransaction
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.SessionEntity
import com.hardtekpt.crux.data.local.SessionItemEntity
import com.hardtekpt.crux.data.local.SessionSetEntity
import com.hardtekpt.crux.data.local.SessionStatus
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.Exercise
import com.hardtekpt.crux.data.model.ExerciseTarget
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/** A set as logged: what was done, or that it was skipped. */
data class SessionSet(val setIndex: Int, val reps: Int?, val seconds: Int?, val loadKg: Double?, val skipped: Boolean)

/** One exercise in a session: its target and the sets logged so far. */
data class SessionItem(val id: Long, val exercise: Exercise, val blockName: String, val target: ExerciseTarget, val sets: List<SessionSet>) {
    /** Sets done or skipped. */
    val logged: Int get() = sets.size
    val done: Int get() = sets.count { !it.skipped }
    val finished: Boolean get() = sets.size >= target.sets

    /** The next set to log, or null when every planned set is in. */
    val nextSet: Int? get() = (0 until target.sets).firstOrNull { index -> sets.none { it.setIndex == index } }
}

/** Enough of the running session to log a climb into it. */
data class RunningSession(val id: Long, val placeId: Long?, val sectionId: Long?)

/** A session with everything in it. */
data class Session(
    val id: Long,
    val name: String,
    val templateId: Long?,
    val placeId: Long?,
    val sectionId: Long?,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val running: Boolean,
    val effort: Int?,
    val notes: String?,
    val items: List<SessionItem>,
    val climbs: List<Climb>,
) {
    val hasPlan: Boolean get() = templateId != null
    val setsPlanned: Int get() = items.sumOf { it.target.sets }
    val setsDone: Int get() = items.sumOf { it.done }
    val setsSkipped: Int get() = items.sumOf { item -> item.sets.count { it.skipped } }

    fun durationMillis(nowMillis: Long): Long = (endedAtMillis ?: nowMillis) - startedAtMillis

    fun date(zone: ZoneId): LocalDate = Instant.ofEpochMilli(startedAtMillis).atZone(zone).toLocalDate()
}

interface SessionRepository {
    /** The session running now, if any. */
    fun observeRunning(): Flow<Session?>

    /** The running session's id, its place and section, if a session is running. */
    suspend fun running(): RunningSession?
    fun observeSession(id: Long): Flow<Session?>

    /** Finished sessions, newest first, for the journal. */
    fun observeFinished(): Flow<List<Session>>

    /** Starts a session from a plan (copying its exercises) or without one. Ends any other running one first. */
    suspend fun start(templateId: Long?, placeId: Long?, sectionId: Long?): Long
    suspend fun logSet(itemId: Long, setIndex: Int, reps: Int?, seconds: Int?, loadKg: Double?)
    suspend fun skipSet(itemId: Long, setIndex: Int)
    suspend fun undoSet(itemId: Long, setIndex: Int)

    /** Adds an exercise during the session, with its usual target. */
    suspend fun addExercise(sessionId: Long, exercise: Exercise): Long
    suspend fun setPlace(sessionId: Long, placeId: Long?, sectionId: Long?)
    suspend fun finish(sessionId: Long, effort: Int?, notes: String?)

    /** Throws the session away; climbs logged in it stay in the journal. */
    suspend fun discard(sessionId: Long)
}

class OfflineSessionRepository @Inject constructor(private val dbs: CruxDatabases, private val templates: TemplateRepository, private val clock: Clock) :
    SessionRepository {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeRunning(): Flow<Session?> = dbs.observe { db ->
        db.sessionDao().observeRunning().flatMapLatest { session -> if (session == null) flowOf(null) else observeSession(session.id) }
    }

    override suspend fun running(): RunningSession? = dbs.current().sessionDao().getRunning()?.let { RunningSession(it.id, it.placeId, it.sectionId) }

    override fun observeSession(id: Long): Flow<Session?> = dbs.observe { db ->
        val dao = db.sessionDao()
        combine(
            dao.observeSession(id),
            dao.observeItems(id),
            dao.observeSets(id),
            db.exerciseDao().observeAll(),
            db.climbDao().observeAll(),
        ) { session, items, sets, exercises, climbs ->
            session ?: return@combine null
            session.toModel(items, sets, exercises.associateBy { it.id }, climbs.filter { it.sessionId == id }.map { it.toModel() })
        }
    }

    override fun observeFinished(): Flow<List<Session>> = dbs.observe { db ->
        val dao = db.sessionDao()
        combine(
            dao.observeFinished(),
            dao.observeAllItems(),
            dao.observeAllSets(),
            db.exerciseDao().observeAll(),
            db.climbDao().observeAll(),
        ) { sessions, items, sets, exercises, climbs ->
            val exerciseById = exercises.associateBy { it.id }
            val itemsBySession = items.groupBy { it.sessionId }
            val climbsBySession = climbs.filter { it.sessionId != null }.groupBy { it.sessionId }
            sessions.map { session ->
                val mine = itemsBySession[session.id].orEmpty()
                val ids = mine.map { it.id }.toSet()
                session.toModel(mine, sets.filter { it.itemId in ids }, exerciseById, climbsBySession[session.id].orEmpty().map { it.toModel() })
            }
        }
    }

    override suspend fun start(templateId: Long?, placeId: Long?, sectionId: Long?): Long {
        val plan = templateId?.let { templates.getTemplate(it) }
        val db = dbs.current()
        return db.withTransaction {
            val dao = db.sessionDao()
            // One session at a time: a forgotten one is finished as it stands.
            dao.getRunning()?.let { dao.updateSession(it.copy(status = SessionStatus.FINISHED, endedAtMillis = clock.millis())) }
            val id = dao.insertSession(
                SessionEntity(
                    name = plan?.name ?: CLIMBING_SESSION,
                    templateId = plan?.id,
                    placeId = placeId,
                    sectionId = sectionId,
                    startedAtMillis = clock.millis(),
                ),
            )
            var position = 0
            dao.insertItems(
                plan?.blocks.orEmpty().flatMap { block ->
                    block.items.map { item ->
                        SessionItemEntity(
                            sessionId = id,
                            exerciseId = item.exercise.id,
                            blockName = block.name,
                            position = position++,
                            sets = item.target.sets,
                            reps = item.target.reps,
                            seconds = item.target.seconds,
                            loadKg = item.target.loadKg,
                            restSeconds = item.target.restSeconds,
                            repRestSeconds = item.target.repRestSeconds,
                        )
                    }
                },
            )
            id
        }
    }

    override suspend fun logSet(itemId: Long, setIndex: Int, reps: Int?, seconds: Int?, loadKg: Double?) {
        val dao = dbs.current().sessionDao()
        dao.deleteSet(itemId, setIndex)
        dao.insertSet(
            SessionSetEntity(itemId = itemId, setIndex = setIndex, reps = reps, seconds = seconds, loadKg = loadKg, completedAtMillis = clock.millis()),
        )
    }

    override suspend fun skipSet(itemId: Long, setIndex: Int) {
        val dao = dbs.current().sessionDao()
        dao.deleteSet(itemId, setIndex)
        dao.insertSet(SessionSetEntity(itemId = itemId, setIndex = setIndex, skipped = true, completedAtMillis = clock.millis()))
    }

    override suspend fun undoSet(itemId: Long, setIndex: Int) = dbs.current().sessionDao().deleteSet(itemId, setIndex)

    override suspend fun addExercise(sessionId: Long, exercise: Exercise): Long {
        val dao = dbs.current().sessionDao()
        val target = ExerciseTarget.defaultFor(exercise)
        return dao.insertItem(
            SessionItemEntity(
                sessionId = sessionId,
                exerciseId = exercise.id,
                position = dao.nextItemPosition(sessionId),
                sets = target.sets,
                reps = target.reps,
                seconds = target.seconds,
                loadKg = target.loadKg,
                restSeconds = target.restSeconds,
                repRestSeconds = target.repRestSeconds,
            ),
        )
    }

    override suspend fun setPlace(sessionId: Long, placeId: Long?, sectionId: Long?) {
        val dao = dbs.current().sessionDao()
        dao.getSession(sessionId)?.let { dao.updateSession(it.copy(placeId = placeId, sectionId = sectionId)) }
    }

    override suspend fun finish(sessionId: Long, effort: Int?, notes: String?) {
        val dao = dbs.current().sessionDao()
        dao.getSession(sessionId)?.let {
            dao.updateSession(
                it.copy(
                    status = SessionStatus.FINISHED,
                    endedAtMillis = it.endedAtMillis ?: clock.millis(),
                    effort = effort?.coerceIn(1, 10),
                    notes = notes?.trim()?.takeIf { n -> n.isNotEmpty() },
                ),
            )
        }
    }

    override suspend fun discard(sessionId: Long) {
        val db = dbs.current()
        db.withTransaction {
            db.sessionDao().unlinkClimbs(sessionId)
            db.sessionDao().deleteSession(sessionId)
        }
    }

    companion object {
        const val CLIMBING_SESSION = "Climbing session"
    }
}

private fun SessionEntity.toModel(
    items: List<SessionItemEntity>,
    sets: List<SessionSetEntity>,
    exercises: Map<Long, com.hardtekpt.crux.data.local.ExerciseEntity>,
    climbs: List<Climb>,
): Session {
    val setsByItem = sets.groupBy { it.itemId }
    return Session(
        id = id,
        name = name,
        templateId = templateId,
        placeId = placeId,
        sectionId = sectionId,
        startedAtMillis = startedAtMillis,
        endedAtMillis = endedAtMillis,
        running = status == SessionStatus.RUNNING,
        effort = effort,
        notes = notes,
        items = items.sortedBy { it.position }.mapNotNull { item ->
            val exercise = exercises[item.exerciseId] ?: return@mapNotNull null
            SessionItem(
                id = item.id,
                exercise = Exercise(exercise.id, exercise.name, exercise.category, exercise.metric, exercise.notes, exercise.defaults, exercise.prepSeconds),
                blockName = item.blockName,
                target = ExerciseTarget(
                    sets = item.sets,
                    reps = item.reps,
                    seconds = item.seconds,
                    loadKg = item.loadKg,
                    restSeconds = item.restSeconds,
                    repRestSeconds = item.repRestSeconds,
                ),
                sets = setsByItem[item.id].orEmpty().sortedBy { it.setIndex }.map { SessionSet(it.setIndex, it.reps, it.seconds, it.loadKg, it.skipped) },
            )
        },
        climbs = climbs,
    )
}
