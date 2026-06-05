package com.quakesphere.globe

/**
 * An animated set of expanding concentric rings drawn on the globe surface
 * around a centre point. Useful for highlighting alerts, broadcasts, or
 * tsunami-style propagation visuals.
 *
 * All animation timing happens inside the renderer — callers just supply
 * position, colour, and the three shape parameters below if they want a
 * non-default look.
 *
 * @property id Stable identifier (used for diffing across frames).
 * @property centre Where the ripples emanate from.
 * @property color Packed ARGB. Alpha is the peak per-ring intensity at the
 *   moment a ring is born; it fades to zero as the ring expands outward.
 * @property ringCount How many concurrent rings to render. Larger numbers
 *   make the effect feel more energetic; small numbers (1–2) look like a
 *   quiet "heartbeat". Clamped to 1..12 internally.
 * @property speed Animation speed multiplier. 1.0 = default. Roughly how
 *   many full ring cycles per ~3 seconds at 60 fps.
 * @property maxRadius Maximum ring radius in globe units (the sphere is
 *   radius 1.0). ~0.22 reaches roughly 1400 km on a real Earth scale.
 */
data class RippleSpec(
    val id: String,
    val centre: GeoCoord,
    val color: Int = 0xFF33B5FF.toInt(),
    val ringCount: Int = 4,
    val speed: Float = 1.0f,
    val maxRadius: Float = 0.22f
)
