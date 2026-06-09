package com.quakesphere.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tiny ViewModel that polls GitHub once on init (and on demand) and exposes a
 * lazily-populated [UpdateInfo]. The Compose banner only renders when this is
 * non-null and the user hasn't dismissed it for this session.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
    val downloader: UpdateDownloader
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    init {
        check()
    }

    fun check() {
        viewModelScope.launch {
            val info = updateRepository.checkForUpdate()
            _state.value = _state.value.copy(available = info)
        }
    }

    fun dismiss() {
        _state.value = _state.value.copy(dismissed = true)
    }

    fun installNow() {
        val info = _state.value.available ?: return
        downloader.startDownload(info)
    }
}

data class UpdateUiState(
    val available: UpdateInfo? = null,
    val dismissed: Boolean = false
)
