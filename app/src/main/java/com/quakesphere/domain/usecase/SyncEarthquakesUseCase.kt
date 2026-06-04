package com.quakesphere.domain.usecase

import com.quakesphere.domain.repository.EarthquakeRepository
import javax.inject.Inject

class SyncEarthquakesUseCase @Inject constructor(
    private val repository: EarthquakeRepository
) {
    suspend operator fun invoke(minMagnitude: Double = 5.0): Result<Int> {
        return repository.syncEarthquakes(minMagnitude)
    }
}
