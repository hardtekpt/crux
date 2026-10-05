package com.hardtekpt.crux.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClimbDaoTest {

    private lateinit var db: CruxDatabase
    private lateinit var dao: ClimbDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CruxDatabase::class.java,
        ).build()
        dao = db.climbDao()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun insertedClimbsAreReturnedNewestFirst() = runTest {
        dao.insert(ClimbEntity(name = "Slab", grade = "V1", loggedAtEpochMillis = 1_000))
        dao.insert(ClimbEntity(name = "Roof", grade = "V5", loggedAtEpochMillis = 2_000))

        val climbs = dao.observeAll().first()

        assertEquals(listOf("Roof", "Slab"), climbs.map { it.name })
        assertEquals(2, dao.observeCount().first())
    }
}
