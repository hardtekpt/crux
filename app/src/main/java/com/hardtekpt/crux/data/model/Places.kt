package com.hardtekpt.crux.data.model

import java.time.LocalDate

data class Place(
    val id: Long,
    val name: String,
    val type: PlaceType,
    val location: String?,
    /** Null means "use my settings" for that discipline. */
    val boulderScale: GradeScale?,
    val routeScale: GradeScale?,
    val defaultAngle: Int?,
    val notes: String?,
    /** The place's own grades, used where a discipline's scale is local. */
    val localScale: LocalScale? = null,
    /** Shown as a quick pick on Log climb. */
    val favourite: Boolean = false,
    /** Where it is on the map, if the climber set it. */
    val mapLocation: MapLocation? = null,
) {
    fun scaleFor(discipline: Discipline): GradeScale? = when (discipline) {
        Discipline.BOULDER -> boulderScale
        Discipline.ROUTE -> routeScale
    }

    /** The grades climbs here pick from, or null to use the climber's settings. */
    fun systemFor(discipline: Discipline): GradeSystem? =
        scaleFor(discipline)?.let { GradeSystem(it, localScale.takeIf { _ -> it.isLocal }) }
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
data class ProblemStats(
    val sessions: Int,
    val attempts: Int,
    val firstSend: LocalDate?,
    val lastGo: LocalDate,
) {
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
)

data class PlaceDetail(
    val place: Place,
    val areas: List<Area>,
    val problems: List<ProblemWithStats>,
)

/** An open project: a problem with goes but no send, and where it is. */
data class Project(
    val problem: Problem,
    val placeName: String,
    val areaName: String?,
    val stats: ProblemStats,
)
