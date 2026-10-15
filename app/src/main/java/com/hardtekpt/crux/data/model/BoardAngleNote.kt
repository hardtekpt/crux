package com.hardtekpt.crux.data.model

/**
 * A board climb's angle lives in its log's note, never in a field of its own: the note's first line
 * is the plain angle ("40°") and the rest of the note follows on the next line. Logs from the
 * schema 22 migration (13 Oct) carry it as their last line instead, "Board angle 40°"; those are
 * read too, and keep that form unless the angle changes.
 */
object BoardAngleNote {
    /** What a note says: its [angle], if it has one, the [body] without that line, and whether the line was the old form. */
    data class Parsed(val angle: Int?, val body: String, val legacy: Boolean = false)

    private val FIRST = Regex("""^\s*(\d{1,2})\s*°\s*$""")
    private val LEGACY = Regex("""^\s*Board angle\s*(\d{1,2})\s*°\s*$""")

    /** The angle and the rest of [note]; a note with no angle line comes back whole. */
    fun read(note: String?): Parsed {
        val text = note.orEmpty()
        if (text.isBlank()) return Parsed(null, "")
        val lines = text.lines()
        FIRST.matchEntire(lines.first())?.let { match ->
            return Parsed(match.groupValues[1].toInt(), lines.drop(1).joinToString("\n").trim())
        }
        val last = lines.indexOfLast { it.isNotBlank() }
        LEGACY.matchEntire(lines[last])?.let { match ->
            return Parsed(match.groupValues[1].toInt(), lines.take(last).joinToString("\n").trim(), legacy = true)
        }
        return Parsed(null, text)
    }

    /** The note to store: "40°", then [body] on the next line; just [body] with no angle. */
    fun write(angle: Int?, body: String): String {
        val text = body.trim()
        if (angle == null) return text
        return if (text.isEmpty()) "$angle°" else "$angle°\n$text"
    }

    /** As [write], but a log that came in the old form keeps it while its angle is unchanged. */
    fun write(angle: Int?, body: String, loaded: Parsed?): String {
        if (loaded != null && loaded.legacy && angle != null && angle == loaded.angle) {
            val text = body.trim()
            val line = "Board angle $angle°"
            return if (text.isEmpty()) line else "$text\n$line"
        }
        return write(angle, body)
    }

    /** Angles a board is set at: 0° to 70° in steps of 5°. */
    val RANGE = 0..70
    const val STEP = 5
}
