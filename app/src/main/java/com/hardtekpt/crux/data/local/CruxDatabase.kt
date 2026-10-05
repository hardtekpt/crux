package com.hardtekpt.crux.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

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
        AreaEntity::class,
        ProblemEntity::class,
    ],
    version = 8,
    exportSchema = true,
    // From here on schema changes migrate instead of wiping data.
    autoMigrations = [
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6, spec = PlacesMigration::class),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
    ],
)
abstract class CruxDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun climbDao(): ClimbDao
    abstract fun bodyMeasurementDao(): BodyMeasurementDao
    abstract fun templateDao(): TemplateDao
    abstract fun exerciseDao(): ExerciseDao

    companion object {
        /** The climber's own data. */
        const val NAME = "crux-user.db"
        /** Demo mode's data set. */
        const val DEMO_NAME = "crux.db"
    }
}
