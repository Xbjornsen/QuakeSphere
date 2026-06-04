package com.quakesphere.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EarthquakeDao {

    @Query("SELECT * FROM earthquakes ORDER BY time DESC")
    fun getAll(): Flow<List<EarthquakeEntity>>

    @Query("SELECT * FROM earthquakes WHERE mag >= :minMag ORDER BY time DESC")
    fun getAllByMinMagnitude(minMag: Double): Flow<List<EarthquakeEntity>>

    @Query("SELECT * FROM earthquakes ORDER BY time DESC LIMIT 1")
    suspend fun getLatest(): EarthquakeEntity?

    @Query("SELECT * FROM earthquakes WHERE id = :id")
    suspend fun getById(id: String): EarthquakeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(earthquakes: List<EarthquakeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(earthquake: EarthquakeEntity)

    @Query("DELETE FROM earthquakes WHERE time < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("SELECT COUNT(*) FROM earthquakes")
    suspend fun count(): Int
}
