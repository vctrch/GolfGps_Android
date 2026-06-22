package com.vctrch.golfgps.data.repository

import com.vctrch.golfgps.data.local.CourseDataCache
import com.vctrch.golfgps.data.remote.OpenGolfApi
import com.vctrch.golfgps.domain.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CourseRepository
    @Inject
    constructor(
        private val openGolfApi: OpenGolfApi,
        private val cache: CourseDataCache,
    ) {
        suspend fun searchCourses(query: String): List<GolfCourseSummary> {
            return openGolfApi.searchCourses(query)
        }

        suspend fun loadCourseBasics(courseId: String): LoadedCourse {
            return try {
                val (summary, scorecard) = openGolfApi.loadCourse(courseId)
                val holes = CourseLoaderSupport.fallbackHoles(scorecard, summary)
                val loaded = LoadedCourse(summary = summary, scorecard = scorecard, holes = holes)
                cache.saveBasics(loaded)
                loaded
            } catch (_: Exception) {
                cache.cachedBasics(courseId)
                    ?: throw IllegalStateException("Couldn't load course")
            }
        }
    }
