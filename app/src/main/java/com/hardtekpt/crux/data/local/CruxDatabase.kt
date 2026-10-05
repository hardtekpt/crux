package com.hardtekpt.crux.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ClimbEntity::class,
        ExerciseEntity::class,
        BodyMeasurementEntity::class,
        WorkoutTemplateEntity::class,
        TemplateBlockEntity::class,
        TemplateExerciseEntity::class,
    ],
    version = 5,
    exportSchema = true,
    // From here on schema changes migrate instead of wiping data.
    autoMigrations = [AutoMigration(from = 4, to = 5)],
)
abstract class CruxDatabase : RoomDatabase() {
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
