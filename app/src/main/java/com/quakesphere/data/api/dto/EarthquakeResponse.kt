package com.quakesphere.data.api.dto

import com.google.gson.annotations.SerializedName

data class EarthquakeResponse(
    @SerializedName("type") val type: String,
    @SerializedName("features") val features: List<Feature>
)

data class Feature(
    @SerializedName("id") val id: String,
    @SerializedName("properties") val properties: Properties,
    @SerializedName("geometry") val geometry: Geometry
)

data class Properties(
    @SerializedName("mag") val mag: Double?,
    @SerializedName("place") val place: String?,
    @SerializedName("time") val time: Long?,
    @SerializedName("updated") val updated: Long?,
    @SerializedName("url") val url: String?,
    @SerializedName("detail") val detail: String?,
    @SerializedName("felt") val felt: Int?,
    @SerializedName("cdi") val cdi: Double?,
    @SerializedName("mmi") val mmi: Double?,
    @SerializedName("alert") val alert: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("tsunami") val tsunami: Int?,
    @SerializedName("sig") val sig: Int?,
    @SerializedName("type") val type: String?,
    @SerializedName("title") val title: String?
)

data class Geometry(
    @SerializedName("type") val type: String,
    @SerializedName("coordinates") val coordinates: List<Double>
)
