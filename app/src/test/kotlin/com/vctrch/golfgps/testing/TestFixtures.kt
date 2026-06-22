package com.vctrch.golfgps.testing

import com.vctrch.golfgps.domain.*

object TestFixtures {
    fun summary(
        id: String = "course-1",
        latitude: Double = 32.8328,
        longitude: Double = -117.2713,
    ): GolfCourseSummary =
        GolfCourseSummary(
            id = id,
            name = "Torrey Pines",
            city = "La Jolla",
            state = "CA",
            latitude = latitude,
            longitude = longitude,
            osmId = 42L,
            holesCount = 18,
            parTotal = 72,
        )

    fun scorecard(): List<ScorecardHole> =
        listOf(
            ScorecardHole(number = 1, par = 4, handicap = 5),
            ScorecardHole(number = 2, par = 5, handicap = 1),
        )

    fun holeTarget(
        number: Int = 1,
        source: HoleTargetSource = HoleTargetSource.SCORECARD_FALLBACK,
        tee: LatLng? = null,
        green: LatLng = LatLng(32.8330, -117.2710),
    ): HoleTarget =
        HoleTarget(
            number = number,
            par = 4,
            tee = tee,
            green = green,
            source = source,
        )

    fun loadedCourse(id: String = "course-1"): LoadedCourse {
        val courseSummary = summary(id)
        val scorecardHoles = scorecard()
        val holes =
            scorecardHoles.map { row ->
                holeTarget(
                    number = row.number,
                    green = LatLng(courseSummary.latitude, courseSummary.longitude),
                )
            }
        return LoadedCourse(
            summary = courseSummary,
            scorecard = scorecardHoles,
            holes = holes,
        )
    }
}
