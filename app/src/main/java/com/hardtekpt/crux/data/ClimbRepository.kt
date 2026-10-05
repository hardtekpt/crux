package com.hardtekpt.crux.data

import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.ClimbEntity
import com.hardtekpt.crux.data.local.PersonalBestRow
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.NewClimb
import com.hardtekpt.crux.data.model.PersonalBest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

interface ClimbRepository {
    fun observeClimbs(): Flow<List<Climb>>
    fun observeRecentClimbs(limit: Int): Flow<List<Climb>>
    fun observeClimbsSince(from: LocalDate): Flow<List<Climb>>
    fun observeClimbCount(): Flow<Int>
    fun observePersonalBests(): Flow<List<PersonalBest>>
    suspend fun logClimb(climb: NewClimb): Long
    suspend fun getClimb(id: Long): Climb?
    /** Replaces a logged climb's details, keeping when it was first logged. */
    suspend fun updateClimb(id: Long, climb: NewClimb)
    suspend fun deleteClimb(id: Long)
    fun observeClimbsForProblem(problemId: Long): Flow<List<Climb>>
    fun observeClimbsAtPlace(placeId: Long): Flow<List<Climb>>
}

class OfflineClimbRepository @Inject constructor(
    private val dbs: CruxDatabases,
    private val clock: Clock,
) : ClimbRepository {
    override fun observeClimbs(): Flow<List<Climb>> =
        dbs.observe { it.climbDao().observeAll() }.map { it.map(ClimbEntity::toModel) }

    override fun observeRecentClimbs(limit: Int): Flow<List<Climb>> =
        dbs.observe { it.climbDao().observeRecent(limit) }.map { it.map(ClimbEntity::toModel) }

    override fun observeClimbsSince(from: LocalDate): Flow<List<Climb>> =
        dbs.observe { it.climbDao().observeSince(from.toEpochDay()) }.map { it.map(ClimbEntity::toModel) }

    override fun observeClimbCount(): Flow<Int> = dbs.observe { it.climbDao().observeCount() }

    override fun observePersonalBests(): Flow<List<PersonalBest>> =
        dbs.observe { it.climbDao().observePersonalBests() }.map { it.map(PersonalBestRow::toModel) }

    override suspend fun logClimb(climb: NewClimb): Long = dbs.current().climbDao().insert(climb.toEntity(clock.millis()))

    override suspend fun getClimb(id: Long): Climb? = dbs.current().climbDao().get(id)?.toModel()

    override suspend fun updateClimb(id: Long, climb: NewClimb) {
        val dao = dbs.current().climbDao()
        val existing = dao.get(id) ?: return
        dao.update(climb.toEntity(existing.createdAtMillis).copy(id = id))
    }

    override suspend fun deleteClimb(id: Long) = dbs.current().climbDao().delete(id)

    override fun observeClimbsForProblem(problemId: Long): Flow<List<Climb>> =
        dbs.observe { it.climbDao().observeForProblem(problemId) }.map { it.map(ClimbEntity::toModel) }

    override fun observeClimbsAtPlace(placeId: Long): Flow<List<Climb>> =
        dbs.observe { it.climbDao().observeAtPlace(placeId) }.map { it.map(ClimbEntity::toModel) }
}

private fun NewClimb.toEntity(createdAtMillis: Long) = ClimbEntity(
    discipline = discipline,
    gradeScale = gradeScale,
    gradeIndex = gradeIndex,
    style = style,
    attempts = attempts,
    venue = venue,
    dateEpochDay = date.toEpochDay(),
    createdAtMillis = createdAtMillis,
    name = name?.trim()?.takeIf { it.isNotEmpty() },
    place = place?.trim()?.takeIf { it.isNotEmpty() },
    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
    placeId = placeId,
    areaId = areaId,
    problemId = problemId,
    angle = angle,
    effort = effort,
)

internal fun ClimbEntity.toModel() = Climb(
    id = id,
    discipline = discipline,
    gradeScale = gradeScale,
    gradeIndex = gradeIndex,
    style = style,
    attempts = attempts,
    venue = venue,
    date = LocalDate.ofEpochDay(dateEpochDay),
    name = name,
    place = place,
    notes = notes,
    placeId = placeId,
    areaId = areaId,
    problemId = problemId,
    angle = angle,
    effort = effort,
)

internal fun PersonalBestRow.toModel() = PersonalBest(
    discipline = discipline,
    style = style,
    gradeScale = gradeScale,
    gradeIndex = gradeIndex,
    name = name,
    place = place,
    date = LocalDate.ofEpochDay(dateEpochDay),
)
