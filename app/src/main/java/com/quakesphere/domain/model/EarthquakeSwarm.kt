package com.quakesphere.domain.model

data class EarthquakeSwarm(
    val id: String,
    val events: List<Earthquake>,   // sorted by magnitude descending
    val centerLat: Double,
    val centerLon: Double,
    val maxMagnitude: Double,
    val startTime: Long,
    val endTime: Long,
    val location: String
) {
    val eventCount: Int get() = events.size
    val durationHours: Long get() = (endTime - startTime) / 3_600_000L
    val magnitudeCategory: MagnitudeCategory
        get() = when {
            maxMagnitude < 5.0 -> MagnitudeCategory.MINOR
            maxMagnitude < 6.0 -> MagnitudeCategory.MODERATE
            maxMagnitude < 7.0 -> MagnitudeCategory.STRONG
            maxMagnitude < 8.0 -> MagnitudeCategory.MAJOR
            else -> MagnitudeCategory.GREAT
        }
}
