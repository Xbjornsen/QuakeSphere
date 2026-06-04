package com.quakesphere

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.quakesphere.ui.settings.SettingsViewModel
import com.quakesphere.work.EarthquakeSyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class QuakeSphereApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scheduleBackgroundSync()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Earthquake Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for significant earthquake events"
            enableVibration(true)
        }
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun scheduleBackgroundSync() {
        // Read the persisted sync interval (defaults to 30 min) and schedule.
        val minutes = runBlocking {
            dataStore.data.first()[SettingsViewModel.KEY_SYNC_INTERVAL]?.toLong() ?: 30L
        }
        EarthquakeSyncWorker.schedule(this, minutes)
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "earthquake_alerts"
    }
}
