package com.hardtekpt.crux.data

import androidx.room.withTransaction
import com.hardtekpt.crux.data.local.AreaEntity
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.PlaceEntity
import com.hardtekpt.crux.data.local.ProblemEntity
import com.hardtekpt.crux.data.local.ProblemStatsRow
import com.hardtekpt.crux.data.local.SectionEntity
import com.hardtekpt.crux.data.model.Area
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.LocalScale
import com.hardtekpt.crux.data.model.MapLocation
import com.hardtekpt.crux.data.model.Place
import com.hardtekpt.crux.data.model.PlaceDetail
import com.hardtekpt.crux.data.model.PlaceSummary
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.model.Problem
import com.hardtekpt.crux.data.model.ProblemStats
import com.hardtekpt.crux.data.model.ProblemWithStats
import com.hardtekpt.crux.data.model.Project
import com.hardtekpt.crux.data.model.Section
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** What the place form edits. `id == 0` creates. */
data class PlaceInput(
    val id: Long = 0,
    val name: String,
    /** Every kind of climbing here, main first; used when [sections] isn't given. */
    val types: List<PlaceType> = listOf(PlaceType.GYM),
    val location: String?,
    val defaultAngle: Int?,
    val notes: String?,
    val favourite: Boolean = false,
    val mapLocation: MapLocation? = null,
    /** The place's named parts, in order; at least one. A blank name becomes the kind's. */
    val sections: List<SectionInput> = types.map { SectionInput(type = it, name = it.label) },
)

/** One section as the place form edits it. `id == 0` creates. */
data class SectionInput(
    val id: Long = 0,
    val type: PlaceType,
    val name: String,
    /** Null uses the climber's settings. */
    val boulderScale: GradeScale? = null,
    val routeScale: GradeScale? = null,
    /** Kept only where a scale is local. */
    val localScale: LocalScale? = null,
)

/** What the problem form edits. `id == 0` creates. */
data class ProblemInput(
    val id: Long = 0,
    val placeId: Long?,
    val areaId: Long?,
    val name: String,
    val discipline: Discipline,
    val gradeScale: GradeScale,
    val gradeIndex: Int,
    val tape: Int?,
    val notes: String?,
    val gradeLabel: String? = null,
    val gradeColour: Long? = null,
    /** The facility, when there's no wall to tell it. */
    val sectionId: Long? = null,
)

interface PlaceRepository {
    fun observePlaces(): Flow<List<PlaceSummary>>
    fun observePlaceDetail(id: Long): Flow<PlaceDetail?>
    fun observeProblem(id: Long): Flow<ProblemWithStats?>

    /** Climbs with goes but no send, at a place or not, most recently tried first. */
    fun observeProjects(): Flow<List<Project>>

    /** Climbs at a place (or with no place, for null), for picking one to log more goes on. */
    suspend fun climbsAt(placeId: Long?): List<Problem>
    suspend fun getPlace(id: Long): Place?
    suspend fun getProblem(id: Long): Problem?
    suspend fun savePlace(input: PlaceInput): Long
    suspend fun deletePlace(id: Long)
    suspend fun saveArea(placeId: Long, areaId: Long, name: String, angle: Int?, imagePath: String?, sectionId: Long? = null): Long
    suspend fun deleteArea(id: Long)

    /** Records a reset today and retires the problems that were on the wall. */
    suspend fun resetArea(id: Long)
    suspend fun saveProblem(input: ProblemInput): Long
    suspend fun setRetired(problemId: Long, retired: Boolean)
    suspend fun deleteProblem(id: Long)
}

class OfflinePlaceRepository @Inject constructor(private val dbs: CruxDatabases, private val clock: Clock) : PlaceRepository {

    override fun observePlaces(): Flow<List<PlaceSummary>> = dbs.observe { db ->
        val dao = db.placeDao()
        val placesWithSections = combine(dao.observePlaces(), dao.observeAllSections()) { places, sections ->
            val byPlace = sections.groupBy { it.placeId }
            places.map { it.toModel(byPlace[it.id].orEmpty()) }
        }
        combine(placesWithSections, dao.observeActivity(), dao.observeAllAreas(), dao.observeAllProblems(), dao.observeProblemStats()) {
                places,
                activity,
                areas,
                problems,
                problemStats,
            ->
            val byPlace = activity.associateBy { it.placeId }
            val areasByPlace = areas.groupBy { it.placeId }
            val liveProblems = problems.filter { !it.retired }.groupBy { it.placeId }
            val statsByProblem = problemStats.associateBy { it.problemId }
            places.map { place ->
                val stats = byPlace[place.id]
                val here = liveProblems[place.id].orEmpty()
                PlaceSummary(
                    place = place,
                    climbs = stats?.climbs ?: 0,
                    lastVisit = stats?.lastEpochDay?.let(LocalDate::ofEpochDay),
                    walls = areasByPlace[place.id]?.size ?: 0,
                    problems = here.size,
                    openProjects = here.count { p -> statsByProblem[p.id]?.let { it.firstSendEpochDay == null } == true },
                    coverImage = areasByPlace[place.id].orEmpty().sortedBy { it.position }.firstNotNullOfOrNull { it.imagePath },
                )
            }.sortedWith(compareByDescending<PlaceSummary> { it.lastVisit }.thenBy { it.place.name.lowercase() })
        }
    }

    override fun observePlaceDetail(id: Long): Flow<PlaceDetail?> = dbs.observe { db ->
        val dao = db.placeDao()
        combine(dao.observePlace(id), dao.observeSections(id), dao.observeAreas(id), dao.observeProblems(id), dao.observeProblemStats()) {
                place,
                sections,
                areas,
                problems,
                stats,
            ->
            place ?: return@combine null
            val byProblem = stats.associateBy { it.problemId }
            PlaceDetail(
                place = place.toModel(sections),
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
                        placeName = problem.placeId?.let(placeNames::get).orEmpty(),
                        areaName = problem.areaId?.let(areaNames::get),
                        stats = row.toModel(),
                    )
                }
                .sortedByDescending { it.stats.lastGo }
        }
    }

    override suspend fun getPlace(id: Long): Place? {
        val dao = dbs.current().placeDao()
        return dao.getPlace(id)?.toModel(dao.getSections(id))
    }

    override suspend fun getProblem(id: Long): Problem? = dbs.current().placeDao().getProblem(id)?.toModel()

    override suspend fun climbsAt(placeId: Long?): List<Problem> = dbs.current().placeDao().getClimbsAt(placeId).map { it.toModel() }

    override suspend fun savePlace(input: PlaceInput): Long {
        val db = dbs.current()
        return db.withTransaction { savePlaceIn(db, input) }
    }

    private suspend fun savePlaceIn(db: com.hardtekpt.crux.data.local.CruxDatabase, input: PlaceInput): Long {
        val dao = db.placeDao()
        val sections = input.sections.ifEmpty { listOf(SectionInput(type = PlaceType.GYM, name = PlaceType.GYM.label)) }
        val kinds = sections.map { it.type }.distinct()
        val existing = if (input.id != 0L) dao.getPlace(input.id) else null
        val entity = PlaceEntity(
            id = existing?.id ?: 0,
            name = input.name.trim(),
            // The kinds, kept alongside the sections for filters and older code.
            type = kinds.first(),
            extraTypes = kinds.drop(1).joinToString(",") { it.name },
            location = input.location?.trim()?.takeIf { it.isNotEmpty() },
            defaultAngle = input.defaultAngle,
            notes = input.notes?.trim()?.takeIf { it.isNotEmpty() },
            createdAtMillis = existing?.createdAtMillis ?: clock.millis(),
            favourite = input.favourite,
            latitude = input.mapLocation?.latitude,
            longitude = input.mapLocation?.longitude,
            address = input.mapLocation?.address,
        )
        val placeId = if (existing != null) {
            dao.updatePlace(entity)
            existing.id
        } else {
            dao.insertPlace(entity)
        }
        // Sections: update the kept ones, add new ones, drop the removed ones (their areas
        // fall back to the first section, their climbs just lose the link).
        val before = dao.getSections(placeId).associateBy { it.id }
        val kept = mutableSetOf<Long>()
        sections.forEachIndexed { position, section ->
            val name = section.name.trim().ifEmpty { section.type.label }.take(40)
            val old = before[section.id]
            // Boards hold boulders only; local grades are kept only where a scale uses them.
            val route = section.routeScale.takeIf { section.type != PlaceType.BOARD }
            val local = section.localScale?.takeIf { section.boulderScale?.isLocal == true || route?.isLocal == true }?.encode()
            val fresh = SectionEntity(
                placeId = placeId,
                type = section.type,
                name = name,
                position = position,
                boulderScale = section.boulderScale,
                routeScale = route,
                localScale = local,
            )
            if (old != null) {
                dao.updateSection(fresh.copy(id = old.id))
                kept += old.id
            } else {
                kept += dao.insertSection(fresh)
            }
        }
        before.keys.filter { it !in kept }.forEach { id ->
            dao.unlinkClimbsFromSection(id)
            dao.deleteSection(id)
        }
        return placeId
    }

    override suspend fun deletePlace(id: Long) {
        val db = dbs.current()
        db.withTransaction {
            db.placeDao().unlinkClimbsFromPlace(id)
            db.placeDao().deletePlace(id)
        }
    }

    override suspend fun saveArea(placeId: Long, areaId: Long, name: String, angle: Int?, imagePath: String?, sectionId: Long?): Long {
        val dao = dbs.current().placeDao()
        val section = sectionId ?: dao.getSections(placeId).firstOrNull()?.id
        return if (areaId != 0L) {
            val existing = dao.getAllAreas().first { it.id == areaId }
            dao.updateArea(existing.copy(name = name.trim(), angle = angle, imagePath = imagePath, sectionId = section))
            areaId
        } else {
            dao.insertArea(
                AreaEntity(
                    placeId = placeId,
                    name = name.trim(),
                    angle = angle,
                    position = dao.nextAreaPosition(placeId),
                    imagePath = imagePath,
                    sectionId = section,
                ),
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
        val db = dbs.current()
        return db.withTransaction { saveProblemIn(db, input) }
    }

    private suspend fun saveProblemIn(db: com.hardtekpt.crux.data.local.CruxDatabase, input: ProblemInput): Long {
        val dao = db.placeDao()
        val existing = if (input.id != 0L) dao.getProblem(input.id) else null
        // A wall says which facility; without one, the facility given.
        val sectionId = input.areaId?.let { area -> dao.getAllAreas().firstOrNull { it.id == area }?.sectionId } ?: input.sectionId
        val entity = ProblemEntity(
            id = existing?.id ?: 0,
            placeId = input.placeId,
            areaId = input.areaId,
            sectionId = sectionId.takeIf { input.placeId != null },
            name = input.name.trim(),
            discipline = input.discipline,
            gradeScale = input.gradeScale,
            gradeIndex = input.gradeIndex,
            tape = input.tape,
            setEpochDay = existing?.setEpochDay ?: LocalDate.now(clock).toEpochDay(),
            retired = existing?.retired ?: false,
            notes = input.notes?.trim()?.takeIf { it.isNotEmpty() },
            createdAtMillis = existing?.createdAtMillis ?: clock.millis(),
            gradeLabel = input.gradeLabel.takeIf { input.gradeScale.isLocal },
            gradeColour = input.gradeColour.takeIf { input.gradeScale.isLocal },
        )
        return if (existing != null) {
            dao.updateProblem(entity)
            // Its logs show the climb as it is now.
            dao.syncLogs(
                id = existing.id,
                name = entity.name,
                discipline = entity.discipline,
                gradeScale = entity.gradeScale,
                gradeIndex = entity.gradeIndex,
                gradeLabel = entity.gradeLabel,
                gradeColour = entity.gradeColour,
                placeId = entity.placeId,
                sectionId = entity.sectionId,
                areaId = entity.areaId,
                placeName = entity.placeId?.let { dao.getPlace(it)?.name },
            )
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
            // A climb goes with its logs.
            db.placeDao().deleteLogsOfProblem(id)
            db.placeDao().deleteProblem(id)
        }
    }
}

internal fun PlaceEntity.toModel(sections: List<SectionEntity> = emptyList()) = Place(
    id, name, type, location, defaultAngle, notes, favourite,
    mapLocation = if (latitude != null && longitude != null) MapLocation(latitude, longitude, address) else null,
    types = listOf(type) + PlaceEntity.parseTypes(extraTypes).filter { it != type },
    sections = sections.sortedWith(
        compareBy({
            it.position
        }, { it.id }),
    ).map { Section(it.id, it.placeId, it.type, it.name, it.boulderScale, it.routeScale, LocalScale.decode(it.localScale)) },
)

internal fun PlaceEntity.Companion.parseTypes(text: String): List<PlaceType> =
    text.split(',').mapNotNull { name -> PlaceType.entries.firstOrNull { it.name == name.trim() } }.distinct()

internal fun AreaEntity.toModel() = Area(id, placeId, name, angle, resetEpochDay?.let(LocalDate::ofEpochDay), imagePath, type, sectionId)

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
    gradeLabel = gradeLabel,
    gradeColour = gradeColour,
    sectionId = sectionId,
)

private fun ProblemStatsRow.toModel() = ProblemStats(
    sessions = sessions,
    attempts = attempts,
    firstSend = firstSendEpochDay?.let(LocalDate::ofEpochDay),
    lastGo = LocalDate.ofEpochDay(lastEpochDay),
)
