package com.hardtekpt.crux.data.backup

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardtekpt.crux.data.FIXED_CLOCK
import com.hardtekpt.crux.data.images.AreaImageStore
import com.hardtekpt.crux.data.local.ClimbMediaEntity
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.MediaKind
import com.hardtekpt.crux.data.seed.StarterDataSeeder
import java.io.ByteArrayOutputStream
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
        source.exerciseRecordDao().insert(
            com.hardtekpt.crux.data.ExerciseRecordEntity(exerciseId = exercise.id, dateEpochDay = 20_000, reps = 5, loadKg = 12.5, createdAtMillis = 2),
        )
        val text = BackupRepository(source, FIXED_CLOCK).export(BackupSection.entries.toSet())

        val target = newDb()
        val restore = BackupRepository(target, FIXED_CLOCK)
        val result = restore.import(restore.parse(text), BackupSection.entries.toSet())

        assertEquals(source.exerciseDao().count(), target.exerciseDao().count())
        assertEquals(source.templateDao().count(), target.templateDao().count())
        assertEquals(source.climbDao().count(), target.climbDao().count())
        assertEquals(source.bodyMeasurementDao().count(), target.bodyMeasurementDao().count())
        assertEquals(source.climbDao().count(), result.added[BackupSection.JOURNAL])
        assertEquals(source.noteDao().getAll().size, target.noteDao().getAll().size)
        assert(target.noteDao().getAll().any { it.text == "Left finger tweak, easy on crimps" })
        assertEquals(source.exerciseRecordDao().getAll().size, target.exerciseRecordDao().getAll().size)
        val record = target.exerciseRecordDao().getAll().single { it.createdAtMillis == 2L }
        assertEquals(12.5, record.loadKg!!, 0.0)
        assertEquals(exercise.name, target.exerciseDao().getAll().first { it.id == record.exerciseId }.name)
        // Interval exercises bring their timer.
        val repeaters = target.exerciseDao().getAll().first { it.name == "Repeaters" }
        val original = source.exerciseDao().getAll().first { it.name == "Repeaters" }
        assertEquals(original.defaults, repeaters.defaults)
        assertEquals(original.prepSeconds, repeaters.prepSeconds)
        assertEquals(7, repeaters.defaults!!.seconds)

        // Places come back with their walls and problems, and climbs link to them again.
        assertEquals(source.placeDao().getPlaces().size, target.placeDao().getPlaces().size)
        assertEquals(source.placeDao().getAllAreas().size, target.placeDao().getAllAreas().size)
        // A gym with a board keeps both kinds, and its board set stays a board area.
        val blockLab = target.placeDao().getPlaces().first { it.name == "Block Lab" }
        assertEquals("BOARD", blockLab.extraTypes)
        val sections = target.placeDao().getSections(blockLab.id)
        assertEquals(listOf("Main gym", "Kilter board"), sections.map { it.name })
        assertEquals(sections[1].id, target.placeDao().getAllAreas().first { it.name == "Benchmarks" }.sectionId)
        assertEquals(source.placeDao().getAllProblems().size, target.placeDao().getAllProblems().size)
        val linkedSource = source.climbDao().getAll().count { it.problemId != null && it.areaId != null && it.placeId != null }
        val linkedTarget = target.climbDao().getAll().count { it.problemId != null && it.areaId != null && it.placeId != null }
        assertTrue(linkedSource > 0)
        assertEquals(linkedSource, linkedTarget)
    }

    /**
     * A file exported by the released v0.1.0 (sample data, a pinned note and a record), kept in
     * test resources. Backups people already made must keep importing as the app changes.
     */
    @Test
    fun `a backup made by the first release still imports in full`() = runTest {
        val text = javaClass.getResource("/backups/backup-v0.1.0.json")!!.readText()
        val db = newDb()
        val repo = BackupRepository(db, FIXED_CLOCK)
        val file = repo.parse(text)

        repo.import(file, BackupSection.entries.toSet())

        assertEquals(file.exercises!!.size, db.exerciseDao().count())
        assertEquals(file.plans!!.size, db.templateDao().count())
        assertEquals(file.climbs!!.size, db.climbDao().count())
        assertEquals(file.bodyMeasurements!!.size, db.bodyMeasurementDao().count())
        assertEquals(file.records!!.size, db.exerciseRecordDao().getAll().size)
        assertEquals(file.notes!!.size, db.noteDao().getAll().size)
        assertTrue(db.noteDao().getAll().single { it.text == "Left finger tweak, easy on crimps" }.pinned)
        assertEquals(12.5, db.exerciseRecordDao().getAll().single { it.createdAtMillis == 2L }.loadKg!!, 0.0)

        // 0.1.0 had one kind per place; each becomes a place with one section of that kind.
        val places = db.placeDao().getPlaces()
        assertEquals(file.places!!.size, places.size)
        places.forEach { place -> assertEquals(listOf(place.type), db.placeDao().getSections(place.id).map { it.type }) }
        val linked = db.climbDao().getAll().count { it.problemId != null && it.sectionId != null }
        assertEquals(file.climbs!!.count { it.problem != null }, linked)
    }

    @Test
    fun `climb photos and wall images travel inside the backup`() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val images = AreaImageStore(context)
        val jpeg = ByteArrayOutputStream().also { out ->
            Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, out)
        }.toByteArray()
        images.file("climb.jpg").writeBytes(jpeg)
        images.file("wall.jpg").writeBytes(jpeg)

        val source = newDb()
        StarterDataSeeder(FIXED_CLOCK).seed(source, includeSampleData = true)
        val climb = source.climbDao().getAll().first()
        source.climbMediaDao().insert(ClimbMediaEntity(climbId = climb.id, kind = MediaKind.IMAGE, path = "climb.jpg", createdAtMillis = 1))
        val wall = source.placeDao().getAllAreas().first()
        source.placeDao().updateArea(wall.copy(imagePath = "wall.jpg"))
        val text = BackupRepository({ source }, FIXED_CLOCK, images).export(BackupSection.entries.toSet())

        val target = newDb()
        val restore = BackupRepository({ target }, FIXED_CLOCK, images)
        restore.import(restore.parse(text), BackupSection.entries.toSet())

        val photo = target.climbMediaDao().getAll().single { it.kind == MediaKind.IMAGE }
        assertTrue(images.file(photo.path).readBytes().contentEquals(jpeg))
        val wallImage = target.placeDao().getAllAreas().single { it.name == wall.name }.imagePath!!
        assertTrue(images.file(wallImage).readBytes().contentEquals(jpeg))
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
