package com.vctrch.golfgps.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CachedCourseEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class GolfGpsDatabase : RoomDatabase() {
    abstract fun cachedCourseDao(): CachedCourseDao
}
