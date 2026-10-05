package com.hardtekpt.crux.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.ClimbRepository
import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.prefs.GradeScales
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.data.model.NewClimb
import com.hardtekpt.crux.data.model.Venue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** The draft lives here so rotation keeps what was typed. */
data class LogClimbDraft(
    val discipline: Discipline = Discipline.BOULDER,
    /** The climber's chosen scale per discipline, from Settings. */
    val scales: GradeScales = GradeScales(),
    val gradeIndex: Int = scales.boulder.defaultIndex,
    val style: AscentStyle = AscentStyle.FLASH,
    val attempts: Int = 1,
    val venue: Venue = Venue.GYM,
    val date: LocalDate,
    val name: String = "",
    val place: String = "",
    val notes: String = "",
    val dateError: String? = null,
    val nameError: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
) {
    val gradeScale: GradeScale get() = scales.forDiscipline(discipline)
    val styles: List<AscentStyle> get() = AscentStyle.forDiscipline(discipline)
    val attemptsLocked: Boolean get() = style.singleAttempt
}

@HiltViewModel
class LogClimbViewModel @Inject constructor(
    private val climbRepository: ClimbRepository,
    private val clock: Clock,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    private val _draft = MutableStateFlow(LogClimbDraft(date = LocalDate.now(clock)))
    val draft: StateFlow<LogClimbDraft> = _draft.asStateFlow()

    init {
        // Follow the scales chosen in Settings. A grade already picked in the same scale is kept.
        viewModelScope.launch {
            preferences.gradeScales.collect { scales ->
                _draft.update { draft ->
                    val keepGrade = scales.forDiscipline(draft.discipline) == draft.gradeScale
                    draft.copy(
                        scales = scales,
                        gradeIndex = if (keepGrade) draft.gradeIndex else scales.forDiscipline(draft.discipline).defaultIndex,
                    )
                }
            }
        }
    }

    fun setDiscipline(discipline: Discipline) = _draft.update { draft ->
        if (draft.discipline == discipline) return@update draft
        val style = draft.style.takeIf { it in AscentStyle.forDiscipline(discipline) } ?: AscentStyle.FLASH
        draft.copy(discipline = discipline, gradeIndex = draft.scales.forDiscipline(discipline).defaultIndex, style = style)
    }

    fun setGrade(index: Int) = _draft.update {
        it.copy(gradeIndex = index.coerceIn(it.gradeScale.grades.indices))
    }

    fun setStyle(style: AscentStyle) = _draft.update {
        // Flash and onsight are one attempt by definition; a redpoint took at least two.
        val attempts = when {
            style.singleAttempt -> 1
            style == AscentStyle.REDPOINT -> maxOf(it.attempts, 2)
            else -> it.attempts
        }
        it.copy(style = style, attempts = attempts)
    }

    fun setAttempts(attempts: Int) = _draft.update { it.copy(attempts = attempts.coerceIn(ATTEMPTS)) }

    fun setVenue(venue: Venue) = _draft.update { it.copy(venue = venue) }

    fun setDate(date: LocalDate) = _draft.update { it.copy(date = date, dateError = null) }

    fun setName(name: String) = _draft.update { it.copy(name = name, nameError = null) }

    fun setPlace(place: String) = _draft.update { it.copy(place = place.take(MAX_TEXT)) }

    fun setNotes(notes: String) = _draft.update { it.copy(notes = notes) }

    fun save() {
        val draft = _draft.value
        if (draft.isSaving || draft.saved) return
        val today = LocalDate.now(clock)
        val dateError = if (draft.date.isAfter(today)) "Pick today or an earlier day" else null
        val nameError = if (draft.name.length > MAX_TEXT) "Keep the name under $MAX_TEXT characters" else null
        if (dateError != null || nameError != null) {
            _draft.update { it.copy(dateError = dateError, nameError = nameError) }
            return
        }
        _draft.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            climbRepository.logClimb(
                NewClimb(
                    discipline = draft.discipline,
                    gradeScale = draft.gradeScale,
                    gradeIndex = draft.gradeIndex,
                    style = draft.style,
                    attempts = if (draft.style.singleAttempt) 1 else draft.attempts,
                    venue = draft.venue,
                    date = draft.date,
                    name = draft.name,
                    place = draft.place,
                    notes = draft.notes,
                ),
            )
            _draft.update { it.copy(isSaving = false, saved = true) }
        }
    }

    companion object {
        val ATTEMPTS = 1..99
        const val MAX_TEXT = 60
    }
}
