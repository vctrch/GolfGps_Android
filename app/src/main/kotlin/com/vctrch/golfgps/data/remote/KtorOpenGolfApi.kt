package com.vctrch.golfgps.data.remote

import com.vctrch.golfgps.domain.GolfCourseSummary
import com.vctrch.golfgps.domain.GolfDataError
import com.vctrch.golfgps.domain.GolfDataException
import com.vctrch.golfgps.domain.ScorecardHole
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.appendPathSegments

/**
 * Ktor-backed implementation of [OpenGolfApi]. The base URL and the differing version segments are
 * OpenGolfAPI's own structure (a third-party service): search is served under `v1`, while full
 * course detail (incl. per-hole data) is served under `api/v1`.
 */
class KtorOpenGolfApi(
    private val client: HttpClient,
    private val baseUrl: String,
) : OpenGolfApi {
    override suspend fun searchCourses(query: String): List<GolfCourseSummary> {
        val response: OpenGolfSearchResponse =
            try {
                client
                    .get(baseUrl) {
                        url {
                            appendPathSegments("v1", "courses", "search")
                            parameters.append("q", query)
                            parameters.append("limit", DEFAULT_LIMIT.toString())
                        }
                    }.body()
            } catch (_: Exception) {
                throw GolfDataException(GolfDataError.INVALID_RESPONSE)
            }
        return response.courses.map { it.toSummary() }
    }

    override suspend fun loadCourse(id: String): Pair<GolfCourseSummary, List<ScorecardHole>> {
        val detail: OpenGolfCourseDetail =
            try {
                client
                    .get(baseUrl) {
                        url { appendPathSegments("api", "v1", "courses", id) }
                    }.body()
            } catch (_: Exception) {
                throw GolfDataException(GolfDataError.INVALID_RESPONSE)
            }
        if (detail.id != id) throw GolfDataException(GolfDataError.INVALID_RESPONSE)
        return detail.toSummary() to detail.toScorecard()
    }

    private companion object {
        const val DEFAULT_LIMIT = 25
    }
}
