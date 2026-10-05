package com.hardtekpt.crux.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Schema 4 → 5 keeps plans and adds rest between repeats, defaulting to zero. */
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

    private companion object {
        const val DB = "migration-test.db"
    }
}
