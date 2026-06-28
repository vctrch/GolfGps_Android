package com.vctrch.golfgps.domain

object CourseLoaderSupport {
    /**
     * Playable holes anchored at the course center, used until real OSM geometry arrives. Mirrors
     * the iOS `CourseLoader.fallbackHoles`: prefers scorecard holes, otherwise synthesizes holes
     * from the course's hole count so a round can still open.
     */
    fun fallbackHoles(
        scorecard: List<ScorecardHole>,
        center: GolfCourseSummary,
    ): List<HoleTarget> {
        val centerCoordinate = LatLng(center.latitude, center.longitude)
        if (scorecard.isNotEmpty()) {
            return scorecard.map { row ->
                HoleTarget(
                    number = row.number,
                    par = row.par,
                    tee = null,
                    green = centerCoordinate,
                    source = HoleTargetSource.SCORECARD_FALLBACK,
                )
            }
        }
        val count = maxOf(center.holesCount ?: 18, 1)
        return (1..count).map { number ->
            HoleTarget(
                number = number,
                par = null,
                tee = null,
                green = centerCoordinate,
                source = HoleTargetSource.SCORECARD_FALLBACK,
            )
        }
    }

    fun isMappedOsmHole(hole: HoleTarget): Boolean = hole.hasReliableGreenPosition
}
