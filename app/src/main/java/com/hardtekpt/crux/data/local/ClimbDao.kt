package com.hardtekpt.crux.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClimbDao {
    @Query("SELECT * FROM climbs ORDER BY dateEpochDay DESC, createdAtMillis DESC")
    fun observeAll(): Flow<List<ClimbEntity>>

    @Query("SELECT * FROM climbs ORDER BY dateEpochDay DESC, createdAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ClimbEntity>>

    @Query("SELECT * FROM climbs WHERE dateEpochDay >= :fromEpochDay")
    fun observeSince(fromEpochDay: Long): Flow<List<ClimbEntity>>

    @Query("SELECT COUNT(*) FROM climbs")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM climbs")
    suspend fun count(): Int

    @Query("SELECT * FROM climbs ORDER BY dateEpochDay, createdAtMillis")
    suspend fun getAll(): List<ClimbEntity>

    /**
     * Hardest send per discipline, scale and style. Grades in different scales are never
     * compared, so each scale keeps its own bests; local grades also per place. SQLite returns the other columns from the
     * row that holds the MAX; ties go to the earliest day it was sent.
     */
    @Query(
        """
        SELECT discipline, style, gradeScale, MAX(gradeIndex) AS gradeIndex, name, place, dateEpochDay,
            placeId, gradeLabel, gradeColour
        FROM (SELECT * FROM climbs WHERE style != 'ATTEMPT' ORDER BY dateEpochDay ASC, createdAtMillis ASC)
        GROUP BY discipline, gradeScale, style, CASE WHEN gradeScale LIKE 'LOCAL%' THEN placeId ELSE 0 END
        """,
    )
    fun observePersonalBests(): Flow<List<PersonalBestRow>>

    @Query("SELECT * FROM climbs WHERE id = :id")
    suspend fun get(id: Long): ClimbEntity?

    @Query("SELECT COUNT(*) FROM climbs WHERE problemId = :problemId")
    suspend fun countForProblem(problemId: Long): Int

    @Query("SELECT * FROM climbs WHERE problemId = :problemId ORDER BY dateEpochDay DESC, createdAtMillis DESC")
    fun observeForProblem(problemId: Long): Flow<List<ClimbEntity>>

    @Query("SELECT * FROM climbs WHERE placeId = :placeId ORDER BY dateEpochDay DESC, createdAtMillis DESC")
    fun observeAtPlace(placeId: Long): Flow<List<ClimbEntity>>

    @Insert
    suspend fun insert(climb: ClimbEntity): Long

    @androidx.room.Update
    suspend fun update(climb: ClimbEntity)

    @Query("DELETE FROM climbs WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertAll(climbs: List<ClimbEntity>)
}
