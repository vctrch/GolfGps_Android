package com.vctrch.golfgps.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "cached_courses")
data class CachedCourseEntity(
    @PrimaryKey val courseId: String,
    val name: String,
    val city: String?,
    val state: String?,
    val latitude: Double,
    val longitude: Double,
    val osmId: Long?,
    val holesCount: Int?,
    val parTotal: Int?,
    val scorecardJson: String,
    val osmHolesJson: String?,
    val basicsUpdatedAt: Long,
    val osmUpdatedAt: Long?,
)

@Dao
interface CachedCourseDao {
    @Query("SELECT * FROM cached_courses WHERE courseId = :courseId LIMIT 1")
    suspend fun get(courseId: String): CachedCourseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedCourseEntity)
}
