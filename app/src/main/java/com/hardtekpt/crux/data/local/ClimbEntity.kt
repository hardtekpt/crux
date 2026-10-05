package com.hardtekpt.crux.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.Venue

/** A single logged climb. The grade is its scale plus an index into that scale. */
@Entity(tableName = "climbs", indices = [Index("dateEpochDay"), Index("placeId"), Index("problemId")])
data class ClimbEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val discipline: Discipline,
    val gradeScale: GradeScale,
    val gradeIndex: Int,
    val style: AscentStyle,
    val attempts: Int,
    val venue: Venue,
    /** The day it was climbed, as LocalDate.toEpochDay(). */
    val dateEpochDay: Long,
    /** When it was logged; orders climbs within a day. */
    val createdAtMillis: Long,
    val name: String? = null,
    /** The place's name as logged; kept even if the place itself is deleted. */
    val place: String? = null,
    val notes: String? = null,
    /** Links to the climber's places, walls and problems (schema 6). All optional. */
    val placeId: Long? = null,
    val areaId: Long? = null,
    val problemId: Long? = null,
    /** Board angle in degrees, when logged on a board. */
    val angle: Int? = null,
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
