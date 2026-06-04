package com.quakesphere.ui.globe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quakesphere.domain.model.Earthquake
import com.quakesphere.domain.usecase.GetEarthquakesUseCase
import com.quakesphere.domain.usecase.SyncEarthquakesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GlobeUiState(
    val earthquakes: List<Earthquake> = emptyList(),
    val selectedEarthquake: Earthquake? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val minMagnitude: Double = 5.0
)

@HiltViewModel
class GlobeViewModel @Inject constructor(
    private val getEarthquakesUseCase: GetEarthquakesUseCase,
    private val syncEarthquakesUseCase: SyncEarthquakesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(GlobeUiState())
    val uiState: StateFlow<GlobeUiState> = _uiState.asStateFlow()

    init {
        observeEarthquakes()
        syncEarthquakes()
    }

    private fun observeEarthquakes() {
        viewModelScope.launch {
            getEarthquakesUseCase(_uiState.value.minMagnitude)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(errorMessage = e.message)
                }
                .collect { list ->
                    _uiState.value = _uiState.value.copy(earthquakes = list, isLoading = false)
                }
        }
    }

    fun syncEarthquakes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            syncEarthquakesUseCase(_uiState.value.minMagnitude)
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Sync failed: ${e.message}"
                    )
                }
        }
    }

    fun selectEarthquake(index: Int) {
        val quake = _uiState.value.earthquakes.getOrNull(index)
        _uiState.value = _uiState.value.copy(selectedEarthquake = quake)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedEarthquake = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
