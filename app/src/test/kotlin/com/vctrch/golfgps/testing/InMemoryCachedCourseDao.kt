package com.vctrch.golfgps.testing

import com.vctrch.golfgps.data.local.*

class InMemoryCachedCourseDao : CachedCourseDao {
    private val store = mutableMapOf<String, CachedCourseEntity>()

    override suspend fun get(courseId: String): CachedCourseEntity? = store[courseId]

    override suspend fun upsert(entity: CachedCourseEntity) {
        store[entity.courseId] = entity
    }
}
