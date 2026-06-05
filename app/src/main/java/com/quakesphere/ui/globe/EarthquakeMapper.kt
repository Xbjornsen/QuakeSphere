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
    ): List<Marker> {
        // The most-recent quake gets the `pulsing` flag and a bigger size hint
        // so it stands out from the sea of background markers. Picked by
        // timestamp across the full list (incl. swarm members) so "latest"
        // genuinely means latest in time, even if it ends up on a swarm spine.
        val latestId = earthquakes.maxByOrNull { it.time }?.id
        return earthquakes
            .filterNot { it.id in swarmEventIds }
            .map { it.toMarker(colorByMagnitude, isLatest = it.id == latestId) }
    }

    /** Build the stack (radial-spine) layer from detected swarms. */
    fun toStacks(
        swarms: List<EarthquakeSwarm>,
        colorByMagnitude: Boolean,
        latestId: String? = swarms.flatMap { it.events }.maxByOrNull { it.time }?.id
    ): List<MarkerStack> = swarms.map { swarm ->
        MarkerStack(
            id = swarm.id,
            centre = GeoCoord(swarm.centerLat, swarm.centerLon),
            markers = swarm.events.map { it.toMarker(colorByMagnitude, isLatest = it.id == latestId) }
        )
    }

    /**
     * Build the ripple layer. Two sources contribute:
     *   - tsunami-flagged quakes get a cyan ripple (steady, ocean-warning feel)
     *   - the single most-recent quake gets a ripple whose ring count, colour
     *     and reach scale with its magnitude — a tiny M2 is a 2-ring green
     *     blip; an M8 is a 7-ring red shockwave that reaches a continent.
     */
    fun toRipples(earthquakes: List<Earthquake>): List<RippleSpec> {
        val latest = earthquakes.maxByOrNull { it.time }
        val tsunamis = earthquakes.filter { it.tsunami == 1 }.map {
            RippleSpec(
                id = "tsunami_${it.id}",
                centre = GeoCoord(it.lat, it.lon),
                color = 0xC033B5FF.toInt(),
                ringCount = 4,
                speed = 1.0f,
                maxRadius = 0.22f
            )
        }
        val latestRipple = latest?.let { quake ->
            val shape = rippleShapeForMagnitude(quake.mag)
            RippleSpec(
                id = "latest_${quake.id}",
                centre = GeoCoord(quake.lat, quake.lon),
                color = shape.color,
                ringCount = shape.rings,
                speed = shape.speed,
                maxRadius = shape.maxRadius
            )
        }
        return tsunamis + listOfNotNull(latestRipple)
    }

    private data class RippleShape(
        val rings: Int,
        val speed: Float,
        val maxRadius: Float,
        val color: Int
    )

    /**
     * Maps magnitude to a ripple "personality":
     *
     *   M       rings  reach   colour
     *   < 3     2      tiny    soft green
     *   3–4     2      small   green
     *   4–5     3      medium  yellow-green
     *   5–6     4      large   yellow         (matches Moderate legend band)
     *   6–7     5      larger  orange         (Strong)
     *   7–8     6      huge    deep orange    (Major)
     *   8+      7      vast    red            (Great)
     *
     * Speed creeps up slightly with magnitude so a big quake feels more
     * energetic, but the change is subtle — the count and reach do most
     * of the visual work.
     */
    private fun rippleShapeForMagnitude(mag: Double): RippleShape = when {
        mag <  3.0 -> RippleShape(rings = 2, speed = 0.70f, maxRadius = 0.05f, color = 0xB066BB66.toInt())
        mag <  4.0 -> RippleShape(rings = 2, speed = 0.80f, maxRadius = 0.08f, color = 0xC04CAF50.toInt())
        mag <  5.0 -> RippleShape(rings = 3, speed = 0.90f, maxRadius = 0.12f, color = 0xC0CCDD33.toInt())
        mag <  6.0 -> RippleShape(rings = 4, speed = 1.00f, maxRadius = 0.16f, color = 0xD0FFEB3B.toInt())
        mag <  7.0 -> RippleShape(rings = 5, speed = 1.10f, maxRadius = 0.20f, color = 0xD0FF9800.toInt())
        mag <  8.0 -> RippleShape(rings = 6, speed = 1.20f, maxRadius = 0.26f, color = 0xE0FF5722.toInt())
        else       -> RippleShape(rings = 7, speed = 1.30f, maxRadius = 0.32f, color = 0xE8FF1744.toInt())
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    private fun Earthquake.toMarker(
        colorByMagnitude: Boolean,
        isLatest: Boolean = false
    ): Marker {
        val baseSize = ((mag - 3.0) / 2.5).coerceIn(0.6, 2.8).toFloat()
        val color = when {
            isLatest         -> 0xFFFFFFFF.toInt()   // bright white — pops against everything
            colorByMagnitude -> colorForMagnitude(mag)
            else             -> colorForDepth(depth)
        }
        return Marker(
            id = id,
            coord = GeoCoord(lat, lon),
            color = color,
            sizeHint = if (isLatest) (baseSize * 2.2f).coerceAtMost(4.0f) else baseSize,
            pulsing = isLatest
        )
    }

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
