package com.vctrch.golfgps.testing

import com.vctrch.golfgps.data.remote.OsmGolfSource
import com.vctrch.golfgps.domain.*

class FakeOsmGolfSource(
    var holes: List<HoleTarget> = emptyList(),
) : OsmGolfSource {
    var lastCenter: LatLng? = null
    var lastOsmCourseId: Long? = null

    override suspend fun loadHoleTargets(
        courseCenter: LatLng,
        osmCourseId: Long?,
        courseName: String?,
        scorecard: List<ScorecardHole>,
        userLocation: LatLng?,
    ): List<HoleTarget> {
        lastCenter = courseCenter
        lastOsmCourseId = osmCourseId
        return holes
    }
}
