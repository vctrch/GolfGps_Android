package com.vctrch.golfgps.domain

/**
 * Validates cached course basics and OSM hole geometry so wrong-facility or drifted data
 * is never served as if it belonged to the selected course.
 */
object CourseCacheValidation {
    /** Cached basics must still describe the same facility the user selected. */
    fun basicsMatchSearchResult(
        cached: GolfCourseSummary,
        searchSummary: GolfCourseSummary,
    ): Boolean {
        if (cached.id != searchSummary.id) return false
        val cachedCenter = LatLng(cached.latitude, cached.longitude)
        val searchCenter = LatLng(searchSummary.latitude, searchSummary.longitude)
        return GeoMath.yards(cachedCenter, searchCenter) <= 400
    }

    /**
     * Hole geometry must plausibly belong to this course — rejects nearby wrong-facility OSM data.
     * Uses the OSM hole **cluster** (not the clubhouse pin) so elongated layouts still validate.
     */
    fun osmHolesMatchCourse(
        holes: List<HoleTarget>,
        summary: GolfCourseSummary,
    ): Boolean {
        val directOsm = holes.filter { it.source == HoleTargetSource.OPEN_STREET_MAP }
        val mapped = holes.filter { CourseLoaderSupport.isMappedOsmHole(it) }
        if (mapped.isEmpty()) return false

        // Gap-filled / inferred holes ride along once direct OSM geometry validates.
        if (directOsm.isEmpty()) {
            return osmInferredOnlyMatchCourse(mapped, summary)
        }

        val openGolfCenter = LatLng(summary.latitude, summary.longitude)
        val greenCoordinates = directOsm.map { it.green }
        val clusterCenter = GeoMath.centroid(greenCoordinates) ?: return false

        val clusterOffset = GeoMath.yards(openGolfCenter, clusterCenter)
        if (clusterOffset > 2_500) return false

        val distancesFromCluster = greenCoordinates.map { GeoMath.yards(clusterCenter, it) }
        val maxSpan = distancesFromCluster.maxOrNull() ?: 0
        if (maxSpan > 4_500) return false

        val cohesive = distancesFromCluster.count { it <= 3_500 }
        if (cohesive.toDouble() / directOsm.size < 0.6) return false

        return true
    }

    /** Accepts inferred holes only when they sit near the OpenGolf center. */
    private fun osmInferredOnlyMatchCourse(
        holes: List<HoleTarget>,
        summary: GolfCourseSummary,
    ): Boolean {
        val center = LatLng(summary.latitude, summary.longitude)
        val distances = holes.map { GeoMath.yards(center, it.green) }.sorted()
        if (distances.isEmpty()) return false
        val median = distances[distances.size / 2]
        return median <= 2_500 && (distances.maxOrNull() ?: 0) <= 4_000
    }

    fun osmCacheAnchorMatchesCourse(
        anchor: LatLng,
        summary: GolfCourseSummary,
    ): Boolean {
        val center = LatLng(summary.latitude, summary.longitude)
        return GeoMath.yards(anchor, center) <= 400
    }

    fun osmCourseWayIdMatches(
        cachedWayId: Long?,
        summaryWayId: Long?,
    ): Boolean {
        if (summaryWayId == null || cachedWayId == null) return true
        return summaryWayId == cachedWayId
    }

    fun shouldInvalidateOsmCache(
        existingLatitude: Double,
        existingLongitude: Double,
        existingOsmId: Long?,
        existingHolesCount: Int?,
        hasOsmHoles: Boolean,
        incoming: GolfCourseSummary,
    ): Boolean {
        if (!hasOsmHoles) return false
        if (existingOsmId != incoming.osmId) return true
        if (existingHolesCount != incoming.holesCount) return true
        val oldCenter = LatLng(existingLatitude, existingLongitude)
        val newCenter = LatLng(incoming.latitude, incoming.longitude)
        return GeoMath.yards(oldCenter, newCenter) > 350
    }
}
