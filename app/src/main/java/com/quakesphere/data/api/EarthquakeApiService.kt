package com.quakesphere.data.api

import com.quakesphere.data.api.dto.EarthquakeResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface EarthquakeApiService {

    @GET("query")
    suspend fun getEarthquakes(
        @Query("minmagnitude") minMagnitude: Double = 5.0,
        @Query("starttime") startTime: String? = null,
        // USGS default sort is time-descending. With limit=200 a 30-day request
        // returned only the most recent ~5 days; 2500 comfortably covers 30d
        // at M5+ globally (~50/day) without abusing the API.
        @Query("limit") limit: Int = 2500,
        @Query("format") format: String = "geojson",
        @Query("orderby") orderBy: String = "time"
    ): EarthquakeResponse
}
