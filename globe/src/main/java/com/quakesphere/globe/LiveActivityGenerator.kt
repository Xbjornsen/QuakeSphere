package com.quakesphere.globe

import com.quakesphere.globe.internal.HeatmapGenerator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Builds a [HeatmapGenerator.WIDTH] × [HeatmapGenerator.HEIGHT] density grid
 * from a list of real, recent earthquakes. Output layout matches the warm
 * baseline heatmap exactly so both layers can sample the sphere's UVs without
 * any geometry-side adjustments.
 *
 * Each event contributes a Gaussian splat weighted by its magnitude (heavier
 * weight for larger magnitudes — M7 dominates a region of M5s). Output is
 * clamped + quantised to bytes 0..255.
 *
 * Cheap to recompute: ~few hundred events × a small pixel neighbourhood is
 * a handful of milliseconds on a midrange phone. Caller invokes this off the
 * UI thread whenever the underlying earthquake list changes.
 */
object LiveActivityGenerator {

    /** A tiny input record so the globe module doesn't depend on the app module. */
    data class Event(val lat: Float, val lon: Float, val mag: Float)

    private const val WIDTH  = HeatmapGenerator.WIDTH
    private const val HEIGHT = HeatmapGenerator.HEIGHT

    /** Standard deviation of the Gaussian splat, in degrees of arc. */
    private const val SIGMA_DEG  = 1.6f

    /** Cut-off radius for the splat (saves work on cells that contribute nothing). */
    private const val CUTOFF_DEG = 6f

    /** Magnitude below which we drop events — anything weaker than M4 is noise here. */
    private const val MIN_MAG = 4.0f

    /**
     * Returns a byte grid suitable for [GlobeRenderer.setLiveActivityPixels],
     * or an empty array if [events] is empty.
     */
    fun build(events: List<Event>): ByteArray {
        if (events.isEmpty()) return ByteArray(0)
        val grid = FloatArray(WIDTH * HEIGHT)

        val sigmaSq  = SIGMA_DEG * SIGMA_DEG
        val cutoffSq = CUTOFF_DEG * CUTOFF_DEG
        val degPerPxX = 360f / WIDTH
        val degPerPxY = 180f / HEIGHT

        for (e in events) {
            if (e.mag < MIN_MAG) continue
            // Magnitude is a log scale; weight by 2^(M-4) so M5=2, M6=4, M7=8, M8=16.
            val weight = Math.pow(2.0, (e.mag - 4f).toDouble()).toFloat()

            val centreCol = ((e.lon + 180f) / degPerPxX).toInt().coerceIn(0, WIDTH - 1)
            val centreRow = ((90f - e.lat) / degPerPxY).toInt().coerceIn(0, HEIGHT - 1)

            val pxRadius = (CUTOFF_DEG / degPerPxY).toInt() + 1
            val rowMin = max(0,          centreRow - pxRadius)
            val rowMax = min(HEIGHT - 1, centreRow + pxRadius)

            for (row in rowMin..rowMax) {
                val cellLat = 90f - (row + 0.5f) * degPerPxY
                val invCosLat = 1f / max(0.05f, cos(cellLat.toDouble() * PI / 180.0).toFloat())
                val colSpread = (pxRadius * invCosLat).toInt() + 1
                val colMin = centreCol - colSpread
                val colMax = centreCol + colSpread

                val dLat = cellLat - e.lat
                val dLatSq = dLat * dLat

                for (rawCol in colMin..colMax) {
                    val col = ((rawCol % WIDTH) + WIDTH) % WIDTH
                    val cellLon = -180f + (col + 0.5f) * degPerPxX
                    var dLon = cellLon - e.lon
                    if (dLon >  180f) dLon -= 360f
                    if (dLon < -180f) dLon += 360f
                    val dLonScaled = dLon * cos(cellLat.toDouble() * PI / 180.0).toFloat()
                    val dSq = dLatSq + dLonScaled * dLonScaled
                    if (dSq > cutoffSq) continue
                    grid[row * WIDTH + col] += weight * exp(-dSq / (2f * sigmaSq))
                }
            }
        }

        // Clamp to 0..1 with a robust ceiling so a single big event doesn't
        // squash the rest. Use peak × 0.55 or 8× the RMS, whichever is larger.
        var sumSq = 0.0
        var peak  = 0f
        for (v in grid) { sumSq += v.toDouble() * v; if (v > peak) peak = v }
        if (peak <= 0f) return ByteArray(grid.size)
        val rms = sqrt(sumSq / grid.size).toFloat()
        val clampMax = max(peak * 0.55f, rms * 8f).coerceAtLeast(0.001f)
        val invMax = 1f / clampMax

        val out = ByteArray(grid.size)
        for (i in grid.indices) {
            val v = (grid[i] * invMax).coerceIn(0f, 1f)
            val shaped = v.let { it * (0.45f + 0.55f * it) }
            out[i] = (round(shaped * 255f).toInt() and 0xFF).toByte()
        }
        return out
    }
}
