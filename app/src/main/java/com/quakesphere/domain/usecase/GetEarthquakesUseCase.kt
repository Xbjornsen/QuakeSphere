package com.quakesphere.domain.usecase

import com.quakesphere.domain.model.Earthquake
import com.quakesphere.domain.repository.EarthquakeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetEarthquakesUseCase @Inject constructor(
    private val repository: EarthquakeRepository
) {
    operator fun invoke(minMagnitude: Double = 5.0, sinceTime: Long = 0L): Flow<List<Earthquake>> {
        return repository.getEarthquakes(minMagnitude, sinceTime)
    }
}
