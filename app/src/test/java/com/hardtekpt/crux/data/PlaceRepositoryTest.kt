package com.hardtekpt.crux.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.DatabaseFactory
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.TestScope
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import com.hardtekpt.crux.data.seed.StarterDataSeeder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Problem stats and projects come from SQL over the journal; checked against the sample data. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlaceRepositoryTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CruxDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @get:Rule val tmp = TemporaryFolder()

    @After
    fun close() = db.close()

    /** Real mode (the default) reads and writes this one in-memory database. */
    private fun TestScope.databases() = CruxDatabases(
        UserPreferencesRepository(PreferenceDataStoreFactory.create { File(tmp.root, "prefs.preferences_pb") }),
        DatabaseFactory { db },
        backgroundScope,
    )

    private suspend fun TestScope.repository(): OfflinePlaceRepository {
        StarterDataSeeder(FIXED_CLOCK).seed(db, includeSampleData = true)
        return OfflinePlaceRepository(databases(), FIXED_CLOCK)
    }

    @Test
    fun `projects are problems tried but never sent`() = runTest {
        val projects = repository().observeProjects().first()

        assertEquals(setOf("Overhang project", "Black roof"), projects.map { it.problem.name }.toSet())
        val overhang = projects.single { it.problem.name == "Overhang project" }
        assertEquals(8, overhang.stats.attempts)
        assertEquals("The Arch", overhang.placeName)
        assertEquals("Overhang", overhang.areaName)
    }

    @Test
    fun `a problem sent on a later session counts every go`() = runTest {
        val repo = repository()
        val dyno = db.placeDao().getAllProblems().single { it.name == "Yellow dyno" }
        val stats = repo.observeProblem(dyno.id).first()!!.stats!!

        assertEquals(2, stats.sessions)
        assertEquals(11, stats.attempts)
        assertTrue(stats.sent)
    }

    @Test
    fun `resetting a wall retires its problems and deleting a place keeps the climbs`() = runTest {
        val repo = repository()
        val cave = db.placeDao().getAllAreas().single { it.name == "Cave" }
        repo.resetArea(cave.id)
        assertTrue(db.placeDao().getAllProblems().filter { it.areaId == cave.id }.all { it.retired })

        val climbsBefore = db.climbDao().count()
        repo.deletePlace(cave.placeId)
        assertEquals(climbsBefore, db.climbDao().count())
        assertTrue(db.climbDao().getAll().none { it.placeId == cave.placeId })
        assertNull(repo.getPlace(cave.placeId))
    }

    @Test
    fun `local bests stay apart per place and from standard scales`() = runTest {
        val climbs = OfflineClimbRepository(databases(), FIXED_CLOCK)
        val day = java.time.LocalDate.now(FIXED_CLOCK)
        fun local(placeId: Long, index: Int, label: String) = com.hardtekpt.crux.data.model.NewClimb(
            com.hardtekpt.crux.data.model.Discipline.BOULDER, com.hardtekpt.crux.data.model.GradeScale.LOCAL_BOULDER, index,
            com.hardtekpt.crux.data.model.AscentStyle.FLASH, 1, com.hardtekpt.crux.data.model.Venue.GYM, day, null, "Gym $placeId", null,
            placeId = placeId, gradeLabel = label,
        )
        climbs.logClimb(local(100, 4, "Red"))
        climbs.logClimb(local(200, 2, "7"))

        val localBests = climbs.observePersonalBests().first().filter { it.gradeScale.isLocal }
        assertEquals(setOf("Red", "7"), localBests.map { it.grade }.toSet())
        assertEquals(setOf(100L, 200L), localBests.map { it.placeId }.toSet())
    }
}
