package com.vctrch.golfgps.data.remote

import com.vctrch.golfgps.domain.GeoMath
import com.vctrch.golfgps.domain.HoleTarget
import com.vctrch.golfgps.domain.LatLng
import com.vctrch.golfgps.domain.ScorecardHole
import com.vctrch.golfgps.domain.TeeMappingSource
import kotlin.math.abs
import kotlin.math.roundToInt

/** Matches orphan OSM tee boxes using OpenGolf published yardages when available. */
object OSMHoleTeeMatcher {
    private const val ASSIGNMENT_RADIUS_YARDS = 220

    private data class ResolvedTee(
        val coordinate: LatLng,
        val source: TeeMappingSource,
    )

    private data class RankedCandidate(
        val candidate: LatLng,
        val distance: Int,
        val score: Int,
    )

    fun teeCoordinates(elements: List<OverpassElement>): List<LatLng> {
        val results = mutableListOf<LatLng>()
        for (element in elements) {
            if (element.tags?.get("golf") != "tee") continue
            val coordinate =
                element.coordinate
                    ?: GeoMath.centroid(element.geometryCoordinates)
                    ?: continue
            appendUnique(coordinate, results)
        }
        return results
    }

    fun taggedTeesByHole(elements: List<OverpassElement>): Map<Int, LatLng> {
        val results = mutableMapOf<Int, LatLng>()
        for (element in elements) {
            if (element.tags?.get("golf") != "tee") continue
            val number = OSMHoleParser.holeNumber(element.tags ?: continue) ?: continue
            val coordinate =
                element.coordinate
                    ?: GeoMath.centroid(element.geometryCoordinates)
                    ?: continue
            results[number] = coordinate
        }
        return results
    }

    fun refine(
        holes: List<HoleTarget>,
        candidateTees: List<LatLng>,
        taggedTeesByHole: Map<Int, LatLng> = emptyMap(),
        scorecard: List<ScorecardHole>,
    ): List<HoleTarget> {
        if (candidateTees.isEmpty()) return holes

        val parByHole = scorecard.mapNotNull { hole -> hole.par?.let { hole.number to it } }.toMap()
        val yardageByHole = scorecard.mapNotNull { hole -> hole.yardage?.let { hole.number to it } }.toMap()
        val usedTees = mutableListOf<LatLng>()
        var previousGreen: LatLng? = null

        return holes.sortedBy { it.number }.map { hole ->
            if (!hole.hasReliableGreenPosition) {
                previousGreen = hole.green
                return@map hole
            }

            val par = hole.par ?: parByHole[hole.number]
            val publishedYardage = yardageByHole[hole.number]
            val tagged = taggedTeesByHole[hole.number]
            val available =
                candidateTees.filter { candidate ->
                    usedTees.none { GeoMath.yards(it, candidate) < 25 }
                }

            val resolved =
                resolveTee(
                    hole = hole,
                    par = par,
                    publishedYardage = publishedYardage,
                    taggedTee = tagged,
                    candidates = available,
                    currentEstimate = hole.tee,
                    previousGreen = previousGreen,
                )

            previousGreen = hole.green
            if (resolved != null) {
                usedTees.add(resolved.coordinate)
                hole.copy(tee = resolved.coordinate, teeSource = resolved.source)
            } else {
                hole
            }
        }
    }

    private fun resolveTee(
        hole: HoleTarget,
        par: Int?,
        publishedYardage: Int?,
        taggedTee: LatLng?,
        candidates: List<LatLng>,
        currentEstimate: LatLng?,
        previousGreen: LatLng?,
    ): ResolvedTee? {
        val green = hole.green
        val (expected, range) = yardageExpectation(publishedYardage, par)

        if (taggedTee != null) {
            val taggedDistance = GeoMath.yards(taggedTee, green)
            if (publishedYardage != null) {
                val tolerance = maxOf(40, (expected * 0.15).roundToInt())
                if (abs(taggedDistance - expected) <= tolerance) {
                    return ResolvedTee(taggedTee, TeeMappingSource.TAGGED)
                }
            } else if (taggedDistance in range) {
                return ResolvedTee(taggedTee, TeeMappingSource.TAGGED)
            }
        }

        val ranked =
            candidates
                .map { candidate ->
                    val distance = GeoMath.yards(candidate, green)
                    RankedCandidate(candidate, distance, abs(distance - expected))
                }
                .filter {
                    rankedCandidatePasses(it, range, publishedYardage, previousGreen, green)
                }
                .sortedWith(compareBy<RankedCandidate> { it.score }.thenByDescending { it.distance })

        ranked.firstOrNull()?.let {
            return ResolvedTee(it.candidate, TeeMappingSource.MATCHED)
        }

        if (currentEstimate != null) {
            val currentDistance = GeoMath.yards(currentEstimate, green)
            val keepTolerance = maxOf(50, (expected * 0.18).roundToInt())
            if (abs(currentDistance - expected) <= keepTolerance) {
                return ResolvedTee(currentEstimate, hole.teeSource ?: TeeMappingSource.FAIRWAY)
            }
        }

        val widenedTolerance =
            publishedYardage?.let { maxOf(55, (it * 0.22).roundToInt()) } ?: 80
        val closestByYardage =
            candidates
                .map { it to GeoMath.yards(it, green) }
                .minByOrNull { abs(it.second - expected) }
        if (closestByYardage != null && abs(closestByYardage.second - expected) <= widenedTolerance) {
            return ResolvedTee(closestByYardage.first, TeeMappingSource.MATCHED)
        }

        snapNearEstimate(currentEstimate, candidates, green, expected)?.let {
            return ResolvedTee(it, TeeMappingSource.MATCHED)
        }

        if (currentEstimate != null) {
            return ResolvedTee(currentEstimate, hole.teeSource ?: TeeMappingSource.FAIRWAY)
        }
        return null
    }

    private fun rankedCandidatePasses(
        ranked: RankedCandidate,
        range: IntRange,
        publishedYardage: Int?,
        previousGreen: LatLng?,
        green: LatLng,
    ): Boolean {
        if (publishedYardage != null) {
            val tolerance = maxOf(55, (publishedYardage * 0.22).roundToInt())
            if (ranked.score > tolerance) return false
        } else if (ranked.distance !in range) {
            return false
        }
        val prior = previousGreen ?: return true
        val teeNearPrior = GeoMath.yards(ranked.candidate, prior)
        val greenNearPrior = GeoMath.yards(green, prior)
        return teeNearPrior <= greenNearPrior + 120
    }

    private fun yardageExpectation(
        publishedYardage: Int?,
        par: Int?,
    ): Pair<Int, IntRange> {
        if (publishedYardage != null) {
            val tolerance = maxOf(35, (publishedYardage * 0.12).roundToInt())
            val lower = maxOf(70, publishedYardage - tolerance)
            val upper = minOf(700, publishedYardage + tolerance)
            return publishedYardage to (lower..upper)
        }
        return expectedYardage(par) to plausibleYardageRange(par)
    }

    private fun snapNearEstimate(
        estimate: LatLng?,
        candidates: List<LatLng>,
        green: LatLng,
        expectedYards: Int,
    ): LatLng? {
        if (estimate == null) return null
        val nearest =
            candidates.minWithOrNull(
                compareBy<LatLng> { GeoMath.yards(estimate, it) }
                    .thenBy { abs(GeoMath.yards(it, green) - expectedYards) },
            ) ?: return null
        return if (GeoMath.yards(estimate, nearest) <= ASSIGNMENT_RADIUS_YARDS) nearest else null
    }

    private fun expectedYardage(par: Int?): Int =
        when (par ?: 4) {
            3 -> 160
            5 -> 520
            else -> 380
        }

    private fun plausibleYardageRange(par: Int?): IntRange =
        when (par ?: 4) {
            3 -> 70..280
            5 -> 300..680
            else -> 160..520
        }

    private fun appendUnique(
        coordinate: LatLng,
        results: MutableList<LatLng>,
    ) {
        if (results.none { GeoMath.yards(it, coordinate) < 20 }) {
            results.add(coordinate)
        }
    }
}
