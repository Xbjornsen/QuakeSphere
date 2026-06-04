package com.quakesphere.work

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.quakesphere.domain.repository.EarthquakeRepository
import com.quakesphere.notification.EarthquakeNotificationManager
import com.quakesphere.ui.settings.SettingsViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

@HiltWorker
class EarthquakeSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: EarthquakeRepository,
    private val notificationManager: EarthquakeNotificationManager,
    private val dataStore: DataStore<Preferences>
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            // Load settings
            val prefs = dataStore.data.firstOrNull()
            val minMag = prefs?.get(SettingsViewModel.KEY_MIN_MAG)?.toDouble() ?: 5.0
            val notifThreshold = prefs?.get(SettingsViewModel.KEY_NOTIF_THRESHOLD)?.toDouble() ?: 6.0
            val notificationsEnabled = prefs?.get(SettingsViewModel.KEY_NOTIF_ENABLED) ?: true

            // Fetch latest earthquakes
            val syncResult = repository.syncEarthquakes(minMag)

            if (syncResult.isSuccess && notificationsEnabled) {
                // Check if we have notification permission
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    // Get all earthquakes above notification threshold
                    val latestQuakes = repository.getEarthquakes(notifThreshold)
                        .map { list ->
                            // Only notify for earthquakes in the last sync window (30 min)
                            val cutoffTime = System.currentTimeMillis() - 30 * 60 * 1000L
                            list.filter { it.time > cutoffTime }
                        }
                        .firstOrNull() ?: emptyList()

                    // Notify for significant quakes
                    latestQuakes.forEach { quake ->
                        if (quake.mag >= notifThreshold) {
                            notificationManager.showEarthquakeNotification(quake)
                        }
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "earthquake_sync_work"
    }
}
