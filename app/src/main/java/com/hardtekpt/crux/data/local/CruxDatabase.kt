package com.hardtekpt.crux.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ClimbEntity::class,
        BodyMeasurementEntity::class,
        WorkoutTemplateEntity::class,
        TemplateBlockEntity::class,
        TemplateExerciseEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class CruxDatabase : RoomDatabase() {
    abstract fun climbDao(): ClimbDao
    abstract fun bodyMeasurementDao(): BodyMeasurementDao
    abstract fun templateDao(): TemplateDao

    companion object {
        const val NAME = "crux.db"
    }
}
