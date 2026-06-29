package com.vctrch.golfgps.testing

import com.vctrch.golfgps.data.remote.OverpassElement
import com.vctrch.golfgps.data.remote.OverpassNode
import com.vctrch.golfgps.domain.*
import kotlin.math.cos

object TestFixtures {
    /** Arbitrary anchor for unit tests — not tied to any real course or region. */
    val sampleOrigin = LatLng(45.0, -93.0)

    val sampleTee: LatLng get() = sampleOrigin

    val sampleGreen: LatLng get() = offset(northYards = 142.0)

    /** Offset from an anchor in yards (north positive, east positive). */
    fun offset(
        northYards: Double = 0.0,
        eastYards: Double = 0.0,
        origin: LatLng = sampleOrigin,
    ): LatLng {
        val metersPerYard = 0.9144
        val latDelta = (northYards * metersPerYard) / 111_320
        val lonDelta = (eastYards * metersPerYard) / (111_320 * cos(Math.toRadians(origin.latitude)))
        return LatLng(origin.latitude + latDelta, origin.longitude + lonDelta)
    }

    fun overpassHoleElement(
        ref: Int,
        teeLat: Double,
        teeLon: Double,
        greenLat: Double,
        greenLon: Double,
        par: Int? = 4,
    ): OverpassElement {
        val tags = mutableMapOf("golf" to "hole", "ref" to "$ref")
        if (par != null) tags["par"] = "$par"
        return OverpassElement(
            id = ref.toLong(),
            tags = tags,
            geometry = listOf(OverpassNode(teeLat, teeLon), OverpassNode(greenLat, greenLon)),
        )
    }

    fun overpassPinElement(
        ref: Int,
        lat: Double,
        lon: Double,
    ): OverpassElement = OverpassElement(tags = mapOf("golf" to "pin", "ref" to "$ref"), lat = lat, lon = lon)

    fun overpassTeeElement(
        ref: Int,
        lat: Double,
        lon: Double,
    ): OverpassElement =
        OverpassElement(
            id = ref.toLong() + 10_000,
            tags = mapOf("golf" to "tee", "ref" to "$ref"),
            lat = lat,
            lon = lon,
        )

    fun overpassGreenElement(
        ref: Int,
        lat: Double,
        lon: Double,
    ): OverpassElement =
        OverpassElement(
            id = ref.toLong() + 20_000,
            tags = mapOf("golf" to "green", "ref" to "$ref"),
            lat = lat,
            lon = lon,
        )

    fun overpassFairwayElement(
        ref: Int,
        teeLat: Double,
        teeLon: Double,
        greenLat: Double,
        greenLon: Double,
    ): OverpassElement =
        OverpassElement(
            id = ref.toLong(),
            tags = mapOf("golf" to "fairway", "ref" to "$ref"),
            geometry = listOf(OverpassNode(teeLat, teeLon), OverpassNode(greenLat, greenLon)),
        )

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
