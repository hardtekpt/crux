package com.hardtekpt.crux.data.model

import java.time.LocalDate

/**
 * A physical place: a gym, crag or board, or several of them in one location (a gym with a
 * board). [type] is the main kind; [types] lists every kind here, main first.
 */
data class Place(
    val id: Long,
    val name: String,
    val type: PlaceType,
    val location: String?,
    val defaultAngle: Int?,
    val notes: String?,
    /** Shown as a quick pick on Log climb. */
    val favourite: Boolean = false,
    /** Where it is on the map, if the climber set it. */
    val mapLocation: MapLocation? = null,
    val types: List<PlaceType> = listOf(type),
    /** The named parts of the place, in order; at least one once saved. */
    val sections: List<Section> = emptyList(),
) {
    /** More than one section (or kind): pickers ask which part of the place. */
    val hasSeveralTypes: Boolean get() = sections.size > 1 || types.size > 1

    /** "Main gym · Spray wall · Moonboard", or the kinds when there are no sections. */
    val typesLabel: String get() = if (sections.isNotEmpty()) sections.joinToString(" · ") { it.name } else types.joinToString(" · ") { it.label }

    /** The section an area is in: its own, or the place's first. */
    fun sectionOf(area: Area?): Section? = sections.firstOrNull { it.id == area?.sectionId } ?: sections.firstOrNull()

    /** The kind of climbing an area is: its section's, or the place's main kind. */
    fun typeOf(area: Area?): PlaceType = sectionOf(area)?.type ?: area?.type?.takeIf { it in types } ?: type

    /** A section by id, or the place's first. */
    fun section(id: Long?): Section? = sections.firstOrNull { it.id == id } ?: sections.firstOrNull()

    /** The scale a section grades a discipline in, or null for the climber's settings. */
    fun scaleFor(discipline: Discipline, sectionId: Long?): GradeScale? = section(sectionId)?.scaleFor(discipline)

    /** The grades climbs in a section pick from, or null to use the climber's settings. */
    fun systemFor(discipline: Discipline, sectionId: Long?): GradeSystem? = section(sectionId)?.systemFor(discipline)
}

/**
 * One named part of a place: a kind and a name, like Board "Moonboard", and the grades climbs
 * there use. Null scales use the climber's settings.
 */
data class Section(
    val id: Long,
    val placeId: Long,
    val type: PlaceType,
    val name: String,
    val boulderScale: GradeScale? = null,
    val routeScale: GradeScale? = null,
    /** The section's own grades, used where a discipline's scale is local. */
    val localScale: LocalScale? = null,
) {
    /** Boards only hold boulders. */
    val disciplines: List<Discipline> get() = if (type == PlaceType.BOARD) listOf(Discipline.BOULDER) else Discipline.entries

    fun scaleFor(discipline: Discipline): GradeScale? = when (discipline) {
        Discipline.BOULDER -> boulderScale
        Discipline.ROUTE -> routeScale
    }

    fun systemFor(discipline: Discipline): GradeSystem? = scaleFor(discipline)?.let { GradeSystem(it, localScale.takeIf { _ -> it.isLocal }) }
}

/** A point on the map and the address or place name found for it. */
data class MapLocation(val latitude: Double, val longitude: Double, val address: String?)

data class Area(
    val id: Long,
    val placeId: Long,
    val name: String,
    val angle: Int?,
    val resetDate: LocalDate?,
    /** A photo of the wall or a map with it marked; a file name in app storage. */
    val imagePath: String? = null,
    /** Which of the place's kinds this is; null means the place's main kind (schema 15). */
    val type: PlaceType? = null,
    /** The section it's in; null means the place's first. */
    val sectionId: Long? = null,
)

data class Problem(
    val id: Long,
    val placeId: Long,
    val areaId: Long?,
    val name: String,
    val discipline: Discipline,
    val gradeScale: GradeScale,
    val gradeIndex: Int,
    val tape: Int?,
    val setDate: LocalDate?,
    val retired: Boolean,
    val notes: String?,
    val gradeLabel: String? = null,
    val gradeColour: Long? = null,
) {
    val grade: String get() = gradeLabel(gradeScale, gradeIndex, gradeLabel)
}

/** Every go the climber logged on one problem, summed. */
data class ProblemStats(val sessions: Int, val attempts: Int, val firstSend: LocalDate?, val lastGo: LocalDate) {
    val sent: Boolean get() = firstSend != null
}

data class ProblemWithStats(val problem: Problem, val stats: ProblemStats?) {
    /** Tried but not sent yet. */
    val isProject: Boolean get() = stats != null && !stats.sent
}

data class PlaceSummary(
    val place: Place,
    val climbs: Int,
    val lastVisit: LocalDate?,
    /** How many walls (sectors, sets) and current problems it has. */
    val walls: Int = 0,
    val problems: Int = 0,
    /** Problems here you've tried and not sent yet. */
    val openProjects: Int = 0,
    /** A wall's photo or map to show on the place's card, if any wall has one. */
    val coverImage: String? = null,
)

data class PlaceDetail(val place: Place, val areas: List<Area>, val problems: List<ProblemWithStats>)

/** An open project: a problem with goes but no send, and where it is. */
data class Project(val problem: Problem, val placeName: String, val areaName: String?, val stats: ProblemStats)
