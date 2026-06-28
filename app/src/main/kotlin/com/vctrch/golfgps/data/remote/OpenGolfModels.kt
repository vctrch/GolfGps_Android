package com.vctrch.golfgps.data.remote

import com.vctrch.golfgps.domain.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenGolfSearchResponse(
    val courses: List<OpenGolfCourseListItem> = emptyList(),
)

// Search results (/v1/courses/search) report coordinates as latitude/longitude and par as `par`.
@Serializable
data class OpenGolfCourseListItem(
    val id: String,
    @SerialName("osm_id") val osmId: Long? = null,
    @SerialName("club_name") val clubName: String? = null,
    @SerialName("course_name") val courseName: String? = null,
    val name: String? = null,
    val city: String? = null,
    val state: String? = null,
    val latitude: Double,
    val longitude: Double,
    @SerialName("holes_count") val holesCount: Int? = null,
    val par: Int? = null,
)

// Course detail (/api/v1/courses/{id}) reports coordinates as lat/lng and holes as `holes_data`.
@Serializable
data class OpenGolfCourseDetail(
    val id: String,
    @SerialName("osm_id") val osmId: Long? = null,
    @SerialName("club_name") val clubName: String? = null,
    @SerialName("course_name") val courseName: String? = null,
    val city: String? = null,
    val state: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val par: Int? = null,
    val holes: Int? = null,
    @SerialName("holes_data") val holesData: List<OpenGolfHole>? = null,
)

@Serializable
data class OpenGolfHole(
    val number: Int? = null,
    val par: Int? = null,
    @SerialName("handicap_index") val handicapIndex: Int? = null,
)

fun OpenGolfCourseListItem.toSummary(): GolfCourseSummary {
    return GolfCourseSummary(
        id = id,
        name = courseName ?: clubName ?: name ?: "Golf Course",
        city = city,
        state = state,
        latitude = latitude,
        longitude = longitude,
        osmId = osmId,
        holesCount = holesCount,
        parTotal = par,
    )
}

fun OpenGolfCourseDetail.toSummary(): GolfCourseSummary =
    GolfCourseSummary(
        id = id,
        name = courseName ?: clubName ?: "Golf Course",
        city = city,
        state = state,
        latitude = lat ?: 0.0,
        longitude = lng ?: 0.0,
        osmId = osmId,
        holesCount = holes,
        parTotal = par,
    )

fun OpenGolfCourseDetail.toScorecard(): List<ScorecardHole> {
    return (holesData ?: emptyList())
        .mapNotNull { hole ->
            val number = hole.number ?: return@mapNotNull null
            ScorecardHole(number = number, par = hole.par, handicap = hole.handicapIndex)
        }
        .sortedBy { it.number }
}

interface OpenGolfApi {
    suspend fun searchCourses(query: String): List<GolfCourseSummary>

    suspend fun loadCourse(id: String): Pair<GolfCourseSummary, List<ScorecardHole>>
}
