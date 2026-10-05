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

    /**
     * Hardest send per discipline and style. SQLite returns the other columns from the
     * row that holds the MAX; ties go to the earliest day it was sent.
     */
    @Query(
        """
        SELECT discipline, style, gradeScale, MAX(gradeIndex) AS gradeIndex, name, place, dateEpochDay
        FROM (SELECT * FROM climbs WHERE style != 'ATTEMPT' ORDER BY dateEpochDay ASC, createdAtMillis ASC)
        GROUP BY discipline, style
        """,
    )
    fun observePersonalBests(): Flow<List<PersonalBestRow>>

    @Insert
    suspend fun insert(climb: ClimbEntity): Long

    @Insert
    suspend fun insertAll(climbs: List<ClimbEntity>)
}
