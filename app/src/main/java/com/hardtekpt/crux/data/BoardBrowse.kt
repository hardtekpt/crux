package com.hardtekpt.crux.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where you stand with a climb: never tried, tried and not sent, or sent. */
@Serializable
enum class YouState(val label: String) { NEW("New"), TRIED("Tried"), SENT("Sent") }

/** What a list can be sorted by; each has a direction, and the first one decides. */
@Serializable
enum class SortKey(val label: String, val descendingFirst: Boolean, val upLabel: String, val downLabel: String, val catalogueOnly: Boolean = false) {
    GRADE("Grade", false, "Easiest first", "Hardest first"),
    POPULAR("Popular", true, "Fewest ascents first", "Most ascents first", catalogueOnly = true),
    RATING("Rating", true, "Lowest rated first", "Best rated first", catalogueOnly = true),
    GOES("Your goes", true, "Fewest goes first", "Most goes first"),
    NEWEST("Newest", true, "Oldest first", "Newest first"),
    NAME("Name", false, "A to Z", "Z to A"),
    SETTER("Setter", false, "A to Z", "Z to A"),
}

@Serializable
data class SortLine(val key: SortKey, val descending: Boolean = key.descendingFirst) {
    val description: String get() = if (descending) key.downLabel else key.upLabel
}

/**
 * The filters on a board or dataset: they add up, so a climb shows only when it matches every one.
 * Grades are indices in [gradeScale], the scale the page reads grades in.
 */
@Serializable
data class BrowseFilters(
    val angle: Int? = null,
    val gradeScale: GradeScale? = null,
    val gradeFrom: Int? = null,
    val gradeTo: Int? = null,
    /** Empty is anyone's; New and Tried together read "Not sent". */
    val you: Set<YouState> = emptySet(),
    val minRating: Double? = null,
    val minAscents: Int? = null,
    val setters: Set<String> = emptySet(),
    val benchmarks: Boolean = false,
) {
    /** How many filters are set, angle aside (it always has a value). */
    val count: Int get() = listOf(
        gradeFrom != null || gradeTo != null,
        you.isNotEmpty() && you.size < 3,
        minRating != null,
        minAscents != null,
        setters.isNotEmpty(),
        benchmarks,
    ).count {
        it
    }

    /** Clear all: everything off, the angle kept. */
    fun cleared(): BrowseFilters = BrowseFilters(angle = angle, gradeScale = gradeScale)
}

/** A whole set: filters and the chain of sorts, as a view saves it. */
@Serializable
data class BrowseQuery(
    val filters: BrowseFilters = BrowseFilters(),
    val sorts: List<SortLine> = DEFAULT_SORTS,
    /** The view this came from, while it still matches it. */
    val view: String? = null,
) {
    companion object {
        /** Popular, then Grade: as the catalogue page always listed them. */
        val DEFAULT_SORTS = listOf(SortLine(SortKey.POPULAR), SortLine(SortKey.GRADE))

        /** Datasets have no ascents: easiest first, then name. */
        val DATASET_SORTS = listOf(SortLine(SortKey.GRADE), SortLine(SortKey.NAME))
    }
}

@Serializable
data class BrowseView(val name: String, val query: BrowseQuery)

/** What a board or dataset remembers between visits: the last set, and the views saved on it. */
@Serializable
data class BrowseSettings(val query: BrowseQuery? = null, val views: List<BrowseView> = emptyList())

/** "You": "Not sent", "Sent", "New, Tried"; null when it shows everyone's. */
fun Set<YouState>.label(): String? = when {
    isEmpty() || size == YouState.entries.size -> null
    this == setOf(YouState.NEW, YouState.TRIED) -> "Not sent"
    else -> YouState.entries.filter { it in this }.joinToString(", ") { it.label }
}

/** A sort chain as its chip reads it: "Grade, Popular". */
fun List<SortLine>.label(): String = joinToString(", ") { it.key.label }

/**
 * One climb a list can show, from a catalogue or a dataset, with what the filters and sorts
 * need: its grade as an index in the page's scale, its numbers, and your goes on it.
 */
data class BrowseCandidate(
    /** The climb's key for your goes: "c<uuid>" for a catalogue climb, "d<id>" for a dataset one. */
    val key: String,
    val name: String,
    val setter: String?,
    val discipline: Discipline = Discipline.BOULDER,
    val grade: Int? = null,
    val gradeLabel: String? = null,
    val ascents: Int? = null,
    val rating: Double? = null,
    val benchmark: Boolean = false,
    /** For Newest: an ISO date or millis as text, compared as text. */
    val created: String? = null,
    val goes: Int = 0,
    val sent: Boolean = false,
    /** Goes in the running session. */
    val tonight: Int = 0,
) {
    val you: YouState get() = when {
        sent -> YouState.SENT
        goes > 0 -> YouState.TRIED
        else -> YouState.NEW
    }
}

/** Your goes on a climb, all time and in one session. */
data class Goes(val goes: Int = 0, val sent: Boolean = false, val tonight: Int = 0)

object BrowseEngine {
    /** Keeps the climbs that match every filter but the angle (the source applies that), sorted by the chain. */
    fun apply(candidates: List<BrowseCandidate>, query: BrowseQuery): List<BrowseCandidate> {
        val f = query.filters
        val kept = candidates.filter { c ->
            (f.gradeFrom == null || (c.grade != null && c.grade >= f.gradeFrom)) &&
                (f.gradeTo == null || (c.grade != null && c.grade <= f.gradeTo)) &&
                (f.you.isEmpty() || c.you in f.you) &&
                (f.minRating == null || (c.rating ?: 0.0) >= f.minRating) &&
                (f.minAscents == null || (c.ascents ?: 0) >= f.minAscents) &&
                (f.setters.isEmpty() || c.setter in f.setters) &&
                (!f.benchmarks || c.benchmark)
        }
        return kept.sortedWith(comparator(query.sorts))
    }

    /** The chain as one comparator; ties fall to the next line, then to the name. Missing values go last. */
    fun comparator(sorts: List<SortLine>): Comparator<BrowseCandidate> {
        val lines = sorts.map { line ->
            val base: Comparator<BrowseCandidate> = when (line.key) {
                SortKey.GRADE -> nullsLast(line.descending) { it.grade }
                SortKey.POPULAR -> nullsLast(line.descending) { it.ascents }
                SortKey.RATING -> nullsLast(line.descending) { it.rating }
                SortKey.GOES -> nullsLast(line.descending) { it.goes }
                SortKey.NEWEST -> nullsLast(line.descending) { it.created }
                SortKey.NAME -> nullsLast(line.descending) { it.name.lowercase() }
                SortKey.SETTER -> nullsLast(line.descending) { it.setter?.lowercase() }
            }
            base
        }
        val none: Comparator<BrowseCandidate> = Comparator { _, _ -> 0 }
        val byName: Comparator<BrowseCandidate> = compareBy { it.name.lowercase() }
        return lines.fold(none) { acc, c -> acc.then(c) }.then(byName)
    }

    private fun <T : Comparable<T>> nullsLast(descending: Boolean, value: (BrowseCandidate) -> T?): Comparator<BrowseCandidate> = Comparator { a, b ->
        val x = value(a)
        val y = value(b)
        when {
            x == null && y == null -> 0
            x == null -> 1
            y == null -> -1
            descending -> y.compareTo(x)
            else -> x.compareTo(y)
        }
    }

    /** How many climbs each grade has among [candidates], for the grade grid's bars. */
    fun gradeCounts(candidates: List<BrowseCandidate>): Map<Int, Int> = candidates.mapNotNull { it.grade }.groupingBy { it }.eachCount()

    /** Setters with climbs in [candidates], most climbs first. */
    fun setters(candidates: List<BrowseCandidate>): List<Pair<String, Int>> =
        candidates.mapNotNull { it.setter?.takeIf { s -> s.isNotBlank() } }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map {
            it.key to
                it.value
        }

    /** Your hardest send among [candidates], as a grade index. */
    fun maxSent(candidates: List<BrowseCandidate>): Int? = candidates.filter { it.sent }.mapNotNull { it.grade }.maxOrNull()
}

/** A grade preset on the grade sheet. */
enum class GradePreset(val label: String) {
    NEAR_MAX("Near my max"),
    WARM_UP("Warm-up"),
    BELOW_MAX("Below my max"),
    ;

    /** The range from your hardest send [max] in a scale of [size] grades. */
    fun range(max: Int, size: Int): IntRange = when (this) {
        NEAR_MAX -> (max - 1).coerceAtLeast(0)..(max + 1).coerceAtMost(size - 1)
        WARM_UP -> (max - 4).coerceAtLeast(0)..(max - 2).coerceAtLeast(0)
        BELOW_MAX -> 0..(max - 1).coerceAtLeast(0)
    }
}

/**
 * Browsing a board in the running session: which board (a dataset id), at what angle, under
 * which view, the climbs in the order they were listed when picked (it holds still while you
 * climb), and the one that's up.
 */
@Serializable
data class BrowseRun(
    val sessionId: Long,
    val datasetId: Long,
    val angle: Int? = null,
    val view: String? = null,
    /** The climbs' keys ("c<uuid>", "d<id>"), in order: a window of the list around the one picked. */
    val keys: List<String> = emptyList(),
    val index: Int = 0,
    /** Where [keys] start in the whole list, and how long that is, for "2 of 31". */
    val offset: Int = 0,
    val total: Int = keys.size,
) {
    val size: Int get() = keys.size

    companion object {
        /** How many climbs a browse keeps to walk with Prev and Next; a big board's list is cut to these. */
        const val WINDOW = 300

        /** A run at [picked] in [all], keeping [WINDOW] climbs from a few before it. */
        fun at(sessionId: Long, datasetId: Long, angle: Int?, view: String?, all: List<String>, picked: Int): BrowseRun {
            val start = (picked - 20).coerceAtLeast(0)
            val keys = all.subList(start, (start + WINDOW).coerceAtMost(all.size)).toList()
            return BrowseRun(sessionId, datasetId, angle, view, keys, picked - start, start, all.size)
        }
    }
}

/**
 * Board browsing, kept on the phone with the other small settings: each board's last filters,
 * sort and views, the board last browsed, and the browse running in a session.
 */
@Singleton
class BrowseStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun settingsKey(datasetId: Long) = stringPreferencesKey("browse_settings_$datasetId")

    fun observeSettings(datasetId: Long): Flow<BrowseSettings> = dataStore.data.map { prefs ->
        prefs[settingsKey(datasetId)]?.let { runCatching { json.decodeFromString<BrowseSettings>(it) }.getOrNull() } ?: BrowseSettings()
    }

    suspend fun settings(datasetId: Long): BrowseSettings = observeSettings(datasetId).first()

    suspend fun saveQuery(datasetId: Long, query: BrowseQuery) = edit(datasetId) { it.copy(query = query) }

    /** Saves the set under [name], replacing a view of that name. */
    suspend fun saveView(datasetId: Long, name: String, query: BrowseQuery) = edit(datasetId) { s ->
        val view = BrowseView(name.trim(), query.copy(view = null))
        s.copy(views = s.views.filterNot { it.name.equals(view.name, ignoreCase = true) } + view, query = query.copy(view = view.name))
    }

    suspend fun deleteView(datasetId: Long, name: String) = edit(datasetId) { s ->
        s.copy(views = s.views.filterNot { it.name == name }, query = s.query?.let { if (it.view == name) it.copy(view = null) else it })
    }

    private suspend fun edit(datasetId: Long, change: (BrowseSettings) -> BrowseSettings) {
        dataStore.edit { prefs ->
            val now = prefs[settingsKey(datasetId)]?.let { runCatching { json.decodeFromString<BrowseSettings>(it) }.getOrNull() } ?: BrowseSettings()
            prefs[settingsKey(datasetId)] = json.encodeToString(change(now))
        }
    }

    /** The boards browsed, most recent first. */
    val recent: Flow<List<Long>> = dataStore.data.map { prefs -> prefs[RECENT]?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty() }

    suspend fun used(datasetId: Long) {
        dataStore.edit { prefs ->
            val list = prefs[RECENT]?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty()
            prefs[RECENT] = (listOf(datasetId) + list.filter { it != datasetId }).take(12).joinToString(",")
        }
    }

    val lastUsed: Flow<Long?> = recent.map { it.firstOrNull() }

    /** The browse running in a session; a run left from another session doesn't count. */
    fun observeRun(sessionId: Long?): Flow<BrowseRun?> = dataStore.data.map { prefs ->
        prefs[RUN]?.let { runCatching { json.decodeFromString<BrowseRun>(it) }.getOrNull() }?.takeIf { sessionId != null && it.sessionId == sessionId }
    }

    suspend fun run(sessionId: Long): BrowseRun? = observeRun(sessionId).first()

    suspend fun setRun(run: BrowseRun?) {
        dataStore.edit { prefs -> if (run == null) prefs.remove(RUN) else prefs[RUN] = json.encodeToString(run) }
    }

    suspend fun move(sessionId: Long, index: Int) {
        val run = run(sessionId) ?: return
        setRun(run.copy(index = index.coerceIn(0, (run.size - 1).coerceAtLeast(0))))
    }

    private companion object {
        val RECENT = stringPreferencesKey("browse_recent")
        val RUN = stringPreferencesKey("browse_run")
    }
}
