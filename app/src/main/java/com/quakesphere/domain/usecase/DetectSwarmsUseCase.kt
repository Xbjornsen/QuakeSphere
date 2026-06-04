package com.quakesphere.domain.usecase

import com.quakesphere.domain.model.Earthquake
import com.quakesphere.domain.model.EarthquakeSwarm
import javax.inject.Inject
import kotlin.math.*

class DetectSwarmsUseCase @Inject constructor() {

    companion object {
        const val CLUSTER_RADIUS_KM = 200.0
        const val CLUSTER_TIME_HOURS = 48L
        const val DEFAULT_MIN_EVENTS = 3
    }

    operator fun invoke(
        earthquakes: List<Earthquake>,
        minEvents: Int = DEFAULT_MIN_EVENTS
    ): List<EarthquakeSwarm> {
        if (earthquakes.size < minEvents) return emptyList()

        val timeWindowMs = CLUSTER_TIME_HOURS * 3_600_000L
        val clusters = mutableListOf<MutableList<Earthquake>>()

        for (quake in earthquakes.sortedByDescending { it.time }) {
            var added = false
            for (cluster in clusters) {
                val c = cluster.centroid()
                if (haversineKm(quake.lat, quake.lon, c.first, c.second) < CLUSTER_RADIUS_KM &&
                    abs(quake.time - cluster.maxOf { it.time }) < timeWindowMs
                ) {
                    cluster.add(quake)
                    added = true
                    break
                }
            }
            if (!added) clusters.add(mutableListOf(quake))
        }

        return clusters
            .filter { it.size >= minEvents }
            .map { events ->
                val byMag = events.sortedByDescending { it.mag }
                val c = events.centroid()
                EarthquakeSwarm(
                    id = "swarm_${events.minOf { it.time }}",
                    events = byMag,
                    centerLat = c.first,
                    centerLon = c.second,
                    maxMagnitude = byMag.first().mag,
                    startTime = events.minOf { it.time },
                    endTime = events.maxOf { it.time },
                    location = byMag.first().place
                )
            }
            .sortedByDescending { it.maxMagnitude }
    }

    private fun List<Earthquake>.centroid(): Pair<Double, Double> =
        Pair(sumOf { it.lat } / size, sumOf { it.lon } / size)

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return R * 2.0 * asin(sqrt(a))
    }
}
