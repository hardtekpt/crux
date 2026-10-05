package com.hardtekpt.crux.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ClimbEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CruxDatabase : RoomDatabase() {
    abstract fun climbDao(): ClimbDao

    companion object {
        const val NAME = "crux.db"
    }
}
