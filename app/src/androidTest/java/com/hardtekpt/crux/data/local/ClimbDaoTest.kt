package com.hardtekpt.crux.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.MeasurementType
import com.hardtekpt.crux.data.model.Venue
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

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CruxDatabase::class.java,
        ).build()
    }

    @After
    fun closeDb() = db.close()

    @Test
    fun climbsComeBackNewestDayFirst() = runTest {
        val dao = db.climbDao()
        dao.insert(climb("Slab", day = 10, created = 1))
        dao.insert(climb("Roof", day = 12, created = 2))
        dao.insert(climb("Arete", day = 12, created = 3))

        assertEquals(listOf("Arete", "Roof", "Slab"), dao.observeAll().first().map { it.name })
        assertEquals(2, dao.observeSince(11).first().size)
        assertEquals(3, dao.observeCount().first())
    }

    @Test
    fun personalBestsAreTheHardestSendPerDisciplineAndStyle() = runTest {
        val dao = db.climbDao()
        dao.insert(climb("Easy flash", gradeIndex = 6, style = AscentStyle.FLASH))
        dao.insert(climb("Hard flash", gradeIndex = 9, style = AscentStyle.FLASH))
        dao.insert(climb("Project", gradeIndex = 14, style = AscentStyle.ATTEMPT))
        dao.insert(climb("Redpoint", gradeIndex = 11, style = AscentStyle.REDPOINT))

        val bests = dao.observePersonalBests().first().associateBy { it.style }

        assertEquals(setOf(AscentStyle.FLASH, AscentStyle.REDPOINT), bests.keys)
        assertEquals("Hard flash", bests.getValue(AscentStyle.FLASH).name)
        assertEquals(11, bests.getValue(AscentStyle.REDPOINT).gradeIndex)
    }

    @Test
    fun personalBestsNeverCompareAcrossScales() = runTest {
        val dao = db.climbDao()
        dao.insert(climb("Font send", gradeIndex = 11, scale = GradeScale.FONT))
        dao.insert(climb("V send", gradeIndex = 15, scale = GradeScale.V_SCALE))

        val bests = dao.observePersonalBests().first().associateBy { it.gradeScale }

        assertEquals("Font send", bests.getValue(GradeScale.FONT).name)
        assertEquals("V send", bests.getValue(GradeScale.V_SCALE).name)
    }

    @Test
    fun measurementsAreFilteredByType() = runTest {
        val dao = db.bodyMeasurementDao()
        dao.insert(BodyMeasurementEntity(type = MeasurementType.WEIGHT, value = 72.4, dateEpochDay = 2, createdAtMillis = 0))
        dao.insert(BodyMeasurementEntity(type = MeasurementType.HEIGHT, value = 178.0, dateEpochDay = 1, createdAtMillis = 0))

        assertEquals(listOf(72.4), dao.observe(MeasurementType.WEIGHT).first().map { it.value })
    }

    private fun climb(
        name: String,
        gradeIndex: Int = 5,
        style: AscentStyle = AscentStyle.FLASH,
        day: Long = 1,
        created: Long = 0,
        scale: GradeScale = GradeScale.FONT,
    ) = ClimbEntity(
        discipline = Discipline.BOULDER,
        gradeScale = scale,
        gradeIndex = gradeIndex,
        style = style,
        attempts = 1,
        venue = Venue.GYM,
        dateEpochDay = day,
        createdAtMillis = created,
        name = name,
    )
}
