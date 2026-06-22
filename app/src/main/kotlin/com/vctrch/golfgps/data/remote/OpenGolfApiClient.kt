package com.vctrch.golfgps.data.remote

import com.vctrch.golfgps.domain.*
import retrofit2.HttpException

class OpenGolfApiClient(
    private val service: OpenGolfApiService,
) : OpenGolfApi {
    override suspend fun searchCourses(query: String): List<GolfCourseSummary> {
        return try {
            service.searchCourses(query).courses.map { it.toSummary() }
        } catch (_: HttpException) {
            throw GolfDataException(GolfDataError.INVALID_RESPONSE)
        }
    }

    override suspend fun loadCourse(id: String): Pair<GolfCourseSummary, List<ScorecardHole>> {
        return try {
            val detail = service.loadCourse(id)
            if (detail.id != id) throw GolfDataException(GolfDataError.INVALID_RESPONSE)
            detail.toSummary() to detail.toScorecard()
        } catch (_: HttpException) {
            throw GolfDataException(GolfDataError.INVALID_RESPONSE)
        }
    }
}
