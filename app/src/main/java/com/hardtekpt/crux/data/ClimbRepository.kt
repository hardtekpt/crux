package com.hardtekpt.crux.data

import androidx.room.withTransaction
import com.hardtekpt.crux.data.local.ClimbEntity
import com.hardtekpt.crux.data.local.ClimbMediaEntity
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.MediaKind
import com.hardtekpt.crux.data.local.PersonalBestRow
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.NewClimb
import com.hardtekpt.crux.data.model.PersonalBest
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

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

    /** Attaches a photo or video to a climb, replacing any of that kind; null removes it. Returns the old file name. */
    suspend fun setClimbMedia(climbId: Long, kind: MediaKind, path: String?): String?
}

class OfflineClimbRepository @Inject constructor(private val dbs: CruxDatabases, private val clock: Clock) : ClimbRepository {
    /** Climbs from a query, each with its photo and video if it has them. */
    private fun withMedia(query: (CruxDatabase) -> Flow<List<ClimbEntity>>): Flow<List<Climb>> = dbs.observe { db ->
        combine(query(db), db.climbMediaDao().observeAll()) { climbs, media ->
            val images = media.filter { it.kind == MediaKind.IMAGE }.associate { it.climbId to it.path }
            val videos = media.filter { it.kind == MediaKind.VIDEO }.associate { it.climbId to it.path }
            climbs.map { it.toModel().copy(imagePath = images[it.id], videoPath = videos[it.id]) }
        }
    }

    override fun observeClimbs(): Flow<List<Climb>> = withMedia { it.climbDao().observeAll() }

    override fun observeRecentClimbs(limit: Int): Flow<List<Climb>> = withMedia { it.climbDao().observeRecent(limit) }

    override fun observeClimbsSince(from: LocalDate): Flow<List<Climb>> = withMedia { it.climbDao().observeSince(from.toEpochDay()) }

    override fun observeClimbCount(): Flow<Int> = dbs.observe { it.climbDao().observeCount() }

    override fun observePersonalBests(): Flow<List<PersonalBest>> =
        dbs.observe { it.climbDao().observePersonalBests() }.map { it.map(PersonalBestRow::toModel) }

    override suspend fun logClimb(climb: NewClimb): Long = dbs.current().climbDao().insert(climb.toEntity(clock.millis()))

    override suspend fun getClimb(id: Long): Climb? {
        val db = dbs.current()
        val media = db.climbMediaDao()
        return db.climbDao().get(id)?.toModel()?.copy(
            imagePath = media.get(id, MediaKind.IMAGE)?.path,
            videoPath = media.get(id, MediaKind.VIDEO)?.path,
        )
    }

    override suspend fun setClimbMedia(climbId: Long, kind: MediaKind, path: String?): String? {
        val db = dbs.current()
        return db.withTransaction {
            val dao = db.climbMediaDao()
            val old = dao.get(climbId, kind)?.path
            dao.delete(climbId, kind)
            if (path != null) dao.insert(ClimbMediaEntity(climbId = climbId, kind = kind, path = path, createdAtMillis = clock.millis()))
            old
        }
    }

    override suspend fun updateClimb(id: Long, climb: NewClimb) {
        val dao = dbs.current().climbDao()
        val existing = dao.get(id) ?: return
        dao.update(climb.toEntity(existing.createdAtMillis).copy(id = id))
    }

    override suspend fun deleteClimb(id: Long) = dbs.current().climbDao().delete(id)

    override fun observeClimbsForProblem(problemId: Long): Flow<List<Climb>> = withMedia { it.climbDao().observeForProblem(problemId) }

    override fun observeClimbsAtPlace(placeId: Long): Flow<List<Climb>> = withMedia { it.climbDao().observeAtPlace(placeId) }
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
    sectionId = sectionId,
    problemId = problemId,
    angle = angle,
    effort = effort,
    gradeLabel = gradeLabel.takeIf { gradeScale.isLocal },
    gradeColour = gradeColour.takeIf { gradeScale.isLocal },
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
    sectionId = sectionId,
    problemId = problemId,
    angle = angle,
    effort = effort,
    gradeLabel = gradeLabel.takeIf { gradeScale.isLocal },
    gradeColour = gradeColour.takeIf { gradeScale.isLocal },
)

internal fun PersonalBestRow.toModel() = PersonalBest(
    discipline = discipline,
    style = style,
    gradeScale = gradeScale,
    gradeIndex = gradeIndex,
    name = name,
    place = place,
    date = LocalDate.ofEpochDay(dateEpochDay),
    placeId = placeId,
    gradeLabel = gradeLabel,
    gradeColour = gradeColour,
)
