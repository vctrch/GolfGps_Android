package com.vctrch.golfgps.data.remote

import com.vctrch.golfgps.domain.model.GolfCourseSummary
import com.vctrch.golfgps.domain.model.ScorecardHole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenGolfSearchResponse(
    val courses: List<OpenGolfCourseListItem> = emptyList(),
)

@Serializable
data class OpenGolfCourseListItem(
    val id: String,
    @SerialName("osm_id") val osmId: Long? = null,
    @SerialName("club_name") val clubName: String? = null,
    @SerialName("course_name") val courseName: String? = null,
    val city: String? = null,
    val state: String? = null,
    val latitude: Double,
    val longitude: Double,
    @SerialName("holes_count") val holesCount: Int? = null,
    @SerialName("par_total") val parTotal: Int? = null,
)

@Serializable
data class OpenGolfCourseDetail(
    val id: String,
    @SerialName("osm_id") val osmId: Long? = null,
    @SerialName("club_name") val clubName: String? = null,
    @SerialName("course_name") val courseName: String? = null,
    val city: String? = null,
    val state: String? = null,
    val latitude: Double,
    val longitude: Double,
    @SerialName("holes_count") val holesCount: Int? = null,
    @SerialName("par_total") val parTotal: Int? = null,
    val scorecard: List<OpenGolfScorecardRow>? = null,
)

@Serializable
data class OpenGolfScorecardRow(
    @SerialName("hole_number") val holeNumber: Int? = null,
    val par: Int? = null,
    val handicap: Int? = null,
)

fun OpenGolfCourseListItem.toSummary(): GolfCourseSummary {
    return GolfCourseSummary(
        id = id,
        name = courseName ?: clubName ?: "Golf Course",
        city = city,
        state = state,
        latitude = latitude,
        longitude = longitude,
        osmId = osmId,
        holesCount = holesCount,
        parTotal = parTotal,
    )
}

fun OpenGolfCourseDetail.toSummary(): GolfCourseSummary =
    OpenGolfCourseListItem(
        id = id,
        osmId = osmId,
        clubName = clubName,
        courseName = courseName,
        city = city,
        state = state,
        latitude = latitude,
        longitude = longitude,
        holesCount = holesCount,
        parTotal = parTotal,
    ).toSummary()

fun OpenGolfCourseDetail.toScorecard(): List<ScorecardHole> {
    return (scorecard ?: emptyList())
        .mapNotNull { row ->
            val number = row.holeNumber ?: return@mapNotNull null
            ScorecardHole(number = number, par = row.par, handicap = row.handicap)
        }
        .sortedBy { it.number }
}

interface OpenGolfApi {
    suspend fun searchCourses(query: String): List<GolfCourseSummary>

    suspend fun loadCourse(id: String): Pair<GolfCourseSummary, List<ScorecardHole>>
}
