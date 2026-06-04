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

    private const val FILL_RADIUS = 1.0015f
    private const val LINE_RADIUS = 1.0040f

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

        // Project each triangle vertex onto the sphere
        for (idx in indices) {
            val lon = flatArr[idx * 2].toFloat()
            val lat = flatArr[idx * 2 + 1].toFloat()
            appendXYZ(fills, lat, lon, FILL_RADIUS)
        }
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
