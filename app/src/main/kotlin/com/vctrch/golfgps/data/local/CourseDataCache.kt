package com.vctrch.golfgps.data.local

import com.vctrch.golfgps.data.remote.OSMHoleParser
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
    val yardage: Int? = null,
)

@Serializable
data class PersistedHoleTarget(
    val number: Int,
    val par: Int? = null,
    val teeLatitude: Double? = null,
    val teeLongitude: Double? = null,
    val teeSourceRaw: String? = null,
    val greenLatitude: Double,
    val greenLongitude: Double,
    @SerialName("source") val sourceRaw: String,
)

@Serializable
data class PersistedOsmCacheEnvelope(
    val courseLatitude: Double,
    val courseLongitude: Double,
    val osmCourseWayID: Long? = null,
    val holes: List<PersistedHoleTarget>,
)

class CourseDataCache(
    private val dao: CachedCourseDao,
    private val json: Json,
) {
    suspend fun cachedBasics(
        courseId: String,
        matching: GolfCourseSummary? = null,
    ): LoadedCourse? {
        val record = dao.get(courseId) ?: return null
        val scorecard = decodeScorecard(record.scorecardJson)
        if (scorecard.isEmpty()) return null
        val summary = record.toSummary()
        if (matching != null && !CourseCacheValidation.basicsMatchSearchResult(summary, matching)) {
            return null
        }
        val fallback = CourseLoaderSupport.fallbackHoles(scorecard, summary)
        val cachedOsm = validatedCachedOsmHoles(summary).orEmpty()
        val holes =
            if (cachedOsm.any { CourseLoaderSupport.isMappedOsmHole(it) }) {
                OSMHoleParser.mergeHoles(fallback, cachedOsm, scorecard)
            } else {
                fallback
            }
        return LoadedCourse(summary = summary, scorecard = scorecard, holes = holes)
    }

    suspend fun cachedOsmHoles(courseId: String): List<HoleTarget>? {
        val record = dao.get(courseId) ?: return null
        return validatedCachedOsmHoles(record.toSummary())
    }

    suspend fun validatedCachedOsmHoles(summary: GolfCourseSummary): List<HoleTarget>? {
        val record = dao.get(summary.id) ?: return null
        val data = record.osmHolesJson ?: return null

        decodeOsmEnvelope(data)?.let { envelope ->
            val anchor = LatLng(envelope.courseLatitude, envelope.courseLongitude)
            val holes = envelope.holes.mapNotNull { decodeHoleTarget(it) }
            if (!CourseCacheValidation.osmCacheAnchorMatchesCourse(anchor, summary) ||
                !CourseCacheValidation.osmCourseWayIdMatches(envelope.osmCourseWayID, summary.osmId) ||
                !CourseCacheValidation.osmHolesMatchCourse(holes, summary)
            ) {
                clearOsmHoles(summary.id)
                return null
            }
            return holes.takeIf { it.isNotEmpty() }
        }

        val legacy = decodeHoles(data)
        if (!CourseCacheValidation.osmHolesMatchCourse(legacy, summary)) {
            clearOsmHoles(summary.id)
            return null
        }
        return legacy.takeIf { it.isNotEmpty() }
    }

    suspend fun saveBasics(course: LoadedCourse) {
        val existing = dao.get(course.summary.id)
        val clearOsm =
            existing != null &&
                CourseCacheValidation.shouldInvalidateOsmCache(
                    existingLatitude = existing.latitude,
                    existingLongitude = existing.longitude,
                    existingOsmId = existing.osmId,
                    existingHolesCount = existing.holesCount,
                    hasOsmHoles = existing.osmHolesJson != null,
                    incoming = course.summary,
                )
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
                osmHolesJson = if (clearOsm) null else existing?.osmHolesJson,
                osmUpdatedAt = if (clearOsm) null else existing?.osmUpdatedAt,
            )
        dao.upsert(entity)
    }

    suspend fun saveOsmHoles(
        courseId: String,
        summary: GolfCourseSummary,
        holes: List<HoleTarget>,
    ) {
        if (holes.isEmpty()) return
        if (!CourseCacheValidation.osmHolesMatchCourse(holes, summary)) return

        val existing = dao.get(courseId) ?: emptyEntity(summary)
        val prior = validatedCachedOsmHoles(summary).orEmpty()
        val merged =
            mergeOsmHoles(
                existing = prior,
                incoming = holes,
            )
        if (!CourseCacheValidation.osmHolesMatchCourse(merged, summary)) return

        dao.upsert(
            existing.copy(
                name = summary.name,
                city = summary.city,
                state = summary.state,
                latitude = summary.latitude,
                longitude = summary.longitude,
                osmId = summary.osmId,
                holesCount = summary.holesCount,
                parTotal = summary.parTotal,
                osmHolesJson =
                    encodeOsmEnvelope(
                        summary = summary,
                        osmCourseWayId = summary.osmId,
                        holes = merged.filter { isPersistableOsmHole(it) },
                    ),
                osmUpdatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clearOsmHoles(courseId: String) {
        val existing = dao.get(courseId) ?: return
        dao.upsert(
            existing.copy(
                osmHolesJson = null,
                osmUpdatedAt = null,
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
        val payload =
            scorecard.map {
                PersistedScorecardHole(it.number, it.par, it.handicap, it.yardage)
            }
        return json.encodeToString(payload)
    }

    private fun decodeScorecard(data: String): List<ScorecardHole> {
        return json.decodeFromString<List<PersistedScorecardHole>>(data).map {
            ScorecardHole(
                number = it.number,
                par = it.par,
                handicap = it.handicap,
                yardage = it.yardage,
            )
        }
    }

    private fun encodeOsmEnvelope(
        summary: GolfCourseSummary,
        osmCourseWayId: Long?,
        holes: List<HoleTarget>,
    ): String {
        val payload =
            PersistedOsmCacheEnvelope(
                courseLatitude = summary.latitude,
                courseLongitude = summary.longitude,
                osmCourseWayID = osmCourseWayId,
                holes = holes.map { persistHole(it) },
            )
        return json.encodeToString(payload)
    }

    private fun decodeOsmEnvelope(data: String): PersistedOsmCacheEnvelope? {
        if (!data.contains("\"courseLatitude\"")) return null
        return runCatching { json.decodeFromString<PersistedOsmCacheEnvelope>(data) }.getOrNull()
    }

    private fun decodeHoles(data: String): List<HoleTarget> {
        return json.decodeFromString<List<PersistedHoleTarget>>(data).mapNotNull { decodeHoleTarget(it) }
    }

    private fun persistHole(hole: HoleTarget): PersistedHoleTarget =
        PersistedHoleTarget(
            number = hole.number,
            par = hole.par,
            teeLatitude = hole.tee?.latitude,
            teeLongitude = hole.tee?.longitude,
            teeSourceRaw = hole.teeSource?.name,
            greenLatitude = hole.green.latitude,
            greenLongitude = hole.green.longitude,
            sourceRaw = hole.source.name,
        )

    private fun decodeHoleTarget(row: PersistedHoleTarget): HoleTarget? {
        val source = runCatching { HoleTargetSource.valueOf(row.sourceRaw) }.getOrNull() ?: return null
        return HoleTarget(
            number = row.number,
            par = row.par,
            tee = row.teeLatitude?.let { lat -> row.teeLongitude?.let { lon -> LatLng(lat, lon) } },
            teeSource =
                row.teeSourceRaw?.let { raw ->
                    runCatching { TeeMappingSource.valueOf(raw) }.getOrNull()
                },
            green = LatLng(row.greenLatitude, row.greenLongitude),
            source = source,
        )
    }
}
