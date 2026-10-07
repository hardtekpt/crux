package com.hardtekpt.crux.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema 19 widens the interval timer kept on an exercise (schema 18) into defaults for every
 * kind of exercise. The interval columns carry over under their general names; load is new.
 */
@androidx.room.RenameColumn.Entries(
    androidx.room.RenameColumn(tableName = "exercises", fromColumnName = "intervalPrepSeconds", toColumnName = "prepSeconds"),
    androidx.room.RenameColumn(tableName = "exercises", fromColumnName = "intervalWorkSeconds", toColumnName = "defaultSeconds"),
    androidx.room.RenameColumn(tableName = "exercises", fromColumnName = "intervalRestSeconds", toColumnName = "defaultRepRestSeconds"),
    androidx.room.RenameColumn(tableName = "exercises", fromColumnName = "intervalRepeats", toColumnName = "defaultReps"),
    androidx.room.RenameColumn(tableName = "exercises", fromColumnName = "intervalCycles", toColumnName = "defaultSets"),
    androidx.room.RenameColumn(tableName = "exercises", fromColumnName = "intervalCycleRestSeconds", toColumnName = "defaultRestSeconds"),
)
class ExerciseDefaultsMigration : AutoMigrationSpec

/**
 * Schema 16 turns each place's kinds into named sections: one per kind it had, named after
 * the kind, with its areas and climbs linked to the section of their kind.
 */
class SectionsMigration : AutoMigrationSpec {
    override fun onPostMigrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        val kinds = listOf("GYM" to "Gym", "CRAG" to "Crag", "BOARD" to "Board")
        kinds.forEach { (kind, label) ->
            db.execSQL("INSERT INTO sections (placeId, type, name, position) SELECT id, '$kind', '$label', 0 FROM places WHERE type = '$kind'")
        }
        kinds.forEach { (kind, label) ->
            db.execSQL(
                "INSERT INTO sections (placeId, type, name, position) SELECT id, '$kind', '$label', 1 FROM places " +
                    "WHERE type != '$kind' AND (',' || extraTypes || ',') LIKE '%,$kind,%'",
            )
        }
        db.execSQL(
            """
            UPDATE areas SET sectionId = (
                SELECT s.id FROM sections s JOIN places p ON p.id = s.placeId
                WHERE s.placeId = areas.placeId AND s.type = COALESCE(areas.type, p.type)
                ORDER BY s.position, s.id LIMIT 1
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            UPDATE climbs SET sectionId = COALESCE(
                (SELECT a.sectionId FROM areas a WHERE a.id = climbs.areaId),
                (SELECT s.id FROM sections s WHERE s.placeId = climbs.placeId AND s.type = climbs.venue ORDER BY s.position, s.id LIMIT 1)
            ) WHERE placeId IS NOT NULL
            """.trimIndent(),
        )
    }
}

/**
 * Schema 6 adds places, areas and problems. Every distinct place name already typed into a
 * climb becomes a place of the matching kind, and those climbs are linked to it.
 */
class PlacesMigration : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO places (name, type, createdAtMillis)
            SELECT TRIM(place), venue, 0 FROM climbs
            WHERE place IS NOT NULL AND TRIM(place) != ''
            GROUP BY LOWER(TRIM(place)), venue
            """,
        )
        db.execSQL(
            """
            UPDATE climbs SET placeId = (
                SELECT p.id FROM places p
                WHERE LOWER(p.name) = LOWER(TRIM(climbs.place)) AND p.type = climbs.venue
            )
            WHERE place IS NOT NULL AND TRIM(place) != ''
            """,
        )
    }
}

@Database(
    entities = [
        ClimbEntity::class,
        ExerciseEntity::class,
        BodyMeasurementEntity::class,
        WorkoutTemplateEntity::class,
        TemplateBlockEntity::class,
        TemplateExerciseEntity::class,
        PlaceEntity::class,
        SectionEntity::class,
        SessionEntity::class,
        SessionItemEntity::class,
        SessionSetEntity::class,
        AreaEntity::class,
        ProblemEntity::class,
        ClimbMediaEntity::class,
        com.hardtekpt.crux.data.NoteEntity::class,
        com.hardtekpt.crux.data.ExerciseRecordEntity::class,
    ],
    version = CruxDatabase.VERSION,
    exportSchema = true,
    // From here on schema changes migrate instead of wiping data.
    autoMigrations = [
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6, spec = PlacesMigration::class),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16, spec = SectionsMigration::class),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19, spec = ExerciseDefaultsMigration::class),
    ],
)
abstract class CruxDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun climbDao(): ClimbDao
    abstract fun climbMediaDao(): ClimbMediaDao
    abstract fun noteDao(): com.hardtekpt.crux.data.NoteDao
    abstract fun exerciseRecordDao(): com.hardtekpt.crux.data.ExerciseRecordDao
    abstract fun bodyMeasurementDao(): BodyMeasurementDao
    abstract fun templateDao(): TemplateDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun sessionDao(): SessionDao

    companion object {
        /** The current schema; each bump needs an auto-migration below and its exported JSON. */
        const val VERSION = 19

        /** The climber's own data. */
        const val NAME = "crux-user.db"

        /** Demo mode's data set. */
        const val DEMO_NAME = "crux.db"
    }
}
