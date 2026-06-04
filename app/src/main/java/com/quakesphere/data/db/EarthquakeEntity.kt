package com.quakesphere.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "earthquakes")
data class EarthquakeEntity(
    @PrimaryKey val id: String,
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
)
