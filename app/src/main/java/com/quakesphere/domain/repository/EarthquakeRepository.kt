package com.quakesphere.domain.repository

import com.quakesphere.domain.model.Earthquake
import kotlinx.coroutines.flow.Flow

interface EarthquakeRepository {
    /** [sinceTime] = epoch-millis lower bound; 0 means no time filter. */
    fun getEarthquakes(minMagnitude: Double = 5.0, sinceTime: Long = 0L): Flow<List<Earthquake>>
    suspend fun syncEarthquakes(minMagnitude: Double = 5.0, sinceTime: Long = 0L): Result<Int>
    suspend fun getEarthquakeById(id: String): Earthquake?
}
