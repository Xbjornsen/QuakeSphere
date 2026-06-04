package com.quakesphere.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quakesphere.domain.model.Earthquake
import com.quakesphere.domain.usecase.GetEarthquakesUseCase
import com.quakesphere.domain.usecase.SyncEarthquakesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ListUiState(
    val earthquakes: List<Earthquake> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val minMagnitude: Double = 5.0,
    val filterOptions: List<Double> = listOf(5.0, 6.0, 7.0)
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EarthquakeListViewModel @Inject constructor(
    private val getEarthquakesUseCase: GetEarthquakesUseCase,
    private val syncEarthquakesUseCase: SyncEarthquakesUseCase
) : ViewModel() {

    private val _minMagnitude = MutableStateFlow(5.0)
    private val _uiState = MutableStateFlow(ListUiState())
    val uiState: StateFlow<ListUiState> = _uiState.asStateFlow()

    init {
        observeEarthquakes()
        refresh()
    }

    private fun observeEarthquakes() {
        viewModelScope.launch {
            _minMagnitude
                .flatMapLatest { mag ->
                    getEarthquakesUseCase(mag)
                }
                .catch { e ->
                    _uiState.value = _uiState.value.copy(errorMessage = e.message)
                }
                .collect { list ->
                    _uiState.value = _uiState.value.copy(
                        earthquakes = list,
                        isLoading = false,
                        isRefreshing = false
                    )
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            syncEarthquakesUseCase(_uiState.value.minMagnitude)
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

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
