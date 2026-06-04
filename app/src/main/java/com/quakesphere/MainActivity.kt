package com.quakesphere

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.quakesphere.ui.navigation.NavGraph
import com.quakesphere.ui.theme.QuakeSphereTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        return when (ev.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER,
            MotionEvent.ACTION_HOVER_MOVE,
            MotionEvent.ACTION_HOVER_EXIT -> false
            else -> super.dispatchGenericMotionEvent(ev)
        }
    }
}
