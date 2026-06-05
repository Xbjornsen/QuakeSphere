package com.quakesphere.globe

/**
 * Cosmetic / display toggles for the globe. Pure data — pass to
 * [GlobeView.displaySettings] whenever any of these change.
 *
 * Defaults give a "Pacific-rim-quake-tracker style" look:
 * continents outlined, stars on, no auto-rotation.
 */
data class GlobeDisplaySettings(
    val showContinentLines: Boolean = true,
    val showStars:          Boolean = true,
    val autoRotate:         Boolean = false
)
