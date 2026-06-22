package com.vctrch.golfgps.domain

object CourseLoaderSupport {
    fun fallbackHoles(
        scorecard: List<ScorecardHole>,
        center: GolfCourseSummary,
    ): List<HoleTarget> {
        val centerCoordinate = LatLng(center.latitude, center.longitude)
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
}
