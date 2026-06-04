package com.quakesphere.data.api

import com.quakesphere.data.api.dto.EarthquakeResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface EarthquakeApiService {

    @GET("query")
    suspend fun getEarthquakes(
        @Query("minmagnitude") minMagnitude: Double = 5.0,
        @Query("starttime") startTime: String? = null,
        @Query("limit") limit: Int = 200,
        @Query("format") format: String = "geojson",
        @Query("orderby") orderBy: String = "time"
    ): EarthquakeResponse
}
