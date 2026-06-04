package com.quakesphere.data.repository

import com.quakesphere.data.api.EarthquakeApiService
import com.quakesphere.data.db.EarthquakeDao
import com.quakesphere.data.db.EarthquakeEntity
import com.quakesphere.domain.model.Earthquake
import com.quakesphere.domain.repository.EarthquakeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EarthquakeRepositoryImpl @Inject constructor(
    private val apiService: EarthquakeApiService,
    private val dao: EarthquakeDao
) : EarthquakeRepository {

    override fun getEarthquakes(minMagnitude: Double, sinceTime: Long): Flow<List<Earthquake>> {
        return dao.getFiltered(minMagnitude, sinceTime).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun syncEarthquakes(minMagnitude: Double, sinceTime: Long): Result<Int> {
        return try {
            val startTimeIso = if (sinceTime > 0L) {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .format(Date(sinceTime))
            } else null
            val response = apiService.getEarthquakes(
                minMagnitude = minMagnitude,
                startTime    = startTimeIso,
                limit        = 200
            )
            val entities = response.features.mapNotNull { feature ->
                val coords = feature.geometry.coordinates
                if (coords.size >= 3) {
                    EarthquakeEntity(
                        id = feature.id,
                        mag = feature.properties.mag ?: 0.0,
                        place = feature.properties.place ?: "Unknown location",
                        time = feature.properties.time ?: 0L,
                        lat = coords[1],
                        lon = coords[0],
                        depth = coords[2],
                        url = feature.properties.url ?: "",
                        sig = feature.properties.sig ?: 0,
                        tsunami = feature.properties.tsunami ?: 0,
                        alert = feature.properties.alert,
                        status = feature.properties.status ?: "unknown",
                        title = feature.properties.title ?: "M${feature.properties.mag} - ${feature.properties.place}"
                    )
                } else null
            }
            dao.insertAll(entities)
            // Clean up old data (older than 30 days)
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
            dao.deleteOlderThan(cutoff)
            Result.success(entities.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getEarthquakeById(id: String): Earthquake? {
        return dao.getById(id)?.toDomain()
    }

    private fun EarthquakeEntity.toDomain(): Earthquake {
        return Earthquake(
            id = id,
            mag = mag,
            place = place,
            time = time,
            lat = lat,
            lon = lon,
            depth = depth,
            url = url,
            sig = sig,
            tsunami = tsunami,
            alert = alert,
            status = status,
            title = title
        )
    }
}
