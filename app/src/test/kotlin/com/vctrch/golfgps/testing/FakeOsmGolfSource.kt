package com.vctrch.golfgps.testing

import com.vctrch.golfgps.data.remote.GapFillContext
import com.vctrch.golfgps.data.remote.OSMHoleLoadResult
import com.vctrch.golfgps.data.remote.OsmGolfSource
import com.vctrch.golfgps.domain.*

class FakeOsmGolfSource(
    var holes: List<HoleTarget> = emptyList(),
    var gapFillContext: GapFillContext = GapFillContext.EMPTY,
) : OsmGolfSource {
    var lastCenter: LatLng? = null
    var lastOsmCourseId: Long? = null

    override suspend fun loadHoleTargets(
        courseCenter: LatLng,
        osmCourseId: Long?,
        courseName: String?,
        scorecard: List<ScorecardHole>,
        userLocation: LatLng?,
    ): OSMHoleLoadResult {
        lastCenter = courseCenter
        lastOsmCourseId = osmCourseId
        return OSMHoleLoadResult(holes, gapFillContext)
    }
}
