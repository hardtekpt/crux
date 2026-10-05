package com.hardtekpt.crux.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How a place's own grades are written: a run of numbers, or coloured tapes. */
@Serializable
enum class LocalKind(val label: String) { NUMBERS("Numbers"), COLOURS("Colour tapes") }

/** One step of a local scale. [colour] is an ARGB value for tape grades. */
@Serializable
data class LocalGrade(val name: String, val colour: Long? = null)

/**
 * A gym's own grades, easiest first. Stored on the place; climbs and problems keep the
 * index into it plus a copy of the label, so they still read right if the scale changes.
 * Local grades are never converted to or compared with standard scales.
 */
@Serializable
data class LocalScale(
    val kind: LocalKind,
    val grades: List<LocalGrade>,
) {
    val labels: List<String> get() = grades.map { it.name }

    fun encode(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun decode(text: String?): LocalScale? =
            text?.let { runCatching { json.decodeFromString<LocalScale>(it) }.getOrNull() }

        fun numbers(from: Int, to: Int): LocalScale =
            LocalScale(LocalKind.NUMBERS, (from..to).map { LocalGrade(it.toString()) })

        /** A common gym circuit, easiest first; the climber renames, recolours and reorders it. */
        val DEFAULT_COLOURS = LocalScale(
            LocalKind.COLOURS,
            listOf(
                LocalGrade("Yellow", 0xFFF2C94CL),
                LocalGrade("Green", 0xFF4CAF50L),
                LocalGrade("Blue", 0xFF2F80EDL),
                LocalGrade("Purple", 0xFF9B51E0L),
                LocalGrade("Red", 0xFFEB5757L),
                LocalGrade("Black", 0xFF1E1E1EL),
            ),
        )

        val DEFAULT_NUMBERS = numbers(1, 10)

        /** The tape colours offered when building a colour scale. */
        val PALETTE: List<Pair<String, Long>> = listOf(
            "White" to 0xFFF5F5F5L,
            "Yellow" to 0xFFF2C94CL,
            "Orange" to 0xFFF2994AL,
            "Green" to 0xFF4CAF50L,
            "Teal" to 0xFF26A69AL,
            "Blue" to 0xFF2F80EDL,
            "Purple" to 0xFF9B51E0L,
            "Pink" to 0xFFF06292L,
            "Red" to 0xFFEB5757L,
            "Brown" to 0xFF8D6E63L,
            "Grey" to 0xFF9E9E9EL,
            "Black" to 0xFF1E1E1EL,
        )
    }
}

/**
 * The grades a climb or problem picks from: a standard scale, or a place's local one.
 * Use this wherever grades are listed or printed.
 */
data class GradeSystem(val scale: GradeScale, val local: LocalScale? = null) {
    val isLocal: Boolean get() = scale.isLocal

    val labels: List<String> get() = if (isLocal) local?.labels.orEmpty() else scale.grades

    val defaultIndex: Int get() = if (isLocal) (labels.size / 2).coerceAtLeast(0) else scale.defaultIndex

    fun label(index: Int): String = labels.getOrNull(index) ?: scale.label(index)

    fun colour(index: Int): Long? = if (isLocal) local?.grades?.getOrNull(index)?.colour else null

    /** "Font", or "Local numbers" / "Local colours". */
    val name: String get() = when {
        !isLocal -> scale.label
        local?.kind == LocalKind.COLOURS -> "Local colours"
        else -> "Local numbers"
    }
}

/** A grade label for display, falling back for local grades stored without one. */
fun gradeLabel(scale: GradeScale, index: Int, label: String?): String =
    if (scale.isLocal) label ?: "#${index + 1}" else scale.label(index)
