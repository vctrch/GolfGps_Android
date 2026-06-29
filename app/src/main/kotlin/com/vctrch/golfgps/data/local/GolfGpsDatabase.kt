package com.vctrch.golfgps.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CachedCourseEntity::class],
    // v2: drop caches written by earlier builds whose course discovery returned a neighbouring
    // course's holes (e.g. a par-3 layout next to the target course), so stale wrong greens/yardages
    // are cleared via the destructive migration.
    version = 2,
    exportSchema = false,
)
abstract class GolfGpsDatabase : RoomDatabase() {
    abstract fun cachedCourseDao(): CachedCourseDao
}
