package com.quakesphere.domain.model

data class Earthquake(
    val id: String,
    val mag: Double,
    val place: String,
    val time: Long,
    val lat: Double,
    val lon: Double,
    val depth: Double,
    val url: String,
    val sig: Int,
    val tsunami: Int,
    val alert: String?,
    val status: String,
    val title: String
) {
    val depthCategory: DepthCategory
        get() = when {
            depth < 70.0 -> DepthCategory.SHALLOW
            depth < 300.0 -> DepthCategory.INTERMEDIATE
            else -> DepthCategory.DEEP
        }

    val magnitudeCategory: MagnitudeCategory
        get() = when {
            mag < 5.0 -> MagnitudeCategory.MINOR
            mag < 6.0 -> MagnitudeCategory.MODERATE
            mag < 7.0 -> MagnitudeCategory.STRONG
            mag < 8.0 -> MagnitudeCategory.MAJOR
            else -> MagnitudeCategory.GREAT
        }
}

enum class DepthCategory(val label: String, val colorHex: String) {
    SHALLOW("Shallow (<70 km)", "#FF4444"),
    INTERMEDIATE("Intermediate (70-300 km)", "#FF8800"),
    DEEP("Deep (>300 km)", "#4488FF")
}

enum class MagnitudeCategory(val label: String) {
    MINOR("Minor"),
    MODERATE("Moderate"),
    STRONG("Strong"),
    MAJOR("Major"),
    GREAT("Great")
}
