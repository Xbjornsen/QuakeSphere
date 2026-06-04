package com.quakesphere.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [EarthquakeEntity::class],
    version = 1,
    exportSchema = false
)
abstract class EarthquakeDatabase : RoomDatabase() {
    abstract fun earthquakeDao(): EarthquakeDao
}
