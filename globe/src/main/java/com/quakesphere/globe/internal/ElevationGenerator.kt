package com.quakesphere.globe.internal

import android.content.Context
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Builds a `WIDTH × HEIGHT` elevation grid normalised to 0..1, derived from
 * the bundled major-peaks list. Each peak contributes a Gaussian splat
 * weighted by its elevation; overlapping splats take the max (not sum), so
 * the Himalayas form a smooth plateau rather than spiking.
 *
 * The grid is consumed by the renderer to displace the globe sphere mesh
 * and continent fills along the surface normal — bumpy land + ocean.
 *
 * Cell layout: row-major, top-to-bottom (latitude +90 → −90), left-to-right
 * (longitude −180 → +180). The longitudinal axis wraps; the latitudinal one
 * does not.
 *
 * Implementation note: we do not ship an actual elevation dataset (ETOPO etc).
 * The peaks-driven splat is geophysically coarse but visually plausible at
 * globe scale, and adds zero asset weight to the APK.
 */
internal object ElevationGenerator {

    const val WIDTH  = 720
    const val HEIGHT = 360

    /** Standard deviation of the Gaussian falloff, in degrees of arc. */
    private const val SIGMA_DEG  = 5f

    /** Beyond this many degrees from a peak, don't bother contributing. */
    private const val CUTOFF_DEG = 18f

    fun build(context: Context): FloatArray {
        val grid = FloatArray(WIDTH * HEIGHT)
        val peaks = PeaksLoader.load(context).map { it.peak }
        if (peaks.isEmpty()) return grid

        // Find max elevation so we can normalise to 0..1
        val maxElev = peaks.maxOf { it.elevM }.toFloat()
        if (maxElev <= 0f) return grid

        for (peak in peaks) {
            val weight = peak.elevM.toFloat() / maxElev
            splat(grid, peak.lat, peak.lon, weight)
        }

        return grid
    }

    /**
     * Gaussian splat at (lat, lon) with peak intensity [weight] (0..1).
     * Writes the MAX of the new contribution and the existing cell — so
     * adjacent peaks form a plateau rather than a sum-spike.
     */
    private fun splat(grid: FloatArray, lat: Float, lon: Float, weight: Float) {
        val cx = wrap(((lon + 180f) / 360f * WIDTH).toInt(), WIDTH)
        val cy = clamp(((90f - lat) / 180f * HEIGHT).toInt(), 0, HEIGHT - 1)

        // Pixel radius: cutoff degrees → fractional rows / cols
        val radiusPx = (CUTOFF_DEG / 180f * HEIGHT).toInt()
        val cosLat = cos(lat.toDouble() * PI / 180.0).toFloat().coerceAtLeast(0.01f)

        for (dy in -radiusPx..radiusPx) {
            val py = cy + dy
            if (py < 0 || py >= HEIGHT) continue
            for (dx in -radiusPx..radiusPx) {
                val px = wrap(cx + dx, WIDTH)
                // Angular distance in degrees (approximate spherical metric).
                // Latitude diff is dy/HEIGHT * 180; longitude diff is dx/WIDTH * 360
                // shrunk by cos(lat) to account for converging meridians.
                val dLatDeg = dy.toFloat() / HEIGHT * 180f
                val dLonDeg = dx.toFloat() / WIDTH  * 360f * cosLat
                val dist = sqrt(dLatDeg * dLatDeg + dLonDeg * dLonDeg)
                if (dist > CUTOFF_DEG) continue
                val w  = exp(-(dist * dist) / (2f * SIGMA_DEG * SIGMA_DEG))
                val contrib = weight * w
                val idx = py * WIDTH + px
                if (contrib > grid[idx]) grid[idx] = contrib
            }
        }
    }

    /**
     * Bilinearly sample the grid at unit-sphere xyz position. Returns the
     * elevation 0..1 at that point. Used both for the sphere mesh and the
     * continent-fill mesh — they share the same projection.
     */
    fun sample(grid: FloatArray, x: Float, y: Float, z: Float): Float {
        // Normalise xyz onto unit sphere — continent vertices are at 1.001, etc.
        val r = sqrt(x * x + y * y + z * z)
        val nx = x / r; val ny = y / r; val nz = z / r
        // Inverse of GlobeRenderer.latLonToXYZ:
        //   x = -cos(lat) cos(lon),  y = sin(lat),  z = cos(lat) sin(lon)
        // ⇒ lat = asin(y),  lon = atan2(z, -x)
        val lat = kotlin.math.asin(ny.coerceIn(-1f, 1f))
        val lon = kotlin.math.atan2(nz, -nx)
        val u = ((lon + PI.toFloat()) / (2f * PI.toFloat())).let { ((it % 1f) + 1f) % 1f }
        val v = ((PI.toFloat() / 2f - lat) / PI.toFloat()).coerceIn(0f, 1f)

        val fx = u * (WIDTH - 1)
        val fy = v * (HEIGHT - 1)
        val x0 = fx.toInt(); val x1 = ((x0 + 1) % WIDTH)
        val y0 = fy.toInt(); val y1 = (y0 + 1).coerceAtMost(HEIGHT - 1)
        val tx = fx - x0
        val ty = fy - y0
        val a = grid[y0 * WIDTH + x0]
        val b = grid[y0 * WIDTH + x1]
        val c = grid[y1 * WIDTH + x0]
        val d = grid[y1 * WIDTH + x1]
        return a * (1f - tx) * (1f - ty) +
               b * tx * (1f - ty) +
               c * (1f - tx) * ty +
               d * tx * ty
    }

    private fun wrap(i: Int, mod: Int): Int { val r = i % mod; return if (r < 0) r + mod else r }
    private fun clamp(i: Int, lo: Int, hi: Int): Int = if (i < lo) lo else if (i > hi) hi else i
}
