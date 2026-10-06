package com.hardtekpt.crux.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardtekpt.crux.data.FIXED_CLOCK
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.seed.StarterDataSeeder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Round-trips real Room databases through the backup file, on the JVM via Robolectric. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupRepositoryTest {

    private val databases = mutableListOf<CruxDatabase>()

    private fun newDb() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CruxDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { databases += it }

    @After
    fun close() = databases.forEach { it.close() }

    @Test
    fun `export then import restores every section`() = runTest {
        val source = newDb()
        StarterDataSeeder(FIXED_CLOCK).seed(source, includeSampleData = true)
        source.noteDao().insert(com.hardtekpt.crux.data.NoteEntity(text = "Left finger tweak, easy on crimps", createdAtMillis = 1, updatedAtMillis = 1))
        val exercise = source.exerciseDao().getAll().first()
        source.exerciseRecordDao().insert(com.hardtekpt.crux.data.ExerciseRecordEntity(exerciseId = exercise.id, dateEpochDay = 20_000, reps = 5, loadKg = 12.5, createdAtMillis = 2))
        val text = BackupRepository(source, FIXED_CLOCK).export(BackupSection.entries.toSet())

        val target = newDb()
        val restore = BackupRepository(target, FIXED_CLOCK)
        val result = restore.import(restore.parse(text), BackupSection.entries.toSet())

        assertEquals(source.exerciseDao().count(), target.exerciseDao().count())
        assertEquals(source.templateDao().count(), target.templateDao().count())
        assertEquals(source.climbDao().count(), target.climbDao().count())
        assertEquals(source.bodyMeasurementDao().count(), target.bodyMeasurementDao().count())
        assertEquals(source.climbDao().count(), result.added[BackupSection.JOURNAL])
        assertEquals("Left finger tweak, easy on crimps", target.noteDao().getAll().single().text)
        val record = target.exerciseRecordDao().getAll().single()
        assertEquals(12.5, record.loadKg!!, 0.0)
        assertEquals(exercise.name, target.exerciseDao().getAll().first { it.id == record.exerciseId }.name)

        // Places come back with their walls and problems, and climbs link to them again.
        assertEquals(source.placeDao().getPlaces().size, target.placeDao().getPlaces().size)
        assertEquals(source.placeDao().getAllAreas().size, target.placeDao().getAllAreas().size)
        assertEquals(source.placeDao().getAllProblems().size, target.placeDao().getAllProblems().size)
        val linkedSource = source.climbDao().getAll().count { it.problemId != null && it.areaId != null && it.placeId != null }
        val linkedTarget = target.climbDao().getAll().count { it.problemId != null && it.areaId != null && it.placeId != null }
        assertTrue(linkedSource > 0)
        assertEquals(linkedSource, linkedTarget)
    }

    @Test
    fun `importing the same backup twice adds nothing the second time`() = runTest {
        val db = newDb()
        StarterDataSeeder(FIXED_CLOCK).seed(db, includeSampleData = true)
        val repo = BackupRepository(db, FIXED_CLOCK)
        val file = repo.parse(repo.export(BackupSection.entries.toSet()))

        val result = repo.import(file, BackupSection.entries.toSet())

        assertTrue(result.added.values.all { it == 0 })
        assertEquals(file.climbs!!.size, result.skipped[BackupSection.JOURNAL])
    }

    @Test
    fun `sections switched off are left out of the file`() = runTest {
        val db = newDb()
        StarterDataSeeder(FIXED_CLOCK).seed(db, includeSampleData = true)
        val repo = BackupRepository(db, FIXED_CLOCK)

        val file = repo.parse(repo.export(setOf(BackupSection.PLANS)))

        assertNull(file.climbs)
        assertNull(file.exercises)
        assertEquals(3, file.plans!!.size)
    }

    @Test
    fun `plans bring their exercises even without the library`() = runTest {
        val source = newDb()
        StarterDataSeeder(FIXED_CLOCK).seed(source, includeSampleData = false)
        val text = BackupRepository(source, FIXED_CLOCK).export(setOf(BackupSection.PLANS))

        val target = newDb()
        val repo = BackupRepository(target, FIXED_CLOCK)
        repo.import(repo.parse(text), setOf(BackupSection.PLANS))

        assertEquals(3, target.templateDao().count())
        assertTrue(target.exerciseDao().count() > 0)
    }

    @Test
    fun `files that are not backups are refused with a reason`() {
        val repo = BackupRepository(newDb(), FIXED_CLOCK)
        assertThrows(BackupFormatException::class.java) { repo.parse("not json") }
        assertThrows(BackupFormatException::class.java) { repo.parse("""{"format":"other","exportedAt":"x"}""") }
        assertThrows(BackupFormatException::class.java) {
            repo.parse("""{"format":"crux-backup","version":99,"exportedAt":"x"}""")
        }
    }
}
