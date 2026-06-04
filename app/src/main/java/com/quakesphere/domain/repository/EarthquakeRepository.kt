package com.quakesphere.domain.repository

import com.quakesphere.domain.model.Earthquake
import kotlinx.coroutines.flow.Flow

interface EarthquakeRepository {
    fun getEarthquakes(minMagnitude: Double = 5.0): Flow<List<Earthquake>>
    suspend fun syncEarthquakes(minMagnitude: Double = 5.0): Result<Int>
    suspend fun getEarthquakeById(id: String): Earthquake?
}
