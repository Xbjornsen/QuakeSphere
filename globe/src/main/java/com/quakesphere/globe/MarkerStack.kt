package com.quakesphere.globe

/**
 * A group of markers rendered stacked vertically on a radial spine above
 * a shared epicentre — useful for visualising clusters where individual
 * markers would overlap on the globe surface.
 *
 * The first entry in [markers] is drawn closest to the surface; subsequent
 * entries are spaced outward along the spine. Callers typically pass the
 * group already sorted by importance (e.g. magnitude descending).
 *
 * @property id Stable identifier for the cluster.
 * @property centre Shared epicentre on the globe.
 * @property markers Members of the cluster, in stack order (bottom → top).
 */
data class MarkerStack(
    val id: String,
    val centre: GeoCoord,
    val markers: List<Marker>
)
