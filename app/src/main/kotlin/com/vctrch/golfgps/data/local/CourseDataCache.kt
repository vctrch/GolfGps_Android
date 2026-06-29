package com.vctrch.golfgps.data.local

import com.vctrch.golfgps.domain.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PersistedScorecardHole(
    val number: Int,
    val par: Int? = null,
    val handicap: Int? = null,
)

@Serializable
data class PersistedHoleTarget(
    val number: Int,
    val par: Int? = null,
    val teeLatitude: Double? = null,
    val teeLongitude: Double? = null,
    val greenLatitude: Double,
    val greenLongitude: Double,
    @SerialName("source") val sourceRaw: String,
)

class CourseDataCache(
    private val dao: CachedCourseDao,
    private val json: Json,
) {
    suspend fun cachedBasics(courseId: String): LoadedCourse? {
        val record = dao.get(courseId) ?: return null
        val scorecard = decodeScorecard(record.scorecardJson)
        if (scorecard.isEmpty()) return null
        val summary = record.toSummary()
        val holes = CourseLoaderSupport.fallbackHoles(scorecard, summary)
        return LoadedCourse(summary = summary, scorecard = scorecard, holes = holes)
    }

    suspend fun cachedOsmHoles(courseId: String): List<HoleTarget>? {
        val data = dao.get(courseId)?.osmHolesJson ?: return null
        val holes = decodeHoles(data)
        return holes.takeIf { it.isNotEmpty() }
    }

    suspend fun saveBasics(course: LoadedCourse) {
        val existing = dao.get(course.summary.id)
        val entity =
            (existing ?: emptyEntity(course.summary)).copy(
                name = course.summary.name,
                city = course.summary.city,
                state = course.summary.state,
                latitude = course.summary.latitude,
                longitude = course.summary.longitude,
                osmId = course.summary.osmId,
                holesCount = course.summary.holesCount,
                parTotal = course.summary.parTotal,
                scorecardJson = encodeScorecard(course.scorecard),
                basicsUpdatedAt = System.currentTimeMillis(),
            )
        dao.upsert(entity)
    }

    suspend fun saveOsmHoles(
        courseId: String,
        summary: GolfCourseSummary,
        holes: List<HoleTarget>,
    ) {
        if (holes.isEmpty()) return
        val existing = dao.get(courseId) ?: emptyEntity(summary)
        val merged =
            mergeOsmHoles(
                existing = existing.osmHolesJson?.let { decodeHoles(it) } ?: emptyList(),
                incoming = holes,
            )
        dao.upsert(
            existing.copy(
                osmHolesJson = encodeHoles(merged),
                osmUpdatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun mergeOsmHoles(
        existing: List<HoleTarget>,
        incoming: List<HoleTarget>,
    ): List<HoleTarget> {
        val byNumber = existing.filter { isPersistableOsmHole(it) }.associateBy { it.number }.toMutableMap()
        incoming.filter { isPersistableOsmHole(it) }.forEach { byNumber[it.number] = it }
        return byNumber.values.sortedBy { it.number }
    }

    private fun isPersistableOsmHole(hole: HoleTarget): Boolean {
        return hole.source == HoleTargetSource.OPEN_STREET_MAP ||
            hole.source == HoleTargetSource.OPEN_STREET_MAP_INFERRED
    }

    private fun emptyEntity(summary: GolfCourseSummary): CachedCourseEntity {
        return CachedCourseEntity(
            courseId = summary.id,
            name = summary.name,
            city = summary.city,
            state = summary.state,
            latitude = summary.latitude,
            longitude = summary.longitude,
            osmId = summary.osmId,
            holesCount = summary.holesCount,
            parTotal = summary.parTotal,
            scorecardJson = "[]",
            osmHolesJson = null,
            basicsUpdatedAt = 0L,
            osmUpdatedAt = null,
        )
    }

    private fun CachedCourseEntity.toSummary(): GolfCourseSummary {
        return GolfCourseSummary(
            id = courseId,
            name = name,
            city = city,
            state = state,
            latitude = latitude,
            longitude = longitude,
            osmId = osmId,
            holesCount = holesCount,
            parTotal = parTotal,
        )
    }

    private fun encodeScorecard(scorecard: List<ScorecardHole>): String {
        val payload = scorecard.map { PersistedScorecardHole(it.number, it.par, it.handicap) }
        return json.encodeToString(payload)
    }

    private fun decodeScorecard(data: String): List<ScorecardHole> {
        return json.decodeFromString<List<PersistedScorecardHole>>(data).map {
            ScorecardHole(number = it.number, par = it.par, handicap = it.handicap)
        }
    }

    private fun encodeHoles(holes: List<HoleTarget>): String {
        val payload =
            holes.map { hole ->
                PersistedHoleTarget(
                    number = hole.number,
                    par = hole.par,
                    teeLatitude = hole.tee?.latitude,
                    teeLongitude = hole.tee?.longitude,
                    greenLatitude = hole.green.latitude,
                    greenLongitude = hole.green.longitude,
                    sourceRaw = hole.source.name,
                )
            }
        return json.encodeToString(payload)
    }

    private fun decodeHoles(data: String): List<HoleTarget> {
        return json.decodeFromString<List<PersistedHoleTarget>>(data).mapNotNull { row ->
            val source = runCatching { HoleTargetSource.valueOf(row.sourceRaw) }.getOrNull() ?: return@mapNotNull null
            HoleTarget(
                number = row.number,
                par = row.par,
                tee = row.teeLatitude?.let { lat -> row.teeLongitude?.let { lon -> LatLng(lat, lon) } },
                green = LatLng(row.greenLatitude, row.greenLongitude),
                source = source,
            )
        }
    }
}
