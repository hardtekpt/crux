package com.hardtekpt.crux.ui.datasets

import com.hardtekpt.crux.data.BoardBrowser
import com.hardtekpt.crux.data.BrowseCandidate
import com.hardtekpt.crux.data.BrowseEngine
import com.hardtekpt.crux.data.BrowseQuery
import com.hardtekpt.crux.data.BrowseSettings
import com.hardtekpt.crux.data.BrowseStore
import com.hardtekpt.crux.data.Dataset
import com.hardtekpt.crux.data.PlaylistRepository
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A browse as a page shows it: the bar's state, the climbs that match, and whether they're still coming. */
data class BrowseUi(
    val dataset: Dataset? = null,
    val bar: BrowseBarState = BrowseBarState(),
    val text: String = "",
    val climbs: List<BrowseCandidate> = emptyList(),
    val loading: Boolean = true,
)

/**
 * Browsing one board or dataset, shared by the session's browse sheet and the catalogue and
 * dataset pages: its angles, the set (remembered per board, with its views), the search, and the
 * climbs that match, recomputed as any of them change. [sessionId] counts its goes as tonight's.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class BrowseModel(
    private val scope: CoroutineScope,
    private val browser: BoardBrowser,
    private val store: BrowseStore,
    playlists: PlaylistRepository,
    preferences: UserPreferencesRepository,
    sessionId: Long? = null,
) {
    private val dataset = MutableStateFlow<Dataset?>(null)
    private val query = MutableStateFlow(BrowseQuery())
    private val text = MutableStateFlow("")
    private val angles = MutableStateFlow<List<Int>>(emptyList())
    private val settings = dataset.flatMapLatest { ds -> ds?.let { store.observeSettings(it.id) } ?: flowOf(BrowseSettings()) }
    private val scales = preferences.gradeScales

    private data class Inputs(val dataset: Dataset?, val angle: Int?, val text: String)

    /**
     * Everything at this angle and search, before the filters and without your goes; reloaded
     * only when those change (a big catalogue takes a few seconds), never for a go logged.
     */
    private val candidates: StateFlow<Pair<Inputs, List<BrowseCandidate>>?> = combine(
        dataset,
        query.map { it.filters.angle }.distinctUntilChanged(),
        // Typing waits for a pause; an empty search (the start, or cleared) goes at once.
        text.debounce { if (it.isEmpty()) 0L else 150L },
    ) { ds, angle, t -> Inputs(ds, angle, t) }
        .distinctUntilChanged()
        .mapLatest { inputs ->
            val ds = inputs.dataset ?: return@mapLatest null
            if (ds.isCatalogue && inputs.angle == null) return@mapLatest inputs to emptyList()
            inputs to browser.candidates(ds, inputs.angle, inputs.text, browser.scale(ds, scales.first()), emptyMap(), emptySet())
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** The candidates with your goes on them, redone (cheaply) as you log. */
    private val withGoes = combine(candidates, playlists.observeGoes(sessionId), playlists.observeSent()) { found, goes, sent ->
        found?.let { (inputs, list) ->
            inputs to list.map { c ->
                val g = goes[c.key]
                val name = "n${c.discipline.name}:${c.name.trim().lowercase()}"
                val sentHere = c.key in sent || (c.key.startsWith("d") && name in sent)
                if (g == null && !sentHere) c else c.copy(goes = g?.goes ?: 0, sent = (g?.sent ?: false) || sentHere, tonight = g?.tonight ?: 0)
            }
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)

    val ui: StateFlow<BrowseUi> = combine(withGoes, query, settings, text, angles) { found, q, s, t, angleList ->
        val ds = dataset.value
        val all = found?.second.orEmpty()
        val shown = BrowseEngine.apply(all, q)
        val scale = ds?.let { browser.scale(it, scales.first()) }
        BrowseUi(
            dataset = ds,
            bar = BrowseBarState(
                query = q,
                catalogue = ds?.isCatalogue == true,
                angles = angleList,
                scale = scale,
                gradeCounts = BrowseEngine.gradeCounts(all),
                setters = BrowseEngine.setters(all),
                maxSent = BrowseEngine.maxSent(all),
                views = s.views,
                shown = shown.size,
                defaultSorts = if (ds?.isCatalogue == false) BrowseQuery.DATASET_SORTS else BrowseQuery.DEFAULT_SORTS,
            ),
            text = t,
            climbs = shown,
            loading = found == null || found.first.dataset?.id != ds?.id || found.first.angle != q.filters.angle,
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, BrowseUi())

    val actions = BrowseBarActions(onQuery = ::setQuery, onSaveView = ::saveView, onDeleteView = ::deleteView)

    /** Opens [ds]: its angles, and the set it was last left with (or the defaults, at [preferredAngle]). */
    fun open(ds: Dataset, preferredAngle: Int? = null) {
        if (dataset.value?.id == ds.id && dataset.value == ds) return
        val same = dataset.value?.id == ds.id
        dataset.value = ds
        if (same) return
        scope.launch {
            val list = browser.angles(ds)
            angles.value = list
            val saved = store.settings(ds.id).query
            val scale = browser.scale(ds, scales.first())
            val base = saved ?: BrowseQuery(sorts = if (ds.isCatalogue) BrowseQuery.DEFAULT_SORTS else BrowseQuery.DATASET_SORTS)
            val angle = browser.startAngle(list, preferredAngle ?: base.filters.angle)
            // A grade range saved in another scale is dropped rather than misread.
            val filters = base.filters.let { if (it.gradeScale != null && it.gradeScale != scale) it.copy(gradeFrom = null, gradeTo = null) else it }
            query.value = base.copy(filters = filters.copy(angle = angle, gradeScale = scale))
        }
    }

    fun setText(value: String) {
        text.value = value.take(60)
    }

    fun setQuery(q: BrowseQuery) {
        query.value = q
        dataset.value?.let { ds -> scope.launch { store.saveQuery(ds.id, q) } }
    }

    fun clear() = setQuery(query.value.copy(filters = query.value.filters.cleared(), view = null))

    private fun saveView(name: String) {
        val ds = dataset.value ?: return
        scope.launch {
            store.saveView(ds.id, name, query.value)
            query.value = query.value.copy(view = name.trim())
        }
    }

    private fun deleteView(name: String) {
        val ds = dataset.value ?: return
        scope.launch {
            store.deleteView(ds.id, name)
            if (query.value.view == name) query.value = query.value.copy(view = null)
        }
    }
}
