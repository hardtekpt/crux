package com.hardtekpt.crux.data

import com.hardtekpt.crux.data.local.ClimbDao
import com.hardtekpt.crux.data.local.ClimbEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface ClimbRepository {
    fun observeClimbCount(): Flow<Int>
    fun observeClimbCountSince(sinceEpochMillis: Long): Flow<Int>
    suspend fun logClimb(name: String, grade: String, notes: String? = null)
}

class OfflineClimbRepository @Inject constructor(
    private val climbDao: ClimbDao,
) : ClimbRepository {
    override fun observeClimbCount(): Flow<Int> = climbDao.observeCount()

    override fun observeClimbCountSince(sinceEpochMillis: Long): Flow<Int> =
        climbDao.observeCountSince(sinceEpochMillis)

    override suspend fun logClimb(name: String, grade: String, notes: String?) {
        climbDao.insert(
            ClimbEntity(
                name = name,
                grade = grade,
                loggedAtEpochMillis = System.currentTimeMillis(),
                notes = notes,
            ),
        )
    }
}
