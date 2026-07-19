package com.vctrch.golfgps.data.remote

import com.vctrch.golfgps.domain.HoleTarget
import com.vctrch.golfgps.domain.LatLng

/** Orphan and tagged OSM tee/green features used to snap inferred gap holes. */
data class GapFillContext(
    val unnumberedGreens: List<LatLng> = emptyList(),
    val unnumberedTees: List<LatLng> = emptyList(),
    val taggedTeesByHole: Map<Int, LatLng> = emptyMap(),
    val allTeeCandidates: List<LatLng> = emptyList(),
) {
    companion object {
        val EMPTY = GapFillContext()
    }
}

data class OSMHoleLoadResult(
    val holes: List<HoleTarget>,
    val gapFillContext: GapFillContext = GapFillContext.EMPTY,
)
