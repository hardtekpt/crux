package com.hardtekpt.crux.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every exported schema migrates to the current one, and data typed at the oldest schema
 * survives the whole way. Step tests below cover the migrations that move data.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CruxDatabase::class.java)

    @Test
    fun everyOldSchemaMigratesToTheLatest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (version in FIRST_MIGRATABLE_VERSION until CruxDatabase.VERSION) {
            val name = "migration-from-$version.db"
            context.deleteDatabase(name)
            helper.createDatabase(name, version).close()
            helper.runMigrationsAndValidate(name, CruxDatabase.VERSION, true).close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun dataFromTheOldestSchemaSurvivesToTheLatest() {
        helper.createDatabase(DB, FIRST_MIGRATABLE_VERSION).apply {
            execSQL("INSERT INTO exercises (id, name, category, metric, notes, createdAtMillis) VALUES (1, 'Hang', 'FINGERS', 'WEIGHTED_TIME', NULL, 0)")
            execSQL("INSERT INTO body_measurements (id, type, value, dateEpochDay, createdAtMillis) VALUES (1, 'WEIGHT', 68.5, 20000, 0)")
            execSQL(
                "INSERT INTO climbs (id, discipline, gradeScale, gradeIndex, style, attempts, venue, dateEpochDay, createdAtMillis, name, place, notes) " +
                    "VALUES (1, 'ROUTE', 'FRENCH', 14, 'ONSIGHT', 1, 'CRAG', 20002, 0, 'Classic', 'Arco', 'Polished')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, CruxDatabase.VERSION, true)
        db.query(
            "SELECT c.gradeIndex, c.name, c.notes, p.name, s.type FROM climbs c " +
                "JOIN places p ON p.id = c.placeId JOIN sections s ON s.id = c.sectionId WHERE c.id = 1",
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(14, cursor.getInt(0))
            assertEquals("Classic", cursor.getString(1))
            assertEquals("Polished", cursor.getString(2))
            assertEquals("Arco", cursor.getString(3))
            assertEquals("CRAG", cursor.getString(4))
        }
        db.query("SELECT value FROM body_measurements WHERE id = 1").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(68.5, cursor.getDouble(0), 0.0)
        }
        db.query("SELECT name FROM exercises WHERE id = 1").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("Hang", cursor.getString(0))
        }
    }

    @Test
    fun migrate4To5KeepsPlanExercises() {
        helper.createDatabase(DB, 4).apply {
            execSQL("INSERT INTO exercises (id, name, category, metric, notes, createdAtMillis) VALUES (1, 'Hang', 'FINGERS', 'WEIGHTED_TIME', NULL, 0)")
            execSQL("INSERT INTO workout_templates (id, name, description, position) VALUES (1, 'Plan', '', 0)")
            execSQL("INSERT INTO template_blocks (id, templateId, position, name) VALUES (1, 1, 0, 'Main')")
            execSQL(
                "INSERT INTO template_exercises (id, blockId, exerciseId, position, sets, reps, seconds, loadKg, restSeconds) " +
                    "VALUES (1, 1, 1, 0, 6, 0, 10, 5.0, 180)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 5, true)
        db.query("SELECT sets, seconds, repRestSeconds FROM template_exercises WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(6, cursor.getInt(0))
            assertEquals(10, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
        }
    }

    @Test
    fun migrate5To6TurnsTypedPlacesIntoSavedPlaces() {
        helper.createDatabase(DB, 5).apply {
            val insert = "INSERT INTO climbs " +
                "(id, discipline, gradeScale, gradeIndex, style, attempts, venue, dateEpochDay, createdAtMillis, name, place, notes) VALUES "
            execSQL(insert + "(1, 'BOULDER', 'FONT', 10, 'FLASH', 1, 'GYM', 20000, 0, NULL, 'Block Lab', NULL)")
            execSQL(insert + "(2, 'BOULDER', 'FONT', 11, 'REDPOINT', 3, 'GYM', 20001, 0, NULL, ' block lab ', NULL)")
            execSQL(insert + "(3, 'ROUTE', 'FRENCH', 14, 'ONSIGHT', 1, 'CRAG', 20002, 0, NULL, 'Arco', NULL)")
            execSQL(insert + "(4, 'ROUTE', 'FRENCH', 14, 'ONSIGHT', 1, 'GYM', 20003, 0, NULL, NULL, NULL)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB, 6, true)
        db.query("SELECT COUNT(*) FROM places").use { cursor ->
            cursor.moveToFirst()
            assertEquals(2, cursor.getInt(0))
        }
        db.query("SELECT c.id, p.name, p.type FROM climbs c LEFT JOIN places p ON p.id = c.placeId ORDER BY c.id").use { cursor ->
            val rows = buildList {
                while (cursor.moveToNext()) add(Triple(cursor.getLong(0), cursor.getString(1), cursor.getString(2)))
            }
            assertEquals("GYM", rows[0].third)
            assertEquals(rows[0].second, rows[1].second)
            assertEquals("Arco" to "CRAG", rows[2].second to rows[2].third)
            assertEquals(null, rows[3].second)
        }
    }

    @Test
    fun migrate6To7AddsAnEmptyEffort() {
        helper.createDatabase(DB, 6).apply {
            execSQL(
                "INSERT INTO climbs (id, discipline, gradeScale, gradeIndex, style, attempts, venue, dateEpochDay, createdAtMillis) " +
                    "VALUES (1, 'BOULDER', 'FONT', 10, 'FLASH', 1, 'GYM', 20000, 0)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 7, true)
        db.query("SELECT gradeIndex, effort FROM climbs WHERE id = 1").use { cursor ->
            cursor.moveToFirst()
            assertEquals(10, cursor.getInt(0))
            assertEquals(true, cursor.isNull(1))
        }
    }

    @Test
    fun migrate15To16GivesEachKindASectionAndLinksAreasAndClimbs() {
        helper.createDatabase(DB, 15).apply {
            execSQL("INSERT INTO places (id, name, type, createdAtMillis, favourite, extraTypes) VALUES (1, 'Block Lab', 'GYM', 0, 0, 'BOARD')")
            execSQL("INSERT INTO places (id, name, type, createdAtMillis, favourite, extraTypes) VALUES (2, 'Arco', 'CRAG', 0, 0, '')")
            execSQL("INSERT INTO areas (id, placeId, name, position, type) VALUES (1, 1, 'Cave', 0, NULL)")
            execSQL("INSERT INTO areas (id, placeId, name, position, type) VALUES (2, 1, 'Kilter', 1, 'BOARD')")
            val climb = "INSERT INTO climbs " +
                "(id, discipline, gradeScale, gradeIndex, style, attempts, venue, dateEpochDay, createdAtMillis, placeId, areaId) VALUES "
            execSQL(climb + "(1, 'BOULDER', 'FONT', 10, 'FLASH', 1, 'BOARD', 20000, 0, 1, NULL)")
            execSQL(climb + "(2, 'BOULDER', 'FONT', 10, 'FLASH', 1, 'GYM', 20000, 0, 1, 1)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 16, true)
        val sections = db.query("SELECT id, placeId, type, name FROM sections ORDER BY placeId, position").use { c ->
            buildList { while (c.moveToNext()) add(listOf(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3))) }
        }
        assertEquals(listOf(listOf(1L, "GYM", "Gym"), listOf(1L, "BOARD", "Board"), listOf(2L, "CRAG", "Crag")), sections.map { it.drop(1) })
        val gym = sections[0][0]
        val board = sections[1][0]
        db.query("SELECT sectionId FROM areas ORDER BY id").use { c ->
            c.moveToNext()
            assertEquals(gym, c.getLong(0))
            c.moveToNext()
            assertEquals(board, c.getLong(0))
        }
        db.query("SELECT sectionId FROM climbs ORDER BY id").use { c ->
            c.moveToNext()
            assertEquals(board, c.getLong(0))
            c.moveToNext()
            assertEquals(gym, c.getLong(0))
        }
    }

    @Test
    fun migrate18To19KeepsAnExercisesIntervalTimerAsItsDefaults() {
        helper.createDatabase(DB, 18).apply {
            execSQL(
                "INSERT INTO exercises (id, name, category, metric, createdAtMillis, intervalPrepSeconds, intervalWorkSeconds, " +
                    "intervalRestSeconds, intervalRepeats, intervalCycles, intervalCycleRestSeconds) " +
                    "VALUES (1, 'Repeaters', 'FINGERS', 'INTERVALS', 0, 5, 7, 3, 6, 3, 180)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 19, true)
        db.query(
            "SELECT prepSeconds, defaultSeconds, defaultRepRestSeconds, defaultReps, defaultSets, defaultRestSeconds, defaultLoadKg FROM exercises",
        ).use { c ->
            c.moveToFirst()
            assertEquals(listOf(5, 7, 3, 6, 3, 180), (0..5).map { c.getInt(it) })
            assertEquals(true, c.isNull(6))
        }
    }

    @Test
    fun migrate19To20GivesEachSectionItsPlacesGrades() {
        helper.createDatabase(DB, 19).apply {
            execSQL(
                "INSERT INTO places (id, name, type, createdAtMillis, favourite, extraTypes, boulderScale, routeScale, localScale) " +
                    "VALUES (1, 'Block Lab', 'GYM', 0, 0, 'BOARD', 'V_SCALE', 'YDS', NULL)",
            )
            execSQL("INSERT INTO sections (id, placeId, type, name, position) VALUES (1, 1, 'GYM', 'Main gym', 0)")
            execSQL("INSERT INTO sections (id, placeId, type, name, position) VALUES (2, 1, 'BOARD', 'Kilter', 1)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 20, true)
        db.query("SELECT boulderScale, routeScale FROM sections ORDER BY id").use { c ->
            while (c.moveToNext()) {
                assertEquals("V_SCALE", c.getString(0))
                assertEquals("YDS", c.getString(1))
            }
        }
        db.query("SELECT boulderScale FROM places").use { c ->
            c.moveToFirst()
            assertEquals(true, c.isNull(0))
        }
    }

    @Test
    fun migrate20To21GivesEveryLogAClimb() {
        helper.createDatabase(DB, 20).apply {
            execSQL("INSERT INTO places (id, name, type, createdAtMillis, favourite, extraTypes) VALUES (1, 'Block Lab', 'GYM', 0, 0, '')")
            execSQL("INSERT INTO sections (id, placeId, type, name, position) VALUES (1, 1, 'GYM', 'Gym', 0)")
            execSQL(
                "INSERT INTO problems (id, placeId, areaId, name, discipline, gradeScale, gradeIndex, retired, createdAtMillis) " +
                    "VALUES (1, 1, NULL, 'Seventh seal', 'BOULDER', 'FONT', 10, 0, 0)",
            )
            val climb = "INSERT INTO climbs " +
                "(id, discipline, gradeScale, gradeIndex, style, attempts, venue, dateEpochDay, createdAtMillis, placeId, problemId, name) VALUES "
            // On a problem; two named alike at the place (one sent); one with no name; one with no place.
            execSQL(climb + "(1, 'BOULDER', 'FONT', 10, 'REDPOINT', 3, 'GYM', 20000, 1, 1, 1, 'Seventh seal')")
            execSQL(climb + "(2, 'BOULDER', 'FONT', 9, 'ATTEMPT', 2, 'GYM', 20000, 2, 1, NULL, 'Cheesecake')")
            execSQL(climb + "(3, 'BOULDER', 'FONT', 9, 'FLASH', 1, 'GYM', 20002, 3, 1, NULL, 'cheesecake')")
            execSQL(climb + "(4, 'BOULDER', 'FONT', 8, 'ATTEMPT', 1, 'GYM', 20003, 4, 1, NULL, NULL)")
            execSQL(climb + "(5, 'ROUTE', 'FRENCH', 12, 'ONSIGHT', 1, 'CRAG', 20004, 5, NULL, NULL, 'Pilastro')")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 21, true)
        val rows = db.query("SELECT id, problemId, sends, name FROM climbs ORDER BY id").use { c ->
            buildList { while (c.moveToNext()) add(listOf(c.getLong(0), c.getLong(1), c.getInt(2).toLong(), c.getString(3))) }
        }
        // Every log has a climb; the problem's log keeps it; the two cheesecakes share one.
        assertEquals(1L, rows[0][1])
        assertEquals(rows[1][1], rows[2][1])
        assertEquals(listOf(1L, 0L, 1L, 0L, 1L), rows.map { it[2] })
        // The unnamed one is named for its grade and place.
        assertEquals("6B+ · Block Lab", rows[3][3])
        db.query("SELECT COUNT(*), SUM(placeId IS NULL) FROM problems").use { c ->
            c.moveToFirst()
            assertEquals(4, c.getInt(0))
            assertEquals(1, c.getInt(1))
        }
    }

    private companion object {
        const val DB = "migration-test.db"

        /** Auto-migrations start at schema 4. */
        const val FIRST_MIGRATABLE_VERSION = 4
    }
}
