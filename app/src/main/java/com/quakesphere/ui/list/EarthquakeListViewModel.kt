package com.quakesphere.ui.list

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quakesphere.domain.model.Earthquake
import com.quakesphere.domain.model.EarthquakeSwarm
import com.quakesphere.domain.usecase.DetectSwarmsUseCase
import com.quakesphere.domain.usecase.GetEarthquakesUseCase
import com.quakesphere.domain.usecase.SyncEarthquakesUseCase
import com.quakesphere.ui.settings.DepthFilter
import com.quakesphere.ui.settings.TimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── View modes ────────────────────────────────────────────────────────────────

enum class ListViewMode { CARDS, TABLE, SWARMS }

enum class SortColumn(val label: String) {
    TIME("Time"), MAGNITUDE("Mag"), DEPTH("Depth"), PLACE("Location")
}

// ── UI State ─────────────────────────────────────────────────────────────────

data class ListUiState(
    val earthquakes:   List<Earthquake>      = emptyList(),
    val swarms:        List<EarthquakeSwarm> = emptyList(),
    val isLoading:     Boolean               = false,
    val isRefreshing:  Boolean               = false,
    val errorMessage:  String?               = null,
    val minMagnitude:  Double                = 5.0,
    val filterOptions: List<Double>          = listOf(2.5, 3.0, 4.0, 5.0, 6.0, 7.0),
    val viewMode:      ListViewMode          = ListViewMode.CARDS,
    val sortColumn:    SortColumn            = SortColumn.TIME,
    val sortAscending: Boolean               = false,
    val useMiles:      Boolean               = false
)

// ── ViewModel ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EarthquakeListViewModel @Inject constructor(
    private val getEarthquakesUseCase: GetEarthquakesUseCase,
    private val syncEarthquakesUseCase: SyncEarthquakesUseCase,
    private val detectSwarmsUseCase: DetectSwarmsUseCase,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    companion object {
        val KEY_TIME_RANGE      = stringPreferencesKey("time_range")
        val KEY_DEPTH_FILTER    = stringPreferencesKey("depth_filter")
        val KEY_DISTANCE_UNIT   = stringPreferencesKey("distance_unit")
        val KEY_SWARM_MIN       = intPreferencesKey("swarm_min_events")
    }

    private data class ListSettings(
        val hours: Int,
        val depthFilter: DepthFilter,
        val useMiles: Boolean,
        val swarmMin: Int
    )

    private val _minMagnitude = MutableStateFlow(5.0)
    private val _uiState      = MutableStateFlow(ListUiState())
    val uiState: StateFlow<ListUiState> = _uiState.asStateFlow()

    /** Current time-window in hours, sourced from settings (for manual refresh). */
    @Volatile private var rangeHours = TimeRange.DAYS_7.hours

    // NB: must be declared BEFORE the init block — observeEarthquakes() reads it,
    // and Kotlin runs property initialisers and init blocks in declaration order.
    private val settingsFlow = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            ListSettings(
                hours = TimeRange.values()
                    .firstOrNull { it.name == (prefs[KEY_TIME_RANGE] ?: "DAYS_7") }
                    ?.hours ?: TimeRange.DAYS_7.hours,
                depthFilter = DepthFilter.values()
                    .firstOrNull { it.name == (prefs[KEY_DEPTH_FILTER] ?: "ALL") }
                    ?: DepthFilter.ALL,
                useMiles  = (prefs[KEY_DISTANCE_UNIT] ?: "km") == "miles",
                swarmMin  = prefs[KEY_SWARM_MIN] ?: 3
            )
        }
        .distinctUntilChanged()

    init {
        observeEarthquakes()
        refresh()
    }

    private fun observeEarthquakes() {
        viewModelScope.launch {
            combine(_minMagnitude, settingsFlow) { mag, s -> Pair(mag, s) }
                .flatMapLatest { (mag, s) ->
                    rangeHours = s.hours
                    val sinceTime = System.currentTimeMillis() - s.hours.toLong() * 3_600_000L
                    getEarthquakesUseCase(mag, sinceTime)
                        .map { list -> Pair(list.filter { s.depthFilter.matches(it.depth) }, s) }
                }
                .catch { e -> _uiState.value = _uiState.value.copy(errorMessage = e.message) }
                .collect { (filtered, s) ->
                    val sorted = applySorting(filtered)
                    _uiState.value = _uiState.value.copy(
                        earthquakes  = sorted,
                        isLoading    = false,
                        isRefreshing = false,
                        useMiles     = s.useMiles
                    )
                    detectSwarms(filtered, s.swarmMin)
                }
        }
    }

    private fun detectSwarms(list: List<Earthquake>, minEvents: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val swarms = detectSwarmsUseCase(list, minEvents)
            _uiState.value = _uiState.value.copy(swarms = swarms)
        }
    }

    private fun applySorting(list: List<Earthquake>): List<Earthquake> {
        val s = _uiState.value
        val sorted = when (s.sortColumn) {
            SortColumn.TIME      -> list.sortedBy { it.time }
            SortColumn.MAGNITUDE -> list.sortedBy { it.mag }
            SortColumn.DEPTH     -> list.sortedBy { it.depth }
            SortColumn.PLACE     -> list.sortedBy { it.place }
        }
        return if (s.sortAscending) sorted else sorted.reversed()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            val sinceTime = System.currentTimeMillis() - rangeHours.toLong() * 3_600_000L
            syncEarthquakesUseCase(_uiState.value.minMagnitude, sinceTime)
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        errorMessage = "Sync failed: ${e.message}"
                    )
                }
        }
    }

    fun setMinMagnitude(mag: Double) {
        if (mag == _uiState.value.minMagnitude) return
        _uiState.value = _uiState.value.copy(minMagnitude = mag, isLoading = true)
        _minMagnitude.value = mag
    }

    fun setViewMode(mode: ListViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = mode)
    }

    fun setSortColumn(column: SortColumn) {
        val s = _uiState.value
        val ascending = if (s.sortColumn == column) !s.sortAscending else false
        val sorted = run {
            val base = when (column) {
                SortColumn.TIME      -> s.earthquakes.sortedBy { it.time }
                SortColumn.MAGNITUDE -> s.earthquakes.sortedBy { it.mag }
                SortColumn.DEPTH     -> s.earthquakes.sortedBy { it.depth }
                SortColumn.PLACE     -> s.earthquakes.sortedBy { it.place }
            }
            if (ascending) base else base.reversed()
        }
        _uiState.value = s.copy(sortColumn = column, sortAscending = ascending, earthquakes = sorted)
    }

    fun clearError() { _uiState.value = _uiState.value.copy(errorMessage = null) }
}
