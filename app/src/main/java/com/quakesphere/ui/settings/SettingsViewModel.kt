package com.quakesphere.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SyncInterval(val label: String, val minutes: Int) {
    FIFTEEN("15 min", 15),
    THIRTY("30 min", 30),
    SIXTY("1 hr", 60)
}

data class SettingsUiState(
    val minMagnitude: Float = 5.0f,
    val notificationThreshold: Float = 6.0f,
    val notificationsEnabled: Boolean = true,
    val syncInterval: SyncInterval = SyncInterval.THIRTY
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    companion object {
        val KEY_MIN_MAG = floatPreferencesKey("min_magnitude")
        val KEY_NOTIF_THRESHOLD = floatPreferencesKey("notification_threshold")
        val KEY_NOTIF_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_SYNC_INTERVAL = intPreferencesKey("sync_interval_minutes")
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.data
                .catch { emit(emptyPreferences()) }
                .map { prefs ->
                    SettingsUiState(
                        minMagnitude = prefs[KEY_MIN_MAG] ?: 5.0f,
                        notificationThreshold = prefs[KEY_NOTIF_THRESHOLD] ?: 6.0f,
                        notificationsEnabled = prefs[KEY_NOTIF_ENABLED] ?: true,
                        syncInterval = when (prefs[KEY_SYNC_INTERVAL] ?: 30) {
                            15 -> SyncInterval.FIFTEEN
                            60 -> SyncInterval.SIXTY
                            else -> SyncInterval.THIRTY
                        }
                    )
                }
                .collect { settings ->
                    _uiState.value = settings
                }
        }
    }

    fun setMinMagnitude(value: Float) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_MIN_MAG] = value }
        }
    }

    fun setNotificationThreshold(value: Float) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_NOTIF_THRESHOLD] = value }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_NOTIF_ENABLED] = enabled }
        }
    }

    fun setSyncInterval(interval: SyncInterval) {
        viewModelScope.launch {
            dataStore.edit { it[KEY_SYNC_INTERVAL] = interval.minutes }
        }
    }
}
