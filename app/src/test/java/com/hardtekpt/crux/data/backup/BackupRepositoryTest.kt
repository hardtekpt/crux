package com.hardtekpt.crux.data.backup

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardtekpt.crux.MainDispatcherRule
import com.hardtekpt.crux.data.ExerciseRecordEntity
import com.hardtekpt.crux.data.FIXED_CLOCK
import com.hardtekpt.crux.data.NoteEntity
import com.hardtekpt.crux.data.dashboard.DashboardRepository
import com.hardtekpt.crux.data.dashboard.DashboardWidget
import com.hardtekpt.crux.data.dashboard.WidgetSize
import com.hardtekpt.crux.data.dashboard.WidgetType
import com.hardtekpt.crux.data.images.AreaImageStore
import com.hardtekpt.crux.data.local.ClimbMediaEntity
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.MediaKind
import com.hardtekpt.crux.data.local.PlaceEntity
import com.hardtekpt.crux.data.local.SessionEntity
import com.hardtekpt.crux.data.local.SessionItemEntity
import com.hardtekpt.crux.data.local.SessionSetEntity
import com.hardtekpt.crux.data.local.SessionStatus
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.prefs.ThemeMode
import com.hardtekpt.crux.data.prefs.UnitSystem
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.data.seed.StarterDataSeeder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Round-trips real Room databases through backup archives, on the JVM via Robolectric. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupRepositoryTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @get:Rule val tmp = TemporaryFolder()

    private val databases = mutableListOf<CruxDatabase>()
    private val images by lazy { AreaImageStore(ApplicationProvider.getApplicationContext()) }

    private fun newDb() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CruxDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { databases += it }

    private fun repo(db: CruxDatabase, settings: BackupSettings? = null) = BackupRepository({ db }, FIXED_CLOCK, images, settings)

    private suspend fun seeded() = newDb().also { StarterDataSeeder(FIXED_CLOCK).seed(it, includeSampleData = true) }

    private suspend fun BackupRepository.exportBytes(sections: Set<BackupSection> = BackupSection.entries.toSet(), media: BackupMedia = BackupMedia()) =
        ByteArrayOutputStream().also { export(sections, it, media) }.toByteArray()

    private suspend fun BackupRepository.openBytes(bytes: ByteArray, media: BackupMedia = BackupMedia()) = open(ByteArrayInputStream(bytes), media)

    private fun entries(bytes: ByteArray) = ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> generateSequence { zip.nextEntry?.name }.toList() }

    private val jpeg by lazy {
        ByteArrayOutputStream().also { Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    /** A finished session at Block Lab with one exercise, a set done and one skipped, and a climb logged in it. */
    private suspend fun addSession(db: CruxDatabase): Long {
        val dao = db.sessionDao()
        val place = db.placeDao().getPlaces().first { it.name == "Block Lab" }
        val id = dao.insertSession(
            SessionEntity(
                name = "Strength day",
                templateId = db.templateDao().getAll().first { it.template.name == "Strength day" }.template.id,
                placeId = place.id,
                sectionId = db.placeDao().getSections(place.id).first().id,
                startedAtMillis = 1_000_000,
                endedAtMillis = 4_600_000,
                status = SessionStatus.FINISHED,
                effort = 7,
                notes = "Felt strong",
            ),
        )
        val itemId = dao.insertItem(
            SessionItemEntity(
                sessionId = id,
                exerciseId = db.exerciseDao().getAll().first().id,
                blockName = "Main",
                position = 0,
                sets = 3,
                reps = 5,
                seconds = 0,
                loadKg = 10.0,
                restSeconds = 180,
            ),
        )
        dao.insertSet(SessionSetEntity(itemId = itemId, setIndex = 0, reps = 5, loadKg = 10.0, completedAtMillis = 1_100_000))
        dao.insertSet(SessionSetEntity(itemId = itemId, setIndex = 1, skipped = true, completedAtMillis = 1_200_000))
        val climb = db.climbDao().getAll().first()
        db.climbDao().update(climb.copy(sessionId = id))
        return id
    }

    @After
    fun close() = databases.forEach { it.close() }

    @Test
    fun `export then import restores every section`() = runTest {
        val source = seeded()
        source.noteDao().insert(NoteEntity(text = "Left finger tweak, easy on crimps", createdAtMillis = 1, updatedAtMillis = 1))
        val exercise = source.exerciseDao().getAll().first()
        source.exerciseRecordDao().insert(ExerciseRecordEntity(exerciseId = exercise.id, dateEpochDay = 20_000, reps = 5, loadKg = 12.5, createdAtMillis = 2))
        // A board graded apart from the gym it's in.
        val kilterSection = source.placeDao().getAllSections().first { it.name == "Kilter board" }
        source.placeDao().updateSection(kilterSection.copy(boulderScale = GradeScale.V_SCALE))
        addSession(source)
        val bytes = repo(source).exportBytes()

        val target = newDb()
        val restore = repo(target)
        val result = restore.import(restore.openBytes(bytes), BackupSection.entries.toSet())

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
        assertEquals(original.createdAtMillis, repeaters.createdAtMillis)

        // Places come back with their walls and problems, and climbs link to them again.
        assertEquals(source.placeDao().getPlaces().size, target.placeDao().getPlaces().size)
        assertEquals(source.placeDao().getAllAreas().size, target.placeDao().getAllAreas().size)
        // A gym with a board keeps both kinds, and its board set stays a board area.
        val blockLab = target.placeDao().getPlaces().first { it.name == "Block Lab" }
        assertEquals("BOARD", blockLab.extraTypes)
        val sections = target.placeDao().getSections(blockLab.id)
        assertEquals(listOf("Main gym", "Kilter board"), sections.map { it.name })
        assertEquals(listOf(null, GradeScale.V_SCALE), sections.map { it.boulderScale })
        assertEquals(sections[1].id, target.placeDao().getAllAreas().first { it.name == "Benchmarks" }.sectionId)
        assertEquals(source.placeDao().getAllProblems().size, target.placeDao().getAllProblems().size)
        val linkedSource = source.climbDao().getAll().count { it.problemId != null && it.areaId != null && it.placeId != null }
        val linkedTarget = target.climbDao().getAll().count { it.problemId != null && it.areaId != null && it.placeId != null }
        assertTrue(linkedSource > 0)
        assertEquals(linkedSource, linkedTarget)

        // The session comes back with its sets, its plan, its place, and the climb logged in it.
        val session = target.sessionDao().getFinished().single()
        assertEquals("Felt strong", session.notes)
        assertEquals(7, session.effort)
        assertEquals(4_600_000L, session.endedAtMillis)
        assertEquals(target.templateDao().getAll().first { it.template.name == "Strength day" }.template.id, session.templateId)
        assertEquals(blockLab.id, session.placeId)
        assertEquals(sections[0].id, session.sectionId)
        val item = target.sessionDao().getAllItems().single()
        assertEquals(exercise.name, target.exerciseDao().get(item.exerciseId)!!.name)
        assertEquals(listOf(false, true), target.sessionDao().getAllSets().map { it.skipped })
        assertEquals(1, target.climbDao().getAll().count { it.sessionId == session.id })
    }

    /**
     * A file exported by the released v0.1.0 (sample data, a pinned note and a record), kept in
     * test resources. Backups people already made must keep importing as the app changes.
     */
    @Test
    fun `a backup made by the first release still imports in full`() = runTest {
        val db = newDb()
        val repo = repo(db)
        val backup = repo.open(javaClass.getResourceAsStream("/backups/backup-v0.1.0.json")!!)
        val file = backup.file

        repo.import(backup, BackupSection.entries.toSet())

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

    /** A JSON file from v0.2.0, the last of format version 1: photos inside as base64, sections graded apart. */
    @Test
    fun `a backup made by 0_2_0 still imports in full, photos included`() = runTest {
        val db = newDb()
        val repo = repo(db)
        val backup = repo.open(javaClass.getResourceAsStream("/backups/backup-v0.2.0.json")!!)
        val file = backup.file
        assertEquals(1, file.version)

        repo.import(backup, BackupSection.entries.toSet())

        assertEquals(file.climbs!!.size, db.climbDao().count())
        assertEquals(file.places!!.sumOf { it.problems.size }, db.placeDao().getAllProblems().size)
        assertEquals(GradeScale.V_SCALE, db.placeDao().getAllSections().single { it.name == "Kilter board" }.boulderScale)
        assertEquals("injury", db.noteDao().getAll().single { it.text.startsWith("Left finger") }.tag)
        val photo = db.climbMediaDao().getAll().single { it.kind == MediaKind.IMAGE }
        assertTrue(images.file(photo.path).readBytes().isNotEmpty())
        assertEquals(1, db.placeDao().getAllAreas().count { it.imagePath != null })
    }

    @Test
    fun `photos and videos travel inside the archive`() = runTest {
        images.file("climb.jpg").writeBytes(jpeg)
        images.file("wall.jpg").writeBytes(jpeg)
        val video = byteArrayOf(0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
        images.file("clip.mp4").writeBytes(video)
        val source = seeded()
        val climb = source.climbDao().getAll().first()
        source.climbMediaDao().insert(ClimbMediaEntity(climbId = climb.id, kind = MediaKind.IMAGE, path = "climb.jpg", createdAtMillis = 1))
        source.climbMediaDao().insert(ClimbMediaEntity(climbId = climb.id, kind = MediaKind.VIDEO, path = "clip.mp4", createdAtMillis = 1))
        val wall = source.placeDao().getAllAreas().first()
        source.placeDao().updateArea(wall.copy(imagePath = "wall.jpg"))
        val bytes = repo(source).exportBytes()
        assertTrue(entries(bytes).containsAll(listOf("backup.json", "media/climb.jpg", "media/clip.mp4", "media/wall.jpg")))

        val target = newDb()
        val restore = repo(target)
        restore.openBytes(bytes).use { restore.import(it, BackupSection.entries.toSet()) }

        val media = target.climbMediaDao().getAll()
        assertTrue(images.file(media.single { it.kind == MediaKind.IMAGE }.path).readBytes().contentEquals(jpeg))
        assertTrue(images.file(media.single { it.kind == MediaKind.VIDEO }.path).readBytes().contentEquals(video))
        val wallImage = target.placeDao().getAllAreas().single { it.name == wall.name }.imagePath!!
        assertTrue(images.file(wallImage).readBytes().contentEquals(jpeg))
        // The unpacked archive is cleared away once the backup is closed.
        val staging = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, AreaImageStore.STAGING_DIR)
        assertTrue(staging.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `photos and videos switched off stay out of the archive and out of the import`() = runTest {
        images.file("climb.jpg").writeBytes(jpeg)
        images.file("clip.mp4").writeBytes(byteArrayOf(1, 2, 3))
        val source = seeded()
        val climb = source.climbDao().getAll().first()
        source.climbMediaDao().insert(ClimbMediaEntity(climbId = climb.id, kind = MediaKind.IMAGE, path = "climb.jpg", createdAtMillis = 1))
        source.climbMediaDao().insert(ClimbMediaEntity(climbId = climb.id, kind = MediaKind.VIDEO, path = "clip.mp4", createdAtMillis = 1))
        val repo = repo(source)

        assertEquals(listOf("backup.json", "media/climb.jpg"), entries(repo.exportBytes(media = BackupMedia(photos = true, videos = false))))
        assertEquals(listOf("backup.json"), entries(repo.exportBytes(media = BackupMedia.NONE)))

        // An archive with both, imported with photos off, brings only the video.
        val target = newDb()
        val restore = repo(target)
        val photosOff = BackupMedia(photos = false, videos = true)
        restore.openBytes(repo.exportBytes(), photosOff).use { restore.import(it, BackupSection.entries.toSet(), media = photosOff) }
        assertEquals(listOf(MediaKind.VIDEO), target.climbMediaDao().getAll().map { it.kind })
    }

    @Test
    fun `importing the same backup twice adds nothing when every duplicate is skipped`() = runTest {
        val db = seeded()
        addSession(db)
        val repo = repo(db)
        val backup = repo.openBytes(repo.exportBytes())
        val conflicts = repo.conflicts(backup, BackupSection.entries.toSet())

        val result = repo.import(backup, BackupSection.entries.toSet(), conflicts.associate { it.id to Resolution.SKIP })

        assertTrue(result.added.values.all { it == 0 })
        assertEquals(backup.file.climbs!!.size, result.skipped[BackupSection.JOURNAL])
        // Every record is asked about once, and each is the very same as what's here.
        val records = BackupSection.entries.filter { it != BackupSection.SETTINGS }.sumOf { backup.file.count(it) ?: 0 }
        assertEquals(records, conflicts.size)
        assertTrue(conflicts.filterNot { it.identical }.toString(), conflicts.all { it.identical })
    }

    @Test
    fun `a duplicate can be skipped, replaced or kept as a copy`() = runTest {
        val db = seeded()
        val repo = repo(db)
        db.noteDao().insert(NoteEntity(text = "Beta for the dyno", createdAtMillis = 5, updatedAtMillis = 5))
        val backup = repo.openBytes(repo.exportBytes())
        // What changed here since the backup.
        val note = db.noteDao().getAll().single { it.createdAtMillis == 5L }
        db.noteDao().update(note.copy(text = "Edited later"))
        val climb = db.climbDao().getAll().first()
        db.climbDao().update(climb.copy(notes = "Changed here"))
        val exercise = db.exerciseDao().getAll().first()
        val climbsBefore = db.climbDao().count()

        val noteKey = ConflictKey(BackupSection.NOTES, "5")
        val noteConflict = repo.conflicts(backup, BackupSection.entries.toSet()).single { it.id == noteKey }
        assertFalse(noteConflict.identical)
        assertEquals("Beta for the dyno", noteConflict.title)
        val decisions = mapOf(
            noteKey to Resolution.REPLACE,
            ConflictKey(BackupSection.JOURNAL, climb.createdAtMillis.toString()) to Resolution.KEEP_BOTH,
            ConflictKey(BackupSection.EXERCISES, exercise.name.lowercase()) to Resolution.KEEP_BOTH,
        )
        val result = repo.import(backup, BackupSection.entries.toSet(), decisions)

        assertEquals("Beta for the dyno", db.noteDao().getAll().single { it.createdAtMillis == 5L }.text)
        assertEquals(climbsBefore + 1, db.climbDao().count())
        assertEquals(listOf("Changed here", climb.notes), db.climbDao().getAll().filter { it.createdAtMillis == climb.createdAtMillis }.map { it.notes })
        assertTrue(db.exerciseDao().getAll().any { it.name == "${exercise.name} (2)" })
        assertEquals(1, result.replaced[BackupSection.NOTES])
        assertEquals(1, result.copied[BackupSection.JOURNAL])
        // Everything not decided is skipped.
        assertEquals(backup.file.climbs!!.size - 1, result.skipped[BackupSection.JOURNAL])
    }

    @Test
    fun `replacing a place that is already here brings its walls and problems`() = runTest {
        val bytes = repo(seeded()).exportBytes(setOf(BackupSection.PLACES))
        // A new phone where the climber already added their gym, bare.
        val target = newDb()
        val placeId = target.placeDao().insertPlace(PlaceEntity(name = "Block Lab", type = PlaceType.GYM, createdAtMillis = 0))
        val restore = repo(target)
        val backup = restore.openBytes(bytes)
        val conflict = restore.conflicts(backup, setOf(BackupSection.PLACES)).single()
        assertEquals("Block Lab", conflict.title)

        restore.import(backup, setOf(BackupSection.PLACES), mapOf(conflict.id to Resolution.REPLACE))

        val blockLab = target.placeDao().getPlaces().filter { it.name == "Block Lab" }
        assertEquals(listOf(placeId), blockLab.map { it.id })
        assertEquals("BOARD", blockLab.single().extraTypes)
        val source = backup.file.places!!.single { it.name == "Block Lab" }
        assertEquals(source.areas.map { it.name }.sorted(), target.placeDao().getAllAreas().filter { it.placeId == placeId }.map { it.name }.sorted())
        assertEquals(source.problems.size, target.placeDao().getAllProblems().count { it.placeId == placeId })
    }

    @Test
    fun `a renamed place doesn't make its climbs look new`() = runTest {
        val db = seeded()
        val place = db.placeDao().getPlaces().first { it.name == "Block Lab" }
        db.placeDao().updatePlace(place.copy(name = "Block Lab North"))
        val repo = repo(db)
        val backup = repo.openBytes(repo.exportBytes(setOf(BackupSection.JOURNAL)))

        val result = repo.import(backup, setOf(BackupSection.JOURNAL))

        assertNull(result.added[BackupSection.JOURNAL])
        // The name typed when it was logged survives a restore.
        val target = newDb()
        repo(target).import(backup, setOf(BackupSection.JOURNAL))
        assertTrue(target.climbDao().getAll().any { it.place == "Block Lab" })
    }

    @Test
    fun `settings travel with the backup`() = runTest(mainDispatcherRule.testDispatcher) {
        class Settings(name: String) {
            val store = mainDispatcherRule.preferencesDataStore(File(tmp.root, "$name.preferences_pb"))
            val preferences = UserPreferencesRepository(store)
            val dashboard = DashboardRepository(store)
            val backup = BackupSettings(preferences, dashboard)
        }
        val source = Settings("source")
        source.preferences.setThemeMode(ThemeMode.LIGHT)
        source.preferences.setUnits(UnitSystem.IMPERIAL)
        source.preferences.setGradeScale(GradeScale.V_SCALE)
        source.preferences.setTimerCompact(true)
        source.preferences.setTimerSounds(false)
        val layout = listOf(DashboardWidget(type = WidgetType.CONSISTENCY, size = WidgetSize.LARGE), DashboardWidget(type = WidgetType.BODYWEIGHT))
        source.dashboard.save(layout)
        val bytes = repo(newDb(), source.backup).exportBytes(setOf(BackupSection.SETTINGS))

        val target = Settings("target")
        val restore = repo(newDb(), target.backup)
        val result = restore.import(restore.openBytes(bytes), setOf(BackupSection.SETTINGS))

        assertEquals(1, result.added[BackupSection.SETTINGS])
        assertEquals(ThemeMode.LIGHT, target.preferences.themeMode.first())
        assertEquals(UnitSystem.IMPERIAL, target.preferences.units.first())
        assertEquals(GradeScale.V_SCALE, target.preferences.gradeScales.first().boulder)
        assertTrue(target.preferences.timerCompact.first())
        assertFalse(target.preferences.timerSounds.first())
        assertEquals(layout.map { it.type to it.size }, target.dashboard.layout.first().map { it.type to it.size })
    }

    @Test
    fun `sections switched off are left out of the file`() = runTest {
        val file = repo(seeded()).snapshot(setOf(BackupSection.PLANS))

        assertNull(file.climbs)
        assertNull(file.exercises)
        assertNull(file.sessions)
        assertNull(file.settings)
        assertEquals(3, file.plans!!.size)
    }

    @Test
    fun `plans bring their exercises even without the library`() = runTest {
        val source = newDb()
        StarterDataSeeder(FIXED_CLOCK).seed(source, includeSampleData = false)
        val bytes = repo(source).exportBytes(setOf(BackupSection.PLANS))

        val target = newDb()
        val repo = repo(target)
        repo.import(repo.openBytes(bytes), setOf(BackupSection.PLANS))

        assertEquals(3, target.templateDao().count())
        assertTrue(target.exerciseDao().count() > 0)
    }

    @Test
    fun `files that are not backups are refused with a reason`() = runTest {
        val repo = repo(newDb())
        fun refusal(text: String) = assertThrows(BackupFormatException::class.java) { repo.parse(text) }.message
        assertEquals("This file is not a Crux backup.", refusal("not json"))
        assertEquals("This file is not a Crux backup.", refusal("""{"format":"other","exportedAt":"x"}"""))
        // A newer file is told apart even when the rest of it no longer reads.
        assertTrue(refusal("""{"format":"crux-backup","version":99,"climbs":"changed"}""")!!.contains("newer version"))
        val zipWithoutBackup = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { it.putNextEntry(ZipEntry("photo.jpg")) }
        }.toByteArray()
        assertTrue(runCatching { repo.openBytes(zipWithoutBackup) }.exceptionOrNull() is BackupFormatException)
    }
}
