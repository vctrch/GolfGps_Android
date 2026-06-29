package com.vctrch.golfgps.domain

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
        return GeoMath.yards(teeCoordinate, green)
    }

    /**
     * True when [location] is plausibly on this hole. With a tee, the fix must be within the hole's
     * own length (plus a buffer) of both the tee and the green; without one, just close to the green.
     * Used to suppress misleading yardages/markers when the GPS fix is far from the hole (e.g. an
     * emulator's default location).
     */
    fun isPlayerOnHole(location: LatLng): Boolean {
        val teeCoordinate = tee
        if (teeCoordinate == null) {
            return GeoMath.yards(location, green) <= ON_HOLE_NO_TEE_YARDS
        }
        val limit = GeoMath.yards(teeCoordinate, green) + ON_HOLE_BUFFER_YARDS
        return GeoMath.yards(location, green) <= limit && GeoMath.yards(location, teeCoordinate) <= limit
    }

    /** Player-to-green yardage, but only when the fix is on this hole (else null). */
    fun playerYardsToGreen(location: LatLng): Int? =
        if (isPlayerOnHole(location)) GeoMath.yards(location, green) else null

    private companion object {
        const val ON_HOLE_BUFFER_YARDS = 150
        const val ON_HOLE_NO_TEE_YARDS = 450
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
