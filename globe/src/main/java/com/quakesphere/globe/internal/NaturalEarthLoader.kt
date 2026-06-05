package com.quakesphere.globe.internal

import android.content.Context
import com.google.gson.JsonParser
import com.quakesphere.globe.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Loads Natural Earth land polygons from a bundled GeoJSON file and
 * builds two GL-ready buffers:
 *   - fills:   triangle list of 3D XYZ vertices on the unit sphere (radius 1.0015)
 *   - outlines: line-segment list of 3D XYZ vertices (radius 1.0040)
 *
 * Each polygon ring is first triangulated in 2D lat/lon space via earcut,
 * then each vertex is projected to the sphere with [latLonToXYZ].
 * This avoids the convex-only limitation of fan-from-centroid triangulation.
 */
object NaturalEarthLoader {

    data class GeometryBuffers(
        val fillVertices: FloatArray,        // x,y,z per vertex; size = triangleCount*3*3
        val lineVertices: FloatArray         // x,y,z per vertex; size = segmentCount*2*3
    )

    private const val FILL_RADIUS = 1.0035f
    private const val LINE_RADIUS = 1.0060f

    /**
     * 4^N triangle multiplier per polygon. Level 2 = 16× = enough to make the
     * Brazil-sized earcut facets visually disappear at max zoom, but only
     * about 30 ms of extra startup work on a modern phone.
     */
    private const val SUBDIVISION_DEPTH = 2

    fun load(context: Context): GeometryBuffers {
        val text = context.resources.openRawResource(R.raw.ne_110m_land)
            .bufferedReader().use { it.readText() }
        val root = JsonParser.parseString(text).asJsonObject
        val features = root.getAsJsonArray("features")

        val fills = ArrayList<Float>(200_000)
        val lines = ArrayList<Float>(120_000)

        for (feat in features) {
            val geom = feat.asJsonObject.getAsJsonObject("geometry")
            val type = geom.get("type").asString
            val coords = geom.getAsJsonArray("coordinates")
            when (type) {
                "Polygon" -> processPolygon(coords, fills, lines)
                "MultiPolygon" -> {
                    for (poly in coords) processPolygon(poly.asJsonArray, fills, lines)
                }
            }
        }

        return GeometryBuffers(
            fillVertices = fills.toFloatArray(),
            lineVertices = lines.toFloatArray()
        )
    }

    /**
     * Triangulates a GeoJSON Polygon (outer ring + optional holes) and appends
     * triangle vertices to [fills] and outline segments to [lines].
     */
    private fun processPolygon(
        polygonArr: com.google.gson.JsonArray,
        fills: ArrayList<Float>,
        lines: ArrayList<Float>
    ) {
        if (polygonArr.size() == 0) return

        // Flatten outer ring + holes into a single DoubleArray with hole indices
        val flatCoords = ArrayList<Double>(256)
        val holeIndices = ArrayList<Int>()

        for (ringIdx in 0 until polygonArr.size()) {
            val ring = polygonArr.get(ringIdx).asJsonArray
            if (ringIdx > 0) holeIndices.add(flatCoords.size / 2)

            // Skip the duplicate closing point that GeoJSON rings always carry
            val ringPoints = ring.size() - 1

            // Detect antimeridian-crossing rings (Russia, Fiji, etc.) and split
            // so the earcut runs in a stable 360° span without giant fake triangles.
            val splitSegments = splitOnAntimeridian(ring, ringPoints)
            // For our purposes, we just push every vertex; the splits below
            // are used only for the outline pass.
            for (i in 0 until ringPoints) {
                val pt = ring.get(i).asJsonArray
                flatCoords.add(pt.get(0).asDouble)   // lon
                flatCoords.add(pt.get(1).asDouble)   // lat
            }

            // Outlines: emit one line segment per consecutive vertex pair, but
            // skip pairs that jump across the antimeridian (Δlon > 180°).
            for (seg in splitSegments) {
                for (i in 0 until seg.size - 1) {
                    val (lonA, latA) = seg[i]
                    val (lonB, latB) = seg[i + 1]
                    appendXYZ(lines, latA.toFloat(), lonA.toFloat(), LINE_RADIUS)
                    appendXYZ(lines, latB.toFloat(), lonB.toFloat(), LINE_RADIUS)
                }
            }
        }

        val flatArr = DoubleArray(flatCoords.size) { flatCoords[it] }
        val holeArr = IntArray(holeIndices.size) { holeIndices[it] }

        val indices = Earcut.triangulate(flatArr, holeArr, dim = 2)

        // Project each earcut triangle onto the sphere, then recursively
        // subdivide so individual triangles stay small enough to follow the
        // curvature. Without this the few giant triangles inside e.g. Brazil
        // become visibly flat facets when the user zooms in close.
        var i = 0
        while (i < indices.size) {
            val a = latLonOnSphere(flatArr[indices[i  ] * 2 + 1].toFloat(),
                                   flatArr[indices[i  ] * 2    ].toFloat())
            val b = latLonOnSphere(flatArr[indices[i+1] * 2 + 1].toFloat(),
                                   flatArr[indices[i+1] * 2    ].toFloat())
            val c = latLonOnSphere(flatArr[indices[i+2] * 2 + 1].toFloat(),
                                   flatArr[indices[i+2] * 2    ].toFloat())
            subdivideTriangle(a, b, c, depth = SUBDIVISION_DEPTH, out = fills)
            i += 3
        }
    }

    /** Lat/lon → XYZ on the fill-radius sphere, matching the renderer's axis convention. */
    private fun latLonOnSphere(lat: Float, lon: Float): FloatArray {
        val latR = (lat.toDouble() * PI / 180.0).toFloat()
        val lonR = (lon.toDouble() * PI / 180.0).toFloat()
        return floatArrayOf(
            -FILL_RADIUS * cos(latR) * cos(lonR),
            FILL_RADIUS * sin(latR),
            FILL_RADIUS * cos(latR) * sin(lonR)
        )
    }

    /**
     * Recursively splits a triangle into 4 sub-triangles, projecting each new
     * mid-edge vertex back onto the sphere so the geometry tracks the curvature.
     * Output triangles are appended as flat XYZ floats.
     */
    private fun subdivideTriangle(
        a: FloatArray, b: FloatArray, c: FloatArray, depth: Int, out: ArrayList<Float>
    ) {
        if (depth == 0) {
            out.add(a[0]); out.add(a[1]); out.add(a[2])
            out.add(b[0]); out.add(b[1]); out.add(b[2])
            out.add(c[0]); out.add(c[1]); out.add(c[2])
            return
        }
        val ab = midpointOnSphere(a, b)
        val bc = midpointOnSphere(b, c)
        val ca = midpointOnSphere(c, a)
        val d = depth - 1
        // Three corner triangles share the parent's winding naturally.
        subdivideTriangle(a,  ab, ca, d, out)
        subdivideTriangle(ab, b,  bc, d, out)
        subdivideTriangle(bc, c,  ca, d, out)
        // The centre triangle's "natural" vertex order (ab, bc, ca) is the
        // mirror of the parent winding. Emitting it as (ca, bc, ab) — the
        // reverse — keeps every sub-triangle consistently oriented so drivers
        // with subpixel-coverage heuristics don't drop the centres and leave
        // a hexagonal lattice of holes across each parent.
        subdivideTriangle(ca, bc, ab, d, out)
    }

    /** Midpoint between two sphere-surface points, re-projected onto the same sphere. */
    private fun midpointOnSphere(a: FloatArray, b: FloatArray): FloatArray {
        val mx = (a[0] + b[0]) * 0.5f
        val my = (a[1] + b[1]) * 0.5f
        val mz = (a[2] + b[2]) * 0.5f
        val len = kotlin.math.sqrt(mx*mx + my*my + mz*mz).coerceAtLeast(1e-6f)
        val s = FILL_RADIUS / len
        return floatArrayOf(mx * s, my * s, mz * s)
    }

    /**
     * Splits a polygon ring into contiguous sub-paths whenever consecutive
     * longitudes jump by more than 180° — Natural Earth wraps Russia / Fiji
     * across the antimeridian, which would otherwise emit a single line
     * stretching halfway around the globe.
     */
    private fun splitOnAntimeridian(
        ring: com.google.gson.JsonArray, ringPoints: Int
    ): List<List<Pair<Double, Double>>> {
        val segments = ArrayList<List<Pair<Double, Double>>>()
        var current = ArrayList<Pair<Double, Double>>()
        var prevLon = ring.get(0).asJsonArray.get(0).asDouble
        current.add(Pair(prevLon, ring.get(0).asJsonArray.get(1).asDouble))

        for (i in 1 until ringPoints) {
            val pt = ring.get(i).asJsonArray
            val lon = pt.get(0).asDouble
            val lat = pt.get(1).asDouble
            if (kotlin.math.abs(lon - prevLon) > 180.0) {
                segments.add(current)
                current = ArrayList()
            }
            current.add(Pair(lon, lat))
            prevLon = lon
        }
        if (current.isNotEmpty()) segments.add(current)
        return segments
    }

    /**
     * Lat/lon → 3D point on a sphere. Must use the same axis convention as
     * [GlobeRenderer.latLonToXYZ] — negative X on the cos·cos term so that
     * increasing longitude moves eastward (rightward when viewed from outside
     * with north up). Without this negation continents render mirrored E-W.
     */
    private fun appendXYZ(target: ArrayList<Float>, lat: Float, lon: Float, radius: Float) {
        val latR = (lat.toDouble() * PI / 180.0).toFloat()
        val lonR = (lon.toDouble() * PI / 180.0).toFloat()
        target.add(-radius * cos(latR) * cos(lonR))
        target.add(radius * sin(latR))
        target.add(radius * cos(latR) * sin(lonR))
    }
}
