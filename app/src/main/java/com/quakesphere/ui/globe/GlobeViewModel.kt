package com.quakesphere.ui.globe

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GlobeDisplaySettings(
    val showContinentLines:     Boolean = true,
    val showStars:              Boolean = true,
    val autoRotate:             Boolean = false,
    val markerColorByMagnitude: Boolean = false,
    val useMiles:               Boolean = false
)

data class GlobeUiState(
    val earthquakes:     List<Earthquake>      = emptyList(),
    val swarms:          List<EarthquakeSwarm> = emptyList(),
    val selectedEarthquake: Earthquake?        = null,
    val isLoading:       Boolean               = false,
    val errorMessage:    String?               = null,
    val minMagnitude:    Double                = 5.0,
    val displaySettings: GlobeDisplaySettings  = GlobeDisplaySettings()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class GlobeViewModel @Inject constructor(
    private val getEarthquakesUseCase: GetEarthquakesUseCase,
    private val syncEarthquakesUseCase: SyncEarthquakesUseCase,
    private val detectSwarmsUseCase: DetectSwarmsUseCase,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    companion object {
        val KEY_MIN_MAG              = floatPreferencesKey("min_magnitude")
        val KEY_SHOW_CONTINENT_LINES = booleanPreferencesKey("show_continent_lines")
        val KEY_SHOW_STARS           = booleanPreferencesKey("show_stars")
        val KEY_AUTO_ROTATE          = booleanPreferencesKey("auto_rotate")
        val KEY_MARKER_COLOR_MODE    = stringPreferencesKey("marker_color_mode")
        val KEY_SWARM_MIN_EVENTS     = androidx.datastore.preferences.core.intPreferencesKey("swarm_min_events")
        val KEY_TIME_RANGE           = stringPreferencesKey("time_range")
        val KEY_DEPTH_FILTER         = stringPreferencesKey("depth_filter")
        val KEY_DISTANCE_UNIT        = stringPreferencesKey("distance_unit")
    }

    private data class GlobeParams(
        val minMag: Double,
        val sinceTime: Long,
        val depthFilter: DepthFilter,
        val display: GlobeDisplaySettings,
        val swarmMin: Int
    )

    /** Current time-window in hours — used when issuing a manual sync. */
    @Volatile private var rangeHours = TimeRange.DAYS_7.hours

    private val _uiState = MutableStateFlow(GlobeUiState())
    val uiState: StateFlow<GlobeUiState> = _uiState.asStateFlow()

    init {
        observeSettingsAndEarthquakes()
        syncEarthquakes()
    }

    private fun observeSettingsAndEarthquakes() {
        viewModelScope.launch {
            dataStore.data
                .catch { emit(emptyPreferences()) }
                .map { prefs ->
                    val hours = TimeRange.values()
                        .firstOrNull { it.name == (prefs[KEY_TIME_RANGE] ?: "DAYS_7") }
                        ?.hours ?: TimeRange.DAYS_7.hours
                    rangeHours = hours
                    GlobeParams(
                        minMag      = (prefs[KEY_MIN_MAG] ?: 5.0f).toDouble(),
                        sinceTime   = System.currentTimeMillis() - hours.toLong() * 3_600_000L,
                        depthFilter = DepthFilter.values()
                            .firstOrNull { it.name == (prefs[KEY_DEPTH_FILTER] ?: "ALL") }
                            ?: DepthFilter.ALL,
                        display     = GlobeDisplaySettings(
                            showContinentLines     = prefs[KEY_SHOW_CONTINENT_LINES] ?: true,
                            showStars              = prefs[KEY_SHOW_STARS] ?: true,
                            autoRotate             = prefs[KEY_AUTO_ROTATE] ?: false,
                            markerColorByMagnitude = (prefs[KEY_MARKER_COLOR_MODE] ?: "depth") == "magnitude",
                            useMiles               = (prefs[KEY_DISTANCE_UNIT] ?: "km") == "miles"
                        ),
                        swarmMin    = prefs[KEY_SWARM_MIN_EVENTS] ?: 3
                    )
                }
                .flatMapLatest { params ->
                    _uiState.value = _uiState.value.copy(
                        minMagnitude    = params.minMag,
                        displaySettings = params.display
                    )
                    getEarthquakesUseCase(params.minMag, params.sinceTime)
                        .map { list ->
                            val filtered = list.filter { params.depthFilter.matches(it.depth) }
                            Pair(filtered, params.swarmMin)
                        }
                }
                .catch { e -> _uiState.value = _uiState.value.copy(errorMessage = e.message) }
                .collect { (list, swarmMin) ->
                    _uiState.value = _uiState.value.copy(earthquakes = list, isLoading = false)
                    detectSwarms(list, swarmMin)
                }
        }
    }

    private fun detectSwarms(list: List<Earthquake>, minEvents: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val swarms = detectSwarmsUseCase(list, minEvents)
            _uiState.value = _uiState.value.copy(swarms = swarms)
        }
    }

    fun syncEarthquakes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val sinceTime = System.currentTimeMillis() - rangeHours.toLong() * 3_600_000L
            syncEarthquakesUseCase(_uiState.value.minMagnitude, sinceTime)
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading    = false,
                        errorMessage = "Sync failed: ${e.message}"
                    )
                }
        }
    }

    fun selectEarthquake(index: Int) {
        _uiState.value = _uiState.value.copy(
            selectedEarthquake = _uiState.value.earthquakes.getOrNull(index)
        )
    }

    /**
     * Select by stable id. The `:globe` library only knows marker ids
     * (it doesn't carry our domain types) so taps come back as ids.
     */
    fun selectEarthquakeById(id: String) {
        _uiState.value = _uiState.value.copy(
            selectedEarthquake = _uiState.value.earthquakes.firstOrNull { it.id == id }
        )
    }

    fun clearSelection() { _uiState.value = _uiState.value.copy(selectedEarthquake = null) }
    fun clearError()     { _uiState.value = _uiState.value.copy(errorMessage = null) }
}
