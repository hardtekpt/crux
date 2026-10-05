package com.hardtekpt.crux.data

import androidx.room.withTransaction
import com.hardtekpt.crux.data.local.AreaEntity
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.PlaceEntity
import com.hardtekpt.crux.data.local.ProblemEntity
import com.hardtekpt.crux.data.local.ProblemStatsRow
import com.hardtekpt.crux.data.model.Area
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.Place
import com.hardtekpt.crux.data.model.PlaceDetail
import com.hardtekpt.crux.data.model.PlaceSummary
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.model.Problem
import com.hardtekpt.crux.data.model.ProblemStats
import com.hardtekpt.crux.data.model.ProblemWithStats
import com.hardtekpt.crux.data.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** What the place form edits. `id == 0` creates. */
data class PlaceInput(
    val id: Long = 0,
    val name: String,
    val type: PlaceType,
    val location: String?,
    val boulderScale: GradeScale?,
    val routeScale: GradeScale?,
    val defaultAngle: Int?,
    val notes: String?,
)

/** What the problem form edits. `id == 0` creates. */
data class ProblemInput(
    val id: Long = 0,
    val placeId: Long,
    val areaId: Long?,
    val name: String,
    val discipline: Discipline,
    val gradeScale: GradeScale,
    val gradeIndex: Int,
    val tape: Int?,
    val notes: String?,
)

interface PlaceRepository {
    fun observePlaces(): Flow<List<PlaceSummary>>
    fun observePlaceDetail(id: Long): Flow<PlaceDetail?>
    fun observeProblem(id: Long): Flow<ProblemWithStats?>
    /** Problems with goes but no send, most recently tried first. */
    fun observeProjects(): Flow<List<Project>>
    suspend fun getPlace(id: Long): Place?
    suspend fun getProblem(id: Long): Problem?
    suspend fun savePlace(input: PlaceInput): Long
    suspend fun deletePlace(id: Long)
    suspend fun saveArea(placeId: Long, areaId: Long, name: String, angle: Int?, imagePath: String?): Long
    suspend fun deleteArea(id: Long)
    /** Records a reset today and retires the problems that were on the wall. */
    suspend fun resetArea(id: Long)
    suspend fun saveProblem(input: ProblemInput): Long
    suspend fun setRetired(problemId: Long, retired: Boolean)
    suspend fun deleteProblem(id: Long)
}

class OfflinePlaceRepository @Inject constructor(
    private val dbs: CruxDatabases,
    private val clock: Clock,
) : PlaceRepository {

    override fun observePlaces(): Flow<List<PlaceSummary>> = dbs.observe { db ->
        combine(db.placeDao().observePlaces(), db.placeDao().observeActivity()) { places, activity ->
            val byPlace = activity.associateBy { it.placeId }
            places.map { entity ->
                val stats = byPlace[entity.id]
                PlaceSummary(
                    place = entity.toModel(),
                    climbs = stats?.climbs ?: 0,
                    lastVisit = stats?.lastEpochDay?.let(LocalDate::ofEpochDay),
                )
            }.sortedWith(compareByDescending<PlaceSummary> { it.lastVisit }.thenBy { it.place.name.lowercase() })
        }
    }

    override fun observePlaceDetail(id: Long): Flow<PlaceDetail?> = dbs.observe { db ->
        val dao = db.placeDao()
        combine(dao.observePlace(id), dao.observeAreas(id), dao.observeProblems(id), dao.observeProblemStats()) { place, areas, problems, stats ->
            place ?: return@combine null
            val byProblem = stats.associateBy { it.problemId }
            PlaceDetail(
                place = place.toModel(),
                areas = areas.map { it.toModel() },
                problems = problems.map { ProblemWithStats(it.toModel(), byProblem[it.id]?.toModel()) },
            )
        }
    }

    override fun observeProblem(id: Long): Flow<ProblemWithStats?> = dbs.observe { db ->
        combine(db.placeDao().observeProblem(id), db.placeDao().observeProblemStats()) { problem, stats ->
            problem?.let { p -> ProblemWithStats(p.toModel(), stats.firstOrNull { it.problemId == id }?.toModel()) }
        }
    }

    override fun observeProjects(): Flow<List<Project>> = dbs.observe { db ->
        val dao = db.placeDao()
        combine(dao.observePlaces(), dao.observeAllAreas(), dao.observeAllProblems(), dao.observeProblemStats()) { places, areas, problems, stats ->
            val placeNames = places.associate { it.id to it.name }
            val areaNames = areas.associate { it.id to it.name }
            val byId = problems.associateBy { it.id }
            stats.filter { it.firstSendEpochDay == null }
                .mapNotNull { row ->
                    val problem = byId[row.problemId]?.takeIf { !it.retired } ?: return@mapNotNull null
                    Project(
                        problem = problem.toModel(),
                        placeName = placeNames[problem.placeId].orEmpty(),
                        areaName = problem.areaId?.let(areaNames::get),
                        stats = row.toModel(),
                    )
                }
                .sortedByDescending { it.stats.lastGo }
        }
    }

    override suspend fun getPlace(id: Long): Place? = dbs.current().placeDao().getPlace(id)?.toModel()

    override suspend fun getProblem(id: Long): Problem? = dbs.current().placeDao().getProblem(id)?.toModel()

    override suspend fun savePlace(input: PlaceInput): Long {
        val dao = dbs.current().placeDao()
        val existing = if (input.id != 0L) dao.getPlace(input.id) else null
        val entity = PlaceEntity(
            id = existing?.id ?: 0,
            name = input.name.trim(),
            type = input.type,
            location = input.location?.trim()?.takeIf { it.isNotEmpty() },
            boulderScale = input.boulderScale,
            routeScale = input.routeScale,
            defaultAngle = input.defaultAngle,
            notes = input.notes?.trim()?.takeIf { it.isNotEmpty() },
            createdAtMillis = existing?.createdAtMillis ?: clock.millis(),
        )
        return if (existing != null) {
            dao.updatePlace(entity)
            existing.id
        } else {
            dao.insertPlace(entity)
        }
    }

    override suspend fun deletePlace(id: Long) {
        val db = dbs.current()
        db.withTransaction {
            db.placeDao().unlinkClimbsFromPlace(id)
            db.placeDao().deletePlace(id)
        }
    }

    override suspend fun saveArea(placeId: Long, areaId: Long, name: String, angle: Int?, imagePath: String?): Long {
        val dao = dbs.current().placeDao()
        return if (areaId != 0L) {
            val existing = dao.getAllAreas().first { it.id == areaId }
            dao.updateArea(existing.copy(name = name.trim(), angle = angle, imagePath = imagePath))
            areaId
        } else {
            dao.insertArea(
                AreaEntity(placeId = placeId, name = name.trim(), angle = angle, position = dao.nextAreaPosition(placeId), imagePath = imagePath),
            )
        }
    }

    override suspend fun deleteArea(id: Long) {
        val db = dbs.current()
        db.withTransaction {
            db.placeDao().unlinkClimbsFromArea(id)
            db.placeDao().deleteArea(id)
        }
    }

    override suspend fun resetArea(id: Long) {
        val db = dbs.current()
        db.withTransaction {
            val dao = db.placeDao()
            val area = dao.getAllAreas().first { it.id == id }
            dao.updateArea(area.copy(resetEpochDay = LocalDate.now(clock).toEpochDay()))
            dao.retireProblemsOnArea(id)
        }
    }

    override suspend fun saveProblem(input: ProblemInput): Long {
        val dao = dbs.current().placeDao()
        val existing = if (input.id != 0L) dao.getProblem(input.id) else null
        val entity = ProblemEntity(
            id = existing?.id ?: 0,
            placeId = input.placeId,
            areaId = input.areaId,
            name = input.name.trim(),
            discipline = input.discipline,
            gradeScale = input.gradeScale,
            gradeIndex = input.gradeIndex,
            tape = input.tape,
            setEpochDay = existing?.setEpochDay ?: LocalDate.now(clock).toEpochDay(),
            retired = existing?.retired ?: false,
            notes = input.notes?.trim()?.takeIf { it.isNotEmpty() },
            createdAtMillis = existing?.createdAtMillis ?: clock.millis(),
        )
        return if (existing != null) {
            dao.updateProblem(entity)
            existing.id
        } else {
            dao.insertProblem(entity)
        }
    }

    override suspend fun setRetired(problemId: Long, retired: Boolean) {
        val dao = dbs.current().placeDao()
        dao.getProblem(problemId)?.let { dao.updateProblem(it.copy(retired = retired)) }
    }

    override suspend fun deleteProblem(id: Long) {
        val db = dbs.current()
        db.withTransaction {
            db.placeDao().unlinkClimbsFromProblem(id)
            db.placeDao().deleteProblem(id)
        }
    }
}

internal fun PlaceEntity.toModel() = Place(id, name, type, location, boulderScale, routeScale, defaultAngle, notes)

internal fun AreaEntity.toModel() = Area(id, placeId, name, angle, resetEpochDay?.let(LocalDate::ofEpochDay), imagePath)

internal fun ProblemEntity.toModel() = Problem(
    id = id,
    placeId = placeId,
    areaId = areaId,
    name = name,
    discipline = discipline,
    gradeScale = gradeScale,
    gradeIndex = gradeIndex,
    tape = tape,
    setDate = setEpochDay?.let(LocalDate::ofEpochDay),
    retired = retired,
    notes = notes,
)

private fun ProblemStatsRow.toModel() = ProblemStats(
    sessions = sessions,
    attempts = attempts,
    firstSend = firstSendEpochDay?.let(LocalDate::ofEpochDay),
    lastGo = LocalDate.ofEpochDay(lastEpochDay),
)
