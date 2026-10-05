package com.hardtekpt.crux.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale

/** A single logged climb. The grade is its scale plus an index into that scale. */
@Entity(tableName = "climbs", indices = [Index("dateEpochDay")])
data class ClimbEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val discipline: Discipline,
    val gradeScale: GradeScale,
    val gradeIndex: Int,
    val style: AscentStyle,
    val attempts: Int,
    /** The day it was climbed, as LocalDate.toEpochDay(). */
    val dateEpochDay: Long,
    /** When it was logged; orders climbs within a day. */
    val createdAtMillis: Long,
    val name: String? = null,
    val place: String? = null,
    val notes: String? = null,
)

/** One row per discipline and send style: the hardest climb sent that way. */
data class PersonalBestRow(
    val discipline: Discipline,
    val style: AscentStyle,
    val gradeScale: GradeScale,
    val gradeIndex: Int,
    val name: String?,
    val place: String?,
    val dateEpochDay: Long,
)
