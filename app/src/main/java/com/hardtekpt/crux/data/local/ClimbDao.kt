package com.hardtekpt.crux.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClimbDao {
    @Query("SELECT * FROM climbs ORDER BY loggedAtEpochMillis DESC")
    fun observeAll(): Flow<List<ClimbEntity>>

    @Query("SELECT COUNT(*) FROM climbs")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(climb: ClimbEntity): Long
}
