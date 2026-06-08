package com.quakesphere.globe.internal

import android.content.Context
import com.google.gson.JsonParser
import com.quakesphere.globe.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Builds a `WIDTH × HEIGHT` density grid representing seismic activity on
 * Earth, derived from the bundled PB2002 plate boundaries (Bird 2003).
 *
 * The premise: M5+ quake density worldwide correlates extremely strongly with
 * distance from active plate boundaries. Rather than fetching and binning a
 * catalog of hundreds of thousands of historical events at runtime (or making
 * the user install Python to bake one offline), we compute "proximity to a
 * plate boundary" as a deterministic, geophysically meaningful proxy.
 *
 * The result is a single-channel byte array, row-major, top-to-bottom
 * (latitude +90 → −90), left-to-right (longitude −180 → +180). Pixel value
 * 0 = no nearby activity, 255 = on top of a boundary.
 *
 * Output is sampled in the globe shader using the existing sphere-mesh UV
 * coordinates, so no extra geometry is needed.
 */
internal object HeatmapGenerator {

    const val WIDTH  = 720
    const val HEIGHT = 360

    /** Standard deviation of the Gaussian falloff, in degrees of arc. */
    private const val SIGMA_DEG = 4.5f

    /** Beyond this many degrees from a boundary, we don't bother contributing. */
    private const val CUTOFF_DEG = 18f

    /**
     * Reads the bundled PB2002 GeoJSON, samples each line segment at ~1° spacing,
     * splats Gaussian intensity into the grid, normalises to 0..255 bytes.
     */
    fun build(context: Context): ByteArray {
        val grid = FloatArray(WIDTH * HEIGHT)

        // 1. Gather a flat list of (lonDeg, latDeg) sample points along every
        //    plate-boundary line.
        val text = context.resources.openRawResource(R.raw.tectonic_plates)
            .bufferedReader().use { it.readText() }
        val root = JsonParser.parseString(text).asJsonObject
        val features = root.getAsJsonArray("features")

        val sampleLons = ArrayList<Float>(50_000)
        val sampleLats = ArrayList<Float>(50_000)
        for (feat in features) {
            val geom = feat.asJsonObject.getAsJsonObject("geometry") ?: continue
            val type = geom.get("type").asString
            val coords = geom.getAsJsonArray("coordinates")
            when (type) {
                "LineString"      -> sampleLine(coords, sampleLons, sampleLats)
                "MultiLineString" -> for (line in coords) sampleLine(line.asJsonArray, sampleLons, sampleLats)
            }
        }

        // 2. For every sample, splat a localised Gaussian into the grid. We
        //    only touch cells within CUTOFF_DEG of the sample to keep this
        //    fast — without that the cost is N samples × HEIGHT × WIDTH.
        val sigmaSq = SIGMA_DEG * SIGMA_DEG
        val cutoffSq = CUTOFF_DEG * CUTOFF_DEG
        val degPerPxX = 360f / WIDTH
        val degPerPxY = 180f / HEIGHT

        for (i in sampleLons.indices) {
            val lon = sampleLons[i]
            val lat = sampleLats[i]

            val centreCol = ((lon + 180f) / degPerPxX).toInt().coerceIn(0, WIDTH - 1)
            val centreRow = ((90f - lat) / degPerPxY).toInt().coerceIn(0, HEIGHT - 1)

            // Bounding box in pixels around (centreCol, centreRow).
            val pxRadius = (CUTOFF_DEG / degPerPxY).toInt() + 1
            val rowMin = max(0,          centreRow - pxRadius)
            val rowMax = min(HEIGHT - 1, centreRow + pxRadius)

            for (row in rowMin..rowMax) {
                val cellLat = 90f - (row + 0.5f) * degPerPxY
                // Longitude radius shrinks at high latitudes, so widen pixel
                // box by 1/cos(lat) to keep the angular radius constant.
                val invCosLat = 1f / max(0.05f, cos(cellLat.toDouble() * PI / 180.0).toFloat())
                val colSpread = (pxRadius * invCosLat).toInt() + 1
                val colMin = centreCol - colSpread
                val colMax = centreCol + colSpread

                val dLat = cellLat - lat
                val dLatSq = dLat * dLat

                for (rawCol in colMin..colMax) {
                    val col = ((rawCol % WIDTH) + WIDTH) % WIDTH   // wrap horizontally
                    val cellLon = -180f + (col + 0.5f) * degPerPxX
                    // Shortest signed longitude delta (handles antimeridian).
                    var dLon = cellLon - lon
                    if (dLon >  180f) dLon -= 360f
                    if (dLon < -180f) dLon += 360f
                    // Approximate angular distance squared in degrees on the
                    // sphere — fine for the small cutoff radius we use here.
                    val dLonScaled = dLon * cos(cellLat.toDouble() * PI / 180.0).toFloat()
                    val dSq = dLatSq + dLonScaled * dLonScaled
                    if (dSq > cutoffSq) continue
                    grid[row * WIDTH + col] += exp(-dSq / (2f * sigmaSq))
                }
            }
        }

        // 3. Normalise to 0..1 then quantise to 0..255 bytes. We clamp the
        //    99th-percentile-ish peak to 1.0 so individual very-busy junctions
        //    (e.g. East-Pacific Rise / Cocos / Nazca triple junction) don't
        //    swamp the overall scale.
        var sumSq = 0.0
        var peak  = 0f
        for (v in grid) { sumSq += v.toDouble() * v; if (v > peak) peak = v }
        val mean = sqrt(sumSq / grid.size).toFloat()
        val clampMax = max(peak * 0.55f, mean * 6f)
        val invMax = 1f / clampMax

        val out = ByteArray(grid.size)
        for (i in grid.indices) {
            val v = (grid[i] * invMax).coerceIn(0f, 1f)
            // Mild gamma so faint zones still register visibly.
            val shaped = v.let { it * (0.4f + 0.6f * it) }   // gamma ~1.4
            out[i] = (round(shaped * 255f).toInt() and 0xFF).toByte()
        }
        return out
    }

    private fun sampleLine(
        coords: com.google.gson.JsonArray,
        outLons: ArrayList<Float>,
        outLats: ArrayList<Float>
    ) {
        if (coords.size() < 2) return
        for (i in 0 until coords.size() - 1) {
            val a = coords.get(i).asJsonArray
            val b = coords.get(i + 1).asJsonArray
            val lonA = a.get(0).asFloat
            val latA = a.get(1).asFloat
            var lonB = b.get(0).asFloat
            val latB = b.get(1).asFloat
            // Antimeridian-safe shortest path.
            if (lonB - lonA >  180f) lonB -= 360f
            if (lonB - lonA < -180f) lonB += 360f
            val arc = sqrt(((lonB - lonA) * (lonB - lonA) + (latB - latA) * (latB - latA)).toDouble()).toFloat()
            val steps = (arc / 1.0f).toInt().coerceAtLeast(1)
            for (s in 0..steps) {
                val t = s.toFloat() / steps
                outLons.add(lonA + (lonB - lonA) * t)
                outLats.add(latA + (latB - latA) * t)
            }
        }
    }
}
