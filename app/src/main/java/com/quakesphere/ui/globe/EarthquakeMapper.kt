package com.quakesphere.ui.globe

import com.quakesphere.domain.model.Earthquake
import com.quakesphere.domain.model.EarthquakeSwarm
import com.quakesphere.globe.GeoCoord
import com.quakesphere.globe.Marker
import com.quakesphere.globe.MarkerStack
import com.quakesphere.globe.RippleSpec

/**
 * Maps QuakeSphere domain types to the renderer-neutral types exposed by
 * the `:globe` library. The renderer itself never sees [Earthquake] or
 * [EarthquakeSwarm] — keeping the library reusable for non-earthquake apps.
 */
object EarthquakeMapper {

    /**
     * Build the flat marker layer.
     *
     * @param earthquakes the current quake list (already time/depth filtered).
     * @param swarmEventIds ids of quakes that belong to a [MarkerStack] — these
     *   are excluded from the flat layer so they only appear on their spine.
     * @param colorByMagnitude when true, marker colour reflects magnitude
     *   bands; otherwise it reflects focal-depth bands.
     */
    fun toMarkers(
        earthquakes: List<Earthquake>,
        swarmEventIds: Set<String>,
        colorByMagnitude: Boolean
    ): List<Marker> = earthquakes
        .filterNot { it.id in swarmEventIds }
        .map { it.toMarker(colorByMagnitude) }

    /** Build the stack (radial-spine) layer from detected swarms. */
    fun toStacks(
        swarms: List<EarthquakeSwarm>,
        colorByMagnitude: Boolean
    ): List<MarkerStack> = swarms.map { swarm ->
        MarkerStack(
            id = swarm.id,
            centre = GeoCoord(swarm.centerLat, swarm.centerLon),
            markers = swarm.events.map { it.toMarker(colorByMagnitude) }
        )
    }

    /** Build the ripple layer from tsunami-flagged quakes. */
    fun toRipples(earthquakes: List<Earthquake>): List<RippleSpec> = earthquakes
        .filter { it.tsunami == 1 }
        .map {
            RippleSpec(
                id = "tsunami_${it.id}",
                centre = GeoCoord(it.lat, it.lon),
                color = 0xC033B5FF.toInt()   // bright cyan-blue with ~75% peak alpha
            )
        }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private fun Earthquake.toMarker(colorByMagnitude: Boolean): Marker = Marker(
        id = id,
        coord = GeoCoord(lat, lon),
        color = if (colorByMagnitude) colorForMagnitude(mag) else colorForDepth(depth),
        sizeHint = ((mag - 3.0) / 2.5).coerceIn(0.6, 2.8).toFloat()
    )

    private fun colorForMagnitude(mag: Double): Int = when {
        mag < 5.0 -> 0xFF4DAF51.toInt()   // green
        mag < 6.0 -> 0xFFFFEB3B.toInt()   // yellow
        mag < 7.0 -> 0xFFFF9800.toInt()   // orange
        mag < 8.0 -> 0xFFFF5722.toInt()   // deep-orange
        else      -> 0xFFFF1744.toInt()   // red
    }

    private fun colorForDepth(depth: Double): Int = when {
        depth <  70.0 -> 0xFFFF4444.toInt()   // red    – shallow
        depth < 300.0 -> 0xFFFF8800.toInt()   // orange – mid
        else          -> 0xFF4488FF.toInt()   // blue   – deep
    }
}
