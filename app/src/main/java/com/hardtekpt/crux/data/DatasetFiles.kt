package com.hardtekpt.crux.data

import com.hardtekpt.crux.data.model.BoardHolds
import com.hardtekpt.crux.data.model.BoardModel
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Reading and writing datasets as files: a CSV with a header row (name, grade, type, setter,
 * note, in any order and case, comma or semicolon separated), and a Crux dataset file
 * (`.crux-dataset.json`, format "crux-dataset", version 1). Grades are kept as text until they
 * are read in a scale, so the import preview can switch scale and read the file again.
 */
object DatasetFiles {
    /** A climb as a file has it, before its grade is read in a scale. */
    data class RawClimb(
        val name: String,
        val discipline: Discipline = Discipline.BOULDER,
        val grade: String? = null,
        val setter: String? = null,
        val note: String? = null,
    )

    /** What a file holds: its climbs, what it said about the dataset (Crux files), and what couldn't be used. */
    data class Parsed(
        val climbs: List<RawClimb>,
        /** Rows with no name, which were left out. */
        val skipped: Int = 0,
        /** CSV columns that aren't name, grade, type, setter or note. */
        val ignoredColumns: List<String> = emptyList(),
        /** A Crux file's dataset: its name, board and scales. */
        val dataset: Dataset? = null,
    )

    class Unreadable(message: String) : Exception(message)

    /** A byte-order mark some spreadsheet apps put at the start of a file. */
    private val BOM = 0xFEFF.toChar().toString()

    private val HEADERS = setOf("name", "grade", "type", "setter", "note")
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        // The format and version always go in the file, so another app (or Crux) can tell what it is.
        encodeDefaults = true
        explicitNulls = false
    }

    // ---- CSV -------------------------------------------------------------------------------

    /** Reads a CSV with a header row; only `name` is required. */
    fun parseCsv(text: String): Parsed {
        val clean = text.removePrefix(BOM)
        val firstLine = clean.lineSequence().firstOrNull { it.isNotBlank() } ?: throw Unreadable("This file is empty.")
        val separator = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
        val rows = csvRows(clean, separator).filter { row -> row.any { it.isNotBlank() } }
        val header = rows.firstOrNull()?.map { it.trim().lowercase() } ?: throw Unreadable("This file is empty.")
        if ("name" !in header) throw Unreadable("The file needs a header row with a \"name\" column.")
        fun column(name: String) = header.indexOf(name).takeIf { it >= 0 }
        val nameAt = column("name")!!
        val gradeAt = column("grade")
        val typeAt = column("type")
        val setterAt = column("setter")
        val noteAt = column("note")
        var skipped = 0
        val climbs = rows.drop(1).mapNotNull { row ->
            fun cell(at: Int?) = at?.let { row.getOrNull(it) }?.trim()?.ifEmpty { null }
            val name = cell(nameAt)
            if (name == null) {
                skipped++
                return@mapNotNull null
            }
            RawClimb(
                name = name,
                discipline = discipline(cell(typeAt)),
                grade = cell(gradeAt),
                setter = cell(setterAt),
                note = cell(noteAt),
            )
        }
        return Parsed(climbs, skipped, header.filter { it.isNotEmpty() && it !in HEADERS })
    }

    /** The dataset as a CSV: name, grade, type, setter, note, with a header row. */
    fun writeCsv(dataset: Dataset): String = buildString {
        append("name,grade,type,setter,note\n")
        dataset.climbs.forEach { climb ->
            append(
                listOf(climb.name, climb.gradeLabel.orEmpty(), climb.discipline.typeName, climb.setter.orEmpty(), climb.note.orEmpty()).joinToString(",") {
                    quote(it)
                },
            )
            append('\n')
        }
    }

    private fun quote(text: String): String = if (text.any {
            it == ',' || it == '"' || it == '\n' || it == ';'
        }
    ) {
        "\"" + text.replace("\"", "\"\"") + "\""
    } else {
        text
    }

    /** Splits CSV text into rows of cells, with quoted cells that may hold separators, quotes and newlines. */
    internal fun csvRows(text: String, separator: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> {
                    cell.append('"')
                    i++
                }

                c == '"' -> quoted = !quoted

                !quoted && c == separator -> {
                    row.add(cell.toString())
                    cell.clear()
                }

                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row.add(cell.toString())
                    cell.clear()
                    rows.add(row)
                    row = mutableListOf()
                }

                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row)
        }
        return rows
    }

    private fun discipline(type: String?): Discipline = if (type?.trim()?.lowercase()?.startsWith("route") == true) Discipline.ROUTE else Discipline.BOULDER

    private val Discipline.typeName: String get() = if (this == Discipline.ROUTE) "route" else "boulder"

    // ---- Crux dataset file -----------------------------------------------------------------

    @Serializable
    data class CruxFile(
        val format: String = FORMAT,
        val version: Int = VERSION,
        val exported: String? = null,
        val dataset: CruxDataset,
        val climbs: List<CruxClimb> = emptyList(),
    )

    @Serializable
    data class CruxDataset(
        val name: String,
        val board: String? = null,
        val boardSize: String? = null,
        val boardHolds: String? = null,
        val boulderScale: String? = null,
        val routeScale: String? = null,
    )

    @Serializable
    data class CruxClimb(val name: String, val type: String = "boulder", val grade: String? = null, val setter: String? = null, val note: String? = null)

    const val FORMAT = "crux-dataset"
    const val VERSION = 1

    fun writeCrux(dataset: Dataset, exportedAt: String): String = json.encodeToString(
        CruxFile.serializer(),
        CruxFile(
            exported = exportedAt,
            dataset = CruxDataset(
                name = dataset.name,
                board = dataset.board?.key ?: DatasetEntity.CUSTOM_BOARD.takeIf { dataset.customBoard },
                boardSize = dataset.boardSize,
                boardHolds = dataset.boardHolds?.key,
                boulderScale = dataset.boulderScale?.name,
                routeScale = dataset.routeScale?.name,
            ),
            climbs = dataset.climbs.map { CruxClimb(it.name, it.discipline.typeName, it.gradeLabel, it.setter, it.note) },
        ),
    )

    fun parseCrux(text: String): Parsed {
        val file = runCatching {
            json.decodeFromString(CruxFile.serializer(), text.removePrefix(BOM))
        }.getOrElse { throw Unreadable("This file isn't a Climb Logger dataset.") }
        if (file.format != FORMAT) throw Unreadable("This file isn't a Climb Logger dataset.")
        if (file.version > VERSION) throw Unreadable("This dataset comes from a newer version of Climb Logger. Update the app to import it.")
        val board = BoardModel.fromKey(file.dataset.board)
        val dataset = Dataset(
            name = file.dataset.name,
            board = board,
            customBoard = file.dataset.board == DatasetEntity.CUSTOM_BOARD,
            boardSize = file.dataset.boardSize?.takeIf { board?.sizes?.contains(it) == true },
            boardHolds = BoardHolds.fromKey(file.dataset.boardHolds)?.takeIf { board?.hasHolds == true },
            boulderScale = scale(file.dataset.boulderScale, Discipline.BOULDER),
            routeScale = scale(file.dataset.routeScale, Discipline.ROUTE),
        )
        var skipped = 0
        val climbs = file.climbs.mapNotNull { climb ->
            val name = climb.name.trim()
            if (name.isEmpty()) {
                skipped++
                null
            } else {
                RawClimb(name, discipline(climb.type), climb.grade?.trim()?.ifEmpty { null }, climb.setter, climb.note)
            }
        }
        return Parsed(climbs, skipped, dataset = dataset)
    }

    private fun scale(name: String?, discipline: Discipline): GradeScale? =
        GradeScale.entries.firstOrNull { it.name == name && it.discipline == discipline && !it.isLocal }

    /** Reads a file by its name: a Crux dataset file, else CSV. */
    fun parse(fileName: String?, text: String): Parsed =
        if (fileName?.lowercase()?.endsWith(".json") == true || text.trimStart().startsWith("{")) parseCrux(text) else parseCsv(text)

    // ---- Grades ----------------------------------------------------------------------------

    /** A file's climbs, their grades read in [scaleFor]'s scales; a grade that isn't one goes in the note. */
    fun resolve(climbs: List<RawClimb>, scaleFor: (Discipline) -> GradeScale): List<DatasetClimb> = climbs.map { raw ->
        val scale = scaleFor(raw.discipline)
        val index = raw.grade?.let { gradeIndex(scale, it) }
        DatasetClimb(
            name = raw.name,
            discipline = raw.discipline,
            gradeScale = scale.takeIf { index != null },
            gradeIndex = index,
            setter = raw.setter,
            note = if (raw.grade != null && index == null) listOfNotNull(raw.note, "Grade in file: ${raw.grade}").joinToString("\n") else raw.note,
        )
    }

    /** Which grades in [climbs] [scaleFor] can't read, for the preview. */
    fun unknownGrades(climbs: List<RawClimb>, scaleFor: (Discipline) -> GradeScale): List<RawClimb> =
        climbs.filter { it.grade != null && gradeIndex(scaleFor(it.discipline), it.grade) == null }

    /** The index of [grade] in [scale], ignoring case and spaces; null if it isn't one of the scale's. */
    fun gradeIndex(scale: GradeScale, grade: String): Int? {
        val wanted = grade.replace(" ", "").lowercase()
        return scale.grades.indexOfFirst { it.replace(" ", "").lowercase() == wanted }.takeIf { it >= 0 }
    }
}
