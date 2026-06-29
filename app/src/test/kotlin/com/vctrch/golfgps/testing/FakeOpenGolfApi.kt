package com.vctrch.golfgps.testing

import com.vctrch.golfgps.data.remote.OpenGolfApi
import com.vctrch.golfgps.domain.*

class FakeOpenGolfApi : OpenGolfApi {
    var searchResults: List<GolfCourseSummary> = emptyList()
    var loadResult: Pair<GolfCourseSummary, List<ScorecardHole>>? = null
    var searchShouldFail = false
    var loadShouldFail = false

    var lastSearchQuery: String? = null
    var lastLoadId: String? = null

    override suspend fun searchCourses(query: String): List<GolfCourseSummary> {
        lastSearchQuery = query
        if (searchShouldFail) throw IllegalStateException("search failed")
        return searchResults
    }

    override suspend fun loadCourse(id: String): Pair<GolfCourseSummary, List<ScorecardHole>> {
        lastLoadId = id
        if (loadShouldFail) throw IllegalStateException("load failed")
        return loadResult ?: throw IllegalStateException("load result not configured")
    }
}
