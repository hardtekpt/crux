package com.hardtekpt.crux.data

import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.prefs.GradeScales
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lists a board catalogue or one of your datasets for browsing: its climbs at an angle as
 * [BrowseCandidate]s, with your goes on each, so [BrowseEngine] can filter and sort them the
 * same way for both; and the climbs of a browse as [PlaylistItem]s, for the session's card.
 */
class BoardBrowser @Inject constructor(private val catalogue: CatalogueRepository) {
    /** The angles a catalogue grades at; none for a dataset. */
    suspend fun angles(dataset: Dataset): List<Int> = dataset.catalogueKey?.let { catalogue.angles(it) }.orEmpty()

    /** The angle to start at: [preferred] if the board has it, else 40° (the commonest), else its first. */
    fun startAngle(angles: List<Int>, preferred: Int?): Int? = preferred?.takeIf { it in angles } ?: if (40 in angles) 40 else angles.firstOrNull()

    /** The scale grades are read and filtered in: the climber's boulder scale for a catalogue, the dataset's own otherwise. */
    fun scale(dataset: Dataset, scales: GradeScales): GradeScale? = if (dataset.isCatalogue) catalogueScale(scales) else datasetGradeScale(dataset.climbs)

    suspend fun candidates(
        dataset: Dataset,
        angle: Int?,
        text: String,
        scale: GradeScale?,
        goes: Map<String, Goes>,
        sentKeys: Set<String>,
    ): List<BrowseCandidate> = withContext(Dispatchers.Default) {
        val key = dataset.catalogueKey
        if (key != null) {
            val at = angle ?: return@withContext emptyList()
            catalogue.candidates(key, at, text).map { row ->
                val font = row.difficulty?.let(BoardseshBoards::fontIndex)
                val grade = font?.let { f -> scale?.let { convertGrade(GradeScale.FONT, f, it) } ?: f }
                val mine = goes["c${row.uuid}"] ?: Goes()
                BrowseCandidate(
                    key = "c${row.uuid}",
                    name = row.name,
                    setter = row.setter,
                    grade = grade,
                    gradeLabel = grade?.let { (scale ?: GradeScale.FONT).label(it) },
                    ascents = row.ascents,
                    rating = row.quality,
                    benchmark = row.benchmark == true,
                    created = row.createdAt,
                    goes = mine.goes,
                    sent = mine.sent || "c${row.uuid}" in sentKeys,
                    tonight = mine.tonight,
                )
            }
        } else {
            val words = text.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            dataset.climbs.filter { climb ->
                val all = listOfNotNull(climb.name, climb.setter, climb.note).joinToString(" ").lowercase()
                words.all { it in all }
            }.map { climb ->
                val grade = climb.gradeScale?.takeIf { !it.isLocal }?.let { from -> climb.gradeIndex?.let { i -> scale?.let { convertGrade(from, i, it) } } }
                val mine = goes["d${climb.id}"] ?: Goes()
                BrowseCandidate(
                    key = "d${climb.id}",
                    name = climb.name,
                    setter = climb.setter,
                    discipline = climb.discipline,
                    grade = grade,
                    gradeLabel = grade?.let { scale?.label(it) } ?: climb.gradeLabel,
                    created = climb.createdAtMillis.toString().padStart(15, '0'),
                    goes = mine.goes,
                    sent = mine.sent || climb.isSentIn(sentKeys),
                    tonight = mine.tonight,
                )
            }
        }
    }

    /** The climbs of [keys] in [dataset], in order, as the session's card shows them; ones gone from it are left out. */
    suspend fun items(dataset: Dataset, angle: Int?, keys: List<String>): List<PlaylistItem> {
        val key = dataset.catalogueKey
        return if (key != null) {
            val rows = catalogue.rows(key, angle ?: 40, keys.filter { it.startsWith("c") }.map { it.drop(1) })
            rows.mapIndexed { i, row ->
                val font = row.difficulty?.let(BoardseshBoards::fontIndex)
                val climb = DatasetClimb(
                    name = row.name,
                    discipline = Discipline.BOULDER,
                    gradeScale = GradeScale.FONT.takeIf { font != null },
                    gradeIndex = font,
                    setter = row.setter,
                    catalogueUuid = row.uuid,
                    holds = row.frames,
                )
                PlaylistItem(
                    id = i.toLong(),
                    name = row.name,
                    discipline = Discipline.BOULDER,
                    gradeScale = climb.gradeScale,
                    gradeIndex = font,
                    datasetName = dataset.name,
                    climb = climb,
                    dataset = dataset,
                    position = i,
                    catalogueUuid = row.uuid,
                )
            }
        } else {
            val byId = dataset.climbs.associateBy { "d${it.id}" }
            keys.mapNotNull { byId[it] }.mapIndexed { i, climb ->
                PlaylistItem(
                    id = i.toLong(),
                    name = climb.name,
                    discipline = climb.discipline,
                    gradeScale = climb.gradeScale,
                    gradeIndex = climb.gradeIndex,
                    datasetName = dataset.name,
                    climb = climb,
                    dataset = dataset,
                    position = i,
                )
            }
        }
    }
}

/** The scale a catalogue's grades are read and filtered in: the climber's boulder scale, or Font for a local one. */
fun catalogueScale(scales: GradeScales): GradeScale = scales.boulder.takeIf { !it.isLocal } ?: GradeScale.FONT

/** The scale most of a dataset's graded climbs are in; null when none is graded. */
fun datasetGradeScale(climbs: List<DatasetClimb>): GradeScale? =
    climbs.mapNotNull { it.gradeScale?.takeIf { s -> !s.isLocal && it.gradeIndex != null } }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
