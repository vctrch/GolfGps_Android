package com.vctrch.golfgps.domain.model

data class LatLng(
    val latitude: Double,
    val longitude: Double,
)

enum class HoleTargetSource {
    OPEN_STREET_MAP,
    OPEN_STREET_MAP_INFERRED,
    SCORECARD_FALLBACK,
}

data class GolfCourseSummary(
    val id: String,
    val name: String,
    val city: String?,
    val state: String?,
    val latitude: Double,
    val longitude: Double,
    val osmId: Long?,
    val holesCount: Int?,
    val parTotal: Int?,
)

data class ScorecardHole(
    val number: Int,
    val par: Int?,
    val handicap: Int?,
)

data class HoleTarget(
    val number: Int,
    val par: Int?,
    val tee: LatLng?,
    val green: LatLng,
    val source: HoleTargetSource,
) {
    val hasReliableGreenPosition: Boolean
        get() =
            source == HoleTargetSource.OPEN_STREET_MAP ||
                source == HoleTargetSource.OPEN_STREET_MAP_INFERRED

    fun holeLengthYards(): Int? {
        val teeCoordinate = tee ?: return null
        return com.vctrch.golfgps.domain.GeoMath.yards(teeCoordinate, green)
    }
}

data class LoadedCourse(
    val summary: GolfCourseSummary,
    val scorecard: List<ScorecardHole>,
    val holes: List<HoleTarget>,
)

enum class MapDisplayStyle(val storageKey: String, val title: String) {
    STANDARD("standard", "Standard"),
    SATELLITE("satellite", "Satellite"),
    HYBRID("hybrid", "Hybrid"),
}

enum class GolfDataError {
    INVALID_RESPONSE,
    COURSE_NOT_FOUND,
    NO_HOLE_DATA,
}
