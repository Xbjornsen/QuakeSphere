package com.quakesphere.globe

/**
 * A single point of interest rendered on the globe as a glowing billboarded dot.
 *
 * @property id Stable identifier used for tap callbacks and diffing.
 * @property coord Geographic location.
 * @property color Packed ARGB integer (e.g. `0xFFFF4444.toInt()`).
 * @property sizeHint Relative size, typically `0.5f`–`2.0f`. The library scales
 *   this against an internal base radius so consumer code doesn't need to know
 *   the GL scale.
 * @property pulsing When true, the marker pulses softly (useful for "selected"
 *   or "alert" state). Defaults to false.
 */
data class Marker(
    val id: String,
    val coord: GeoCoord,
    val color: Int,
    val sizeHint: Float = 1.0f,
    val pulsing: Boolean = false
)
