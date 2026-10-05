package com.hardtekpt.crux.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Schema 4 → 5 adds rest between repeats; 5 → 6 turns typed place names into saved places. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CruxDatabase::class.java)

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
            val insert = "INSERT INTO climbs (id, discipline, gradeScale, gradeIndex, style, attempts, venue, dateEpochDay, createdAtMillis, name, place, notes) VALUES "
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

    private companion object {
        const val DB = "migration-test.db"
    }
}
