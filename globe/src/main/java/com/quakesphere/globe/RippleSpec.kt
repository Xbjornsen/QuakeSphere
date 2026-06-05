package com.quakesphere.globe

/**
 * An animated set of expanding concentric rings drawn on the globe surface
 * around a centre point. Useful for highlighting alerts, broadcasts, or
 * tsunami-style propagation visuals.
 *
 * The library handles the animation phase, ring count, and additive blending
 * internally; callers just supply position and colour.
 *
 * @property id Stable identifier.
 * @property centre Where the ripples emanate from.
 * @property color Packed ARGB. Alpha is used as a peak intensity multiplier.
 */
data class RippleSpec(
    val id: String,
    val centre: GeoCoord,
    val color: Int = 0xFF33B5FF.toInt()
)
