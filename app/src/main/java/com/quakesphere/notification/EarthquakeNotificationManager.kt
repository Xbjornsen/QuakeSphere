package com.quakesphere.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import com.quakesphere.MainActivity
import com.quakesphere.QuakeSphereApp
import com.quakesphere.domain.model.Earthquake
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EarthquakeNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun showEarthquakeNotification(earthquake: Earthquake) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("earthquake_id", earthquake.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            earthquake.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val depthStr = when {
            earthquake.depth < 70.0 -> "Shallow (${earthquake.depth.toInt()} km)"
            earthquake.depth < 300.0 -> "Intermediate (${earthquake.depth.toInt()} km)"
            else -> "Deep (${earthquake.depth.toInt()} km)"
        }

        val notification = NotificationCompat.Builder(context, QuakeSphereApp.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("M${String.format("%.1f", earthquake.mag)} Earthquake Detected")
            .setContentText(earthquake.place)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${earthquake.place}\nDepth: $depthStr\nCoordinates: ${String.format("%.2f", earthquake.lat)}°, ${String.format("%.2f", earthquake.lon)}°")
                    .setBigContentTitle("M${String.format("%.1f", earthquake.mag)} - ${earthquake.magnitudeCategory.label}")
            )
            .setPriority(
                if (earthquake.mag >= 7.0) NotificationCompat.PRIORITY_MAX
                else NotificationCompat.PRIORITY_HIGH
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .apply {
                if (earthquake.tsunami == 1) {
                    addAction(0, "TSUNAMI WARNING", pendingIntent)
                }
            }
            .build()

        notificationManager.notify(earthquake.id.hashCode(), notification)
    }

    fun cancelNotification(earthquakeId: String) {
        notificationManager.cancel(earthquakeId.hashCode())
    }

    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }
}
