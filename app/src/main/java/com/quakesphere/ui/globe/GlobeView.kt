package com.quakesphere.ui.globe

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector

class GlobeView(context: Context) : GLSurfaceView(context) {

    val renderer: GlobeRenderer = GlobeRenderer()
    var onEarthquakeTapped: ((Int) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            renderer.setZoom(detector.scaleFactor)
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            renderer.setRotation(-distanceX, -distanceY)
            requestRender()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val normalizedX = (2.0f * e.x / width) - 1.0f
            val normalizedY = 1.0f - (2.0f * e.y / height)
            queueEvent {
                val index = renderer.handleTap(normalizedX, normalizedY)
                if (index >= 0) {
                    post { onEarthquakeTapped?.invoke(index) }
                }
            }
            return true
        }

        override fun onDown(e: MotionEvent): Boolean = true
    })

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) {
            gestureDetector.onTouchEvent(event)
        }
        return true
    }
}
