package com.quakesphere

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.quakesphere.ui.navigation.NavGraph
import com.quakesphere.ui.theme.QuakeSphereTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Android 13+ requires the user to grant POST_NOTIFICATIONS at runtime —
    // without this, the EarthquakeSyncWorker silently skips every notify
    // because the permission check fails. Result on the user's side: settings
    // say "notify above M6" but the M7.8 they expect never appears.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Result ignored — if the user denies we simply won't notify. The
            // worker tolerates the missing permission gracefully.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            QuakeSphereTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    NavGraph()
                }
            }
        }
    }

    /**
     * Swallow hover/pointer events before they reach Compose.
     *
     * Compose UI 1.6.1 (our current BOM) crashes with
     * "IllegalStateException: The ACTION_HOVER_EXIT event was not cleared"
     * when an embedded AndroidView (the OpenGL globe) receives hover events
     * from devices with a hover-capable digitizer (e.g. OnePlus 15).
     * See https://issuetracker.google.com/issues/319881002 (fixed in 1.6.4+).
     *
     * Hover has no function in this touch-driven app, so dropping these events
     * is safe and avoids the upstream bug entirely.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val already = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!already) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        return when (ev.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER,
            MotionEvent.ACTION_HOVER_MOVE,
            MotionEvent.ACTION_HOVER_EXIT -> false
            else -> super.dispatchGenericMotionEvent(ev)
        }
    }
}
