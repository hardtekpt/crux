package com.hardtekpt.crux.data

import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.NewClimb
import com.hardtekpt.crux.data.model.SourceChange
import com.hardtekpt.crux.data.model.Venue
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * + Fell and + Sent on a session's playlist card: one go on the playlist's climb, without the
 * Log climb form. The go joins the climb's log in this session, or starts one on the climb at
 * the session's place and facility (the climb of that name there, or a new one picked from the
 * dataset).
 */
class PlaylistGoes @Inject constructor(
    private val climbs: ClimbRepository,
    private val places: PlaceRepository,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
) {
    /** How to take a go back: delete the log it started, or put the log back as it was. */
    sealed interface Undo {
        data class Delete(val logId: Long) : Undo

        data class Restore(val log: Climb) : Undo
    }

    suspend fun add(session: Session, source: ClimbSource, sent: Boolean): Undo {
        val place = session.placeId?.let { places.getPlace(it) }
        val section = place?.sections?.firstOrNull { it.id == session.sectionId } ?: place?.sections?.firstOrNull()
        val here = places.climbsAt(place?.id).filter { place == null || place.sections.size <= 1 || it.sectionId == null || it.sectionId == section?.id }
        val climb = source.climb
        val problem =
            here.firstOrNull { if (climb.catalogueUuid != null) it.sourceCatalogueUuid == climb.catalogueUuid else it.sourceDatasetClimbId == climb.id }
                ?: here.firstOrNull { it.discipline == climb.discipline && it.name.trim().equals(climb.name.trim(), ignoreCase = true) }
        val logs = problem?.let { climbs.observeClimbsForProblem(it.id).first() }.orEmpty()
        val link = SourceChange(climb.id.takeIf { climb.catalogueUuid == null }, climb.setter, climb.catalogueUuid)
        val inSession = logs.firstOrNull { it.sessionId == session.id }
        if (inSession != null) {
            val sends = inSession.sends + if (sent) 1 else 0
            // A first-go send stays one; otherwise a send after falls is a redpoint.
            val style = when {
                sends == 0 -> AscentStyle.ATTEMPT
                inSession.style.singleAttempt -> inSession.style
                else -> AscentStyle.REDPOINT
            }
            climbs.updateClimb(inSession.id, inSession.toNew().copy(attempts = inSession.attempts + 1, sends = sends, style = style, source = link))
            return Undo.Restore(inSession)
        }
        val goesBefore = logs.sumOf { it.attempts }
        val style = when {
            !sent -> AscentStyle.ATTEMPT
            goesBefore == 0 -> AscentStyle.FLASH
            else -> AscentStyle.REDPOINT
        }
        // The climb's grade; a new one's in the facility's scale (or the climber's), converted from the dataset's.
        val scale =
            problem?.gradeScale ?: section?.scaleFor(climb.discipline)?.takeIf { !it.isLocal }
                ?: preferences.gradeScales.first().forDiscipline(climb.discipline)
        val index = problem?.gradeIndex
            ?: climb.gradeScale?.let { from -> climb.gradeIndex?.let { convertGrade(from, it, scale) } }
            ?: scale.defaultIndex
        val id = climbs.logClimb(
            NewClimb(
                discipline = climb.discipline,
                gradeScale = scale,
                gradeIndex = index,
                style = style,
                attempts = 1,
                sends = if (sent) 1 else 0,
                venue = section?.type?.venue ?: if (source.dataset.board != null || source.dataset.customBoard) Venue.BOARD else Venue.GYM,
                date = LocalDate.now(clock),
                name = problem?.name ?: climb.name,
                place = place?.name,
                notes = null,
                placeId = place?.id,
                sectionId = section?.id,
                sessionId = session.id,
                problemId = problem?.id,
                gradeLabel = problem?.gradeLabel,
                gradeColour = problem?.gradeColour,
                source = link,
            ),
        )
        return Undo.Delete(id)
    }

    suspend fun undo(undo: Undo) {
        when (undo) {
            is Undo.Delete -> climbs.deleteClimb(undo.logId)
            is Undo.Restore -> climbs.updateClimb(undo.log.id, undo.log.toNew())
        }
    }

    private fun Climb.toNew() = NewClimb(
        discipline = discipline,
        gradeScale = gradeScale,
        gradeIndex = gradeIndex,
        style = style,
        attempts = attempts,
        venue = venue,
        date = date,
        name = name,
        place = place,
        notes = notes,
        placeId = placeId,
        areaId = areaId,
        problemId = problemId,
        effort = effort,
        gradeLabel = gradeLabel,
        gradeColour = gradeColour,
        sectionId = sectionId,
        sessionId = sessionId,
        sends = sends,
    )
}
