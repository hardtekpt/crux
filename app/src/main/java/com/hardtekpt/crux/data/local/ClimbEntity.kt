package com.hardtekpt.crux.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A single logged climb. Starter schema; it will grow with the journal feature. */
@Entity(tableName = "climbs")
data class ClimbEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val grade: String,
    val loggedAtEpochMillis: Long,
    val notes: String? = null,
)
