package com.vctrch.golfgps.domain

import com.vctrch.golfgps.domain.model.GolfCourseSummary
import com.vctrch.golfgps.domain.model.HoleTarget
import com.vctrch.golfgps.domain.model.HoleTargetSource
import com.vctrch.golfgps.domain.model.LatLng
import com.vctrch.golfgps.domain.model.ScorecardHole

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
