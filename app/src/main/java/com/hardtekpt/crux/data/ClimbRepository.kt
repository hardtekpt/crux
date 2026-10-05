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

    override suspend fun logClimb(climb: NewClimb): Long = dbs.current().climbDao().insert(
        ClimbEntity(
            discipline = climb.discipline,
            gradeScale = climb.gradeScale,
            gradeIndex = climb.gradeIndex,
            style = climb.style,
            attempts = climb.attempts,
            venue = climb.venue,
            dateEpochDay = climb.date.toEpochDay(),
            createdAtMillis = clock.millis(),
            name = climb.name?.trim()?.takeIf { it.isNotEmpty() },
            place = climb.place?.trim()?.takeIf { it.isNotEmpty() },
            notes = climb.notes?.trim()?.takeIf { it.isNotEmpty() },
        ),
    )
}

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
