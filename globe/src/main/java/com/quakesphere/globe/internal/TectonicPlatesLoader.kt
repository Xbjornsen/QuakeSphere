package com.quakesphere.globe.internal

import android.content.Context
import com.google.gson.JsonParser
import com.quakesphere.globe.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Loads PB2002 plate boundary line segments (Bird 2003) from the bundled
 * GeoJSON and projects them onto the sphere as a single line-list vertex
 * buffer ready for the renderer's line shader.
 */
internal object TectonicPlatesLoader {

    private const val PLATE_LINE_RADIUS = 1.0070f

    /** Returns flat XYZ floats: two consecutive vertices per `GL_LINES` segment. */
    fun load(context: Context): FloatArray {
        val text = context.resources.openRawResource(R.raw.tectonic_plates)
            .bufferedReader().use { it.readText() }
        val root = JsonParser.parseString(text).asJsonObject
        val features = root.getAsJsonArray("features")

        val out = ArrayList<Float>(100_000)
        for (feat in features) {
            val geom = feat.asJsonObject.getAsJsonObject("geometry") ?: continue
            val type = geom.get("type").asString
            val coords = geom.getAsJsonArray("coordinates")
            when (type) {
                "LineString"      -> processLine(coords, out)
                "MultiLineString" -> for (line in coords) processLine(line.asJsonArray, out)
            }
        }
        return out.toFloatArray()
    }

    private fun processLine(line: com.google.gson.JsonArray, out: ArrayList<Float>) {
        val pointCount = line.size()
        if (pointCount < 2) return

        // Same antimeridian unwrap trick we use for continents — needed
        // because some plates (Pacific) straddle ±180°.
        val unwrapped = DoubleArray(pointCount * 2)
        var prevLon = line.get(0).asJsonArray.get(0).asDouble
        unwrapped[0] = prevLon
        unwrapped[1] = line.get(0).asJsonArray.get(1).asDouble
        for (i in 1 until pointCount) {
            var lon = line.get(i).asJsonArray.get(0).asDouble
            val lat = line.get(i).asJsonArray.get(1).asDouble
            while (lon - prevLon >  180.0) lon -= 360.0
            while (lon - prevLon < -180.0) lon += 360.0
            unwrapped[i * 2]     = lon
            unwrapped[i * 2 + 1] = lat
            prevLon = lon
        }

        // Emit one GL_LINES segment per consecutive vertex pair, but skip
        // any still-antimeridian-crossing pair (shouldn't happen after unwrap
        // but kept as a defensive guard).
        for (i in 0 until pointCount - 1) {
            val lonA = unwrapped[i * 2]
            val latA = unwrapped[i * 2 + 1]
            val lonB = unwrapped[(i + 1) * 2]
            val latB = unwrapped[(i + 1) * 2 + 1]
            if (kotlin.math.abs(lonB - lonA) > 180.0) continue
            appendXYZ(out, latA.toFloat(), lonA.toFloat())
            appendXYZ(out, latB.toFloat(), lonB.toFloat())
        }
    }

    private fun appendXYZ(target: ArrayList<Float>, lat: Float, lon: Float) {
        val latR = (lat.toDouble() * PI / 180.0).toFloat()
        val lonR = (lon.toDouble() * PI / 180.0).toFloat()
        target.add(-PLATE_LINE_RADIUS * cos(latR) * cos(lonR))
        target.add(PLATE_LINE_RADIUS * sin(latR))
        target.add(PLATE_LINE_RADIUS * cos(latR) * sin(lonR))
    }
}
